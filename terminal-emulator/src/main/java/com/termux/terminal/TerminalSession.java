package com.termux.terminal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 * <p>
 * The subprocess will be executed by the constructor, and when the size is made known by a call to
 * {@link #updateSize(int, int, int, int)} terminal emulation will begin and threads will be spawned to handle the subprocess I/O.
 * All terminal emulation and callback methods will be performed on the main thread.
 * <p>
 * The child process may be exited forcefully by using the {@link #finishIfRunning()} method.
 * <p>
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
public final class TerminalSession {

    private static final int MSG_NEW_INPUT = 1;

    private static final int MSG_PROCESS_EXITED = 4;
    /**
     * A queue written to from a separate thread when the process outputs, and read by main thread to process by
     * terminal emulator.
     */
    private final ByteQueue mProcessToTerminalIOQueue = new ByteQueue();
    /**
     * A queue written to from the main thread due to user interaction, and read by another thread which forwards by
     * writing to the {@link #mTerminalFileDescriptor}.
     */
    private final ByteQueue mTerminalToProcessIOQueue = new ByteQueue();
    /**
     * Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue
     */
    private final byte[] mUtf8InputBuffer = new byte[5];
    private final String mShellPath;
    private final String mCwd;
    private final String[] mArgs;
    private final String[] mEnv;
    private final Integer mTranscriptRows;
    /**
     * Set by the application for user identification of session, not by terminal.
     */
    public String mSessionName;
    private TerminalEmulator mEmulator;
    /**
     * Callback which gets notified when a session finishes or changes title.
     */
    private TerminalSessionClient mClient;

    /**
     * The pid of the shell process. 0 if not started and -1 if finished running.
     */
    private int mShellPid;
    /**
     * The exit status of the shell process. Only valid if ${@link #mShellPid} is -1.
     */
    private int mShellExitStatus;
    /**
     * Whether to show bold text with bright colors.
     */
    private boolean mBoldWithBright;
    /**
     * The file descriptor referencing the master half of a pseudo-terminal pair, resulting from calling
     * {@link JNI#createSubprocess(String, String, String[], String[], int[], int, int, int, int)}.
     */
    private int mTerminalFileDescriptor;
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper()) {


        final byte[] mReceiveBuffer = new byte[(4 << 10)];

        private static byte[] getBytes(final int exitCode) {
            String exitDescription = "\r\n[Process completed";
            if (exitCode > 0) {
                // Non-zero process exit.
                exitDescription += " (code " + exitCode + ")";
            } else if (exitCode < 0) {
                // Negated signal.
                exitDescription += " (signal " + (-exitCode) + ")";
            }
            exitDescription += " - press Enter]";
            return exitDescription.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handleMessage(final Message msg) {
            final int bytesRead = TerminalSession.this.mProcessToTerminalIOQueue.read(this.mReceiveBuffer, false);
            if (bytesRead > 0) {
                TerminalSession.this.mEmulator.append(this.mReceiveBuffer, bytesRead);
                TerminalSession.this.notifyScreenUpdate();
            }
            if (msg.what == TerminalSession.MSG_PROCESS_EXITED) {
                final int exitCode = (Integer) msg.obj;
                TerminalSession.this.cleanupResources(exitCode);
                final byte[] bytesToWrite = getBytes(exitCode);
                TerminalSession.this.mEmulator.append(bytesToWrite, bytesToWrite.length);
                TerminalSession.this.notifyScreenUpdate();
                TerminalSession.this.mClient.onSessionFinished(TerminalSession.this);
            }
        }
    };//= new MainThreadHandler();

    public TerminalSession(final String shellPath, final String cwd, final String[] args, final String[] env, final Integer transcriptRows, final TerminalSessionClient client) {
        super();
        mShellPath = shellPath;
        mCwd = cwd;
        mArgs = args;
        mEnv = env;
        mTranscriptRows = transcriptRows;
        mClient = client;
    }

    private static FileDescriptor wrapFileDescriptor(final int fileDescriptor) {
        final FileDescriptor result = new FileDescriptor();
        try {
            Field descriptorField;
            try {
                descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
            } catch (final NoSuchFieldException e) {
                // For desktop java:
                descriptorField = FileDescriptor.class.getDeclaredField("fd");
            }
            descriptorField.setAccessible(true);
            descriptorField.set(result, fileDescriptor);
        } catch (final NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {

            System.exit(1);
        }
        return result;
    }

    public void updateTerminalSessionClient(final TerminalSessionClient client) {
        this.mClient = client;
        if (this.mEmulator != null)
            this.mEmulator.updateTermuxTerminalSessionClientBase();
    }

    /**
     * Update the setting to render bold text with bright colors. This takes effect on
     * the next call to updateSize().
     */
    public void setBoldWithBright(final boolean boldWithBright) {
        mBoldWithBright = boldWithBright;
    }

    /**
     * Inform the attached pty of the new size and reflow or initialize the emulator.
     */
    public void updateSize(final int columns, final int rows, final int fontWidth, final int fontHeight) {
        if (this.mEmulator == null) {
            this.initializeEmulator(columns, rows, fontWidth, fontHeight);
        } else {
            JNI.setPtyWindowSize(this.mTerminalFileDescriptor, rows, columns, fontWidth, fontHeight);
            this.mEmulator.resize(columns, rows);
        }
    }

    /**
     * The terminal title as set through escape sequences or null if none set.
     */
    public String getTitle() {
        return (this.mEmulator == null) ? null : this.mEmulator.getTitle();
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    private void initializeEmulator(final int columns, final int rows, final int cellWidth, final int cellHeight) {
        this.mEmulator = new TerminalEmulator(this, this.mBoldWithBright, columns, rows, this.mTranscriptRows);
        final int[] processId = new int[1];
        this.mTerminalFileDescriptor = JNI.createSubprocess(this.mShellPath, this.mCwd, this.mArgs, this.mEnv, processId, rows, columns, cellWidth, cellHeight);
        this.mShellPid = processId[0];
        this.mClient.setTerminalShellPid(this, this.mShellPid);
        FileDescriptor terminalFileDescriptorWrapped = TerminalSession.wrapFileDescriptor(this.mTerminalFileDescriptor);
        new Thread("TermSessionInputReader[pid=" + this.mShellPid + "]") {

            @Override
            public void run() {
                try (final InputStream termIn = new FileInputStream(terminalFileDescriptorWrapped)) {
                    byte[] buffer = new byte[4096];
                    while (true) {
                        final int read = termIn.read(buffer);
                        if (read == -1)
                            return;
                        if (!TerminalSession.this.mProcessToTerminalIOQueue.write(buffer, 0, read))
                            return;
                        TerminalSession.this.mMainThreadHandler.sendEmptyMessage(TerminalSession.MSG_NEW_INPUT);
                    }
                } catch (final Exception e) {
                    // Ignore, just shutting down.
                }
            }
        }.start();
        new Thread("TermSessionOutputWriter[pid=" + this.mShellPid + "]") {

            @Override
            public void run() {
                byte[] buffer = new byte[4096];
                try (final FileOutputStream termOut = new FileOutputStream(terminalFileDescriptorWrapped)) {
                    while (true) {
                        final int bytesToWrite = TerminalSession.this.mTerminalToProcessIOQueue.read(buffer, true);
                        if (bytesToWrite == -1)
                            return;
                        termOut.write(buffer, 0, bytesToWrite);
                    }
                } catch (final IOException e) {
                    // Ignore.
                }
            }
        }.start();
        new Thread("TermSessionWaiter[pid=" + this.mShellPid + "]") {

            @Override
            public void run() {
                final int processExitCode = JNI.waitFor(TerminalSession.this.mShellPid);
                TerminalSession.this.mMainThreadHandler.sendMessage(TerminalSession.this.mMainThreadHandler.obtainMessage(TerminalSession.MSG_PROCESS_EXITED, processExitCode));
            }
        }.start();
    }

    /**
     * Write data to the shell process.
     */
    public void write(final byte[] data, final int offset, final int count) {
        if (this.mShellPid > 0)
            this.mTerminalToProcessIOQueue.write(data, offset, count);
    }

    public void write(final String data) {
        if (data == null)
            return;
        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        this.write(bytes, 0, bytes.length);
    }

    /**
     * Write the Unicode code point to the terminal encoded in UTF-8.
     */
    public void writeCodePoint(final boolean prependEscape, final int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            // 1114111 (= 2**16 + 1024**2 - 1) is the highest code point, [0xD800,0xDFFF] is the surrogate range.
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }
        int bufferPosition = 0;
        if (prependEscape)
            this.mUtf8InputBuffer[bufferPosition++] = 27;
        if (codePoint <= /* 7 bits */
            0b1111111) {
            this.mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */
            0b11111111111) {
            /* 110xxxxx leading byte with leading 5 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            /* 10xxxxxx continuation byte with following 6 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */
            0b1111111111111111) {
            /* 1110xxxx leading byte with leading 4 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            /* 10xxxxxx continuation byte with following 6 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            /* 10xxxxxx continuation byte with following 6 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            this.mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        this.write(this.mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return this.mEmulator;
    }

    /**
     * Notify the {@link #mClient} that the screen has changed.
     */
    private void notifyScreenUpdate() {
        this.mClient.onTextChanged(this);
    }

    /**
     * Finish this terminal session by sending SIGKILL to the shell.
     */
    public void finishIfRunning() {
        if (this.isRunning()) {
            try {
                Os.kill(this.mShellPid, OsConstants.SIGKILL);
            } catch (final ErrnoException ignored) {

            }
        }
    }

    /**
     * Cleanup resources when the process exits.
     */
    private void cleanupResources(final int exitStatus) {
        synchronized (this) {
            this.mShellPid = -1;
            this.mShellExitStatus = exitStatus;
        }
        // Stop the reader and writer threads, and close the I/O streams
        this.mTerminalToProcessIOQueue.close();
        this.mProcessToTerminalIOQueue.close();
        JNI.close(this.mTerminalFileDescriptor);
    }

    public boolean isRunning() {
        synchronized (this) {
            return this.mShellPid != -1;
        }
    }

    /**
     * Only valid if not {@link #isRunning()}.
     */
    public int getExitStatus() {
        synchronized (this) {
            return this.mShellExitStatus;
        }
    }

    public void onCopyTextToClipboard(final String text) {
        this.mClient.onCopyTextToClipboard(text);
    }


    public void onPasteTextFromClipboard() {
        this.mClient.onPasteTextFromClipboard();
    }

    /**
     * Returns the shell's working directory or null if it was unavailable.
     */
    public String getCwd() {
        if (this.mShellPid < 1) {
            return null;
        }
        try {
            String cwdSymlink = "/proc/" + this.mShellPid + "/cwd/";
            final String outputPath = new File(cwdSymlink).getCanonicalPath();
            String outputPathWithTrailingSlash = outputPath;
            if (!(!outputPath.isEmpty() && outputPath.charAt(outputPath.length() - 1) == '/')) {
                outputPathWithTrailingSlash += '/';
            }
            if (!cwdSymlink.equals(outputPathWithTrailingSlash)) {
                return outputPath;
            }
        } catch (final IOException | SecurityException ignored) {

        }
        return null;
    }


}
