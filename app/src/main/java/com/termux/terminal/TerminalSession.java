package com.termux.terminal;

import static com.termux.NyxActivityKt.console;
import static com.termux.data.ConfigManager.transcriptRows;
import static com.termux.terminal.JNI.process;
import static com.termux.terminal.JNI.size;

import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

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
final public class TerminalSession {
    public final TerminalEmulator emulator = new TerminalEmulator(this, 4, 4, transcriptRows);
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

    public TerminalSession(boolean failsafe) {
        int[] processId = new int[1];
        mTerminalFileDescriptor = process(failsafe, processId, 4, 4);
        mShellPid = processId[0];
        FileDescriptor terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor);
        new Thread(() -> {
            try { //"TermSessionInputReader[pid=" + mShellPid + "]"
                final FileInputStream termIn = new FileInputStream(terminalFileDescriptorWrapped);
                final byte[] buffer = new byte[2048];
                int read;
                while ((read = termIn.read(buffer)) != -1) {
                    emulator.append(buffer, read);
                    notifyScreenUpdate();
                }

            } catch (Throwable ignored) { // Ignore, just shutting down.
            }
        }).start();
        termOut = new FileOutputStream(terminalFileDescriptorWrapped);
    }

    /**
     * Inform the attached pty of the new size and reflow or initialize the emulator.
     */
    public void updateSize(int columns, int rows) {
        size(mTerminalFileDescriptor, rows, columns);
        emulator.resize(columns, rows);
    }


    /**
     * Write data to the shell process.
     */
    public void write(byte[] data, int count) {
        if (mShellPid > 0) {
            try {
                termOut.write(data, 0, count);
            } catch (Throwable e) {
            }
        }
    }

    public void write(String data) {
        if (data == null) return;
        final byte[] bytes = data.getBytes();
        write(bytes, bytes.length);
    }

    /**
     * Write the Unicode code point to the terminal encoded in UTF-8.
     */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
            /* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else { /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
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
        } catch (Throwable t) {
        }
    }

    void onCopyTextToClipboard(String text) {
        console.onCopyTextToClipboard(text);
    }


    private FileDescriptor wrapFileDescriptor(int fileDescriptor) {
        FileDescriptor result = new FileDescriptor();
        try {
            Field descriptorField;
            try {
                descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
            } catch (Throwable t) { // For desktop java:
                descriptorField = FileDescriptor.class.getDeclaredField("fd");
            }
            descriptorField.setAccessible(true);
            descriptorField.set(result, fileDescriptor);
        } catch (Throwable ignored) {
        }
        return result;
    }
}
