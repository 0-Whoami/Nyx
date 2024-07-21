package com.termux.terminal;

/**
 * Native methods for creating and managing pseudoterminal subprocesses. C code is in jni/termux.c.
 */
enum JNI {
    ;

    static {
        System.loadLibrary("termux");
    }


    /**
     * Create a subprocess. Differs from [ProcessBuilder] in that a pseudoterminal is used to communicate with the
     * subprocess.
     * <p>
     * <p>
     * Callers are responsible for calling [.close] on the returned file descriptor.
     *
     * @param processId A one-element array to which the process ID of the started process will be written.
     * @return the file descriptor resulting from opening /dev/ptmx master device. The sub process will have opened the
     * slave device counterpart (/dev/pts/$N) and have it as stdint, stdout and stderr.
     */

    static native int process(boolean failsafe, int[] processId, int rows, int columns);

    /**
     * Set the window size for a given pty, which allows connected programs to learn how large their console is.
     */

    static native void size(int fd, int rows, int cols);
}
