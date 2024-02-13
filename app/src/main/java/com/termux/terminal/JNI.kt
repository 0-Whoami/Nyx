package com.termux.terminal

/**
 * Native methods for creating and managing pseudoterminal subprocesses. C code is in jni/termux.c.
 */
internal object JNI {
    init {
        System.loadLibrary("termux")
    }

    /**
     * Create a subprocess. Differs from [ProcessBuilder] in that a pseudoterminal is used to communicate with the
     * subprocess.
     *
     *
     * Callers are responsible for calling [.close] on the returned file descriptor.
     *
     * @param processId A one-element array to which the process ID of the started process will be written.
     * @return the file descriptor resulting from opening /dev/ptmx master device. The sub process will have opened the
     * slave device counterpart (/dev/pts/$N) and have it as stdint, stdout and stderr.
     */

    external fun process(
        failsafe: Boolean,
        processId: IntArray,
        rows: Int,
        columns: Int,
        cellWidth: Int,
        cellHeight: Int
    ): Int

    /**
     * Set the window size for a given pty, which allows connected programs to learn how large their screen is.
     */

    external fun size(fd: Int, rows: Int, cols: Int, cellWidth: Int, cellHeight: Int)

    /**
     * Causes the calling thread to wait for the process associated with the receiver to finish executing.
     *
     * @return if >= 0, the exit status of the process. If < 0, the signal causing the process to stop negated.
     */

    external fun waitFor(processId: Int): Int

    /**
     * Close a file descriptor through the close(2) system call.
     */

    external fun close(fileDescriptor: Int)
}
