package com.termux.terminal;

import static com.termux.NyxActivity.console;
import static com.termux.data.ConfigManager.transcriptRows;
import static com.termux.terminal.JNI.process;
import static com.termux.terminal.JNI.size;

import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 * <p>
 * <p>
 * The subprocess will be executed by the constructor, and when the size is made known by a call to
 * [.updateSize] terminal emulation will begin and threads will be spawned to handle the subprocess I/O.
 * All terminal emulation and callback methods will be performed on the NyxActivity thread.
 * <p>
 * <p>
 * The child process may be exited forcefully by using the [.finishIfRunning] method.
 * <p>
 * <p>
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
public final class TerminalSession {
    public final TerminalEmulator emulator = new TerminalEmulator(this, 18, 18, transcriptRows);
    /**
     * Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue
     */
    private final byte[] mUtf8InputBuffer = new byte[5];
    /**
     * The pid of the shell process. 0 if not started and -1 if finished running.
     */
    private final int mShellPid;

    /**
     * The file descriptor referencing the master half of a pseudo-terminal pair, resulting from calling
     * [JNI.process].
     */
    private final int mTerminalFileDescriptor;

    private final FileOutputStream termOut;

    public TerminalSession(final boolean failsafe) {
        final int[] processId = new int[1];
        mTerminalFileDescriptor = process(failsafe, processId, 4, 4);
        mShellPid = processId[0];
        final FileDescriptor terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor);
        new Thread(() -> {
            try (final FileInputStream termIn = new FileInputStream(terminalFileDescriptorWrapped)) { //"TermSessionInputReader[pid=" + mShellPid + "]"
                final byte[] buffer = new byte[2048];
                int read;
                while (-1 != (read = termIn.read(buffer))) {
                    emulator.append(buffer, read);
                    notifyScreenUpdate();
                }
            } catch (final Throwable ignored) { // Ignore, just shutting down.
            }
        }).start();
        termOut = new FileOutputStream(terminalFileDescriptorWrapped);
    }

    static void onCopyTextToClipboard(final CharSequence text) {
        console.onCopyTextToClipboard(text);
    }

    private static FileDescriptor wrapFileDescriptor(final int fileDescriptor) {
        final FileDescriptor result = new FileDescriptor();
        try {
            Field descriptorField;
            try {
                descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
            } catch (final Throwable t) { // For desktop java:
                descriptorField = FileDescriptor.class.getDeclaredField("fd");
            }
            descriptorField.setAccessible(true);
            descriptorField.set(result, fileDescriptor);
        } catch (final Throwable ignored) {
        }
        return result;
    }

    /**
     * Inform the attached pty of the new size and reflow or initialize the emulator.
     */
    public void updateSize(final int columns, final int rows) {
        size(mTerminalFileDescriptor, rows, columns);
        emulator.resize(columns, rows);
    }

    /**
     * Write data to the shell process.
     */
    public void write(final byte[] data, final int count) {
        if (0 < mShellPid) {
            try {
                termOut.write(data, 0, count);
            } catch (final Throwable ignored) {
            }
        }
    }

    public void write(final String data) {
        if (null == data) return;
        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, bytes.length);
    }

    /**
     * Write the Unicode code point to the terminal encoded in UTF-8.
     */
    public void writeCodePoint(final boolean prependEscape, final int codePoint) {
        int bufferPosition = 0;
        if (prependEscape) {
            mUtf8InputBuffer[bufferPosition] = 27;
            bufferPosition++;
        }

        /* 11 bits */
        /* 7 bits */
        if (0b1111111 >= codePoint) {
            mUtf8InputBuffer[bufferPosition] = (byte) codePoint;
            bufferPosition++;
        } else /* 16 bits */ if (0b11111111111 >= codePoint) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b11000000 | (codePoint >> 6));
            bufferPosition++;
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b10000000 | (codePoint & 0b111111));
            bufferPosition++;
        } else if (0b1111111111111111 >= codePoint) {
            /* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b11100000 | (codePoint >> 12));
            bufferPosition++;
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            bufferPosition++;
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b10000000 | (codePoint & 0b111111));
            bufferPosition++;
        } else { /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b11110000 | (codePoint >> 18));
            bufferPosition++;
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            bufferPosition++;
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            bufferPosition++;
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition] = (byte) (0b10000000 | (codePoint & 0b111111));
            bufferPosition++;
        }
        write(mUtf8InputBuffer, bufferPosition);
    }

    /**
     * Notify the [.mClient] that the console has changed.
     */
    private void notifyScreenUpdate() {
        if (console.currentSession == this) console.onScreenUpdated();
    }

    /**
     * Finish this terminal session by sending SIGKILL to the shell.
     */
    void finishIfRunning() {
        try {
            Os.kill(mShellPid, OsConstants.SIGKILL);
            termOut.close();
        } catch (final Throwable ignored) {
        }
    }
}
