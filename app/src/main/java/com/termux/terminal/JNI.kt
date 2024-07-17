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

    external fun process(failsafe : Boolean, processId : IntArray, rows : Int, columns : Int) : Int

    /**
     * Set the window size for a given pty, which allows connected programs to learn how large their console is.
     */

    external fun size(fd : Int, rows : Int, cols : Int)
}
