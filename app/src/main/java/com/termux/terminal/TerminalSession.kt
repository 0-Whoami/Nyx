package com.termux.terminal

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.termux.data.ConfigManager.transcriptRows
import com.termux.data.console
import com.termux.terminal.JNI.close
import com.termux.terminal.JNI.process
import com.termux.terminal.JNI.size
import com.termux.terminal.JNI.waitFor
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 *
 *
 * The subprocess will be executed by the constructor, and when the size is made known by a call to
 * [.updateSize] terminal emulation will begin and threads will be spawned to handle the subprocess I/O.
 * All terminal emulation and callback methods will be performed on the NyxActivity thread.
 *
 *
 * The child process may be exited forcefully by using the [.finishIfRunning] method.
 *
 *
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
class TerminalSession(
    failsafe: Boolean
) {
    /**
     * A queue written to from a separate thread when the process outputs, and read by NyxActivity thread to process by
     * terminal emulator.
     */
    private val mProcessToTerminalIOQueue = ByteQueue()

    /**
     * A queue written to from the NyxActivity thread due to user interaction, and read by another thread which forwards by
     * writing to the [.mTerminalFileDescriptor].
     */
    private val mTerminalToProcessIOQueue = ByteQueue()

    /**
     * Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue
     */
    private val mUtf8InputBuffer = ByteArray(5)

    var emulator: TerminalEmulator = TerminalEmulator(this, 4, 4, transcriptRows)

    /**
     * The pid of the shell process. 0 if not started and -1 if finished running.
     */
    private var mShellPid = 0

    /**
     * The exit status of the shell process. Only valid if $[.mShellPid] is -1.
     */
    private var mShellExitStatus = 0

    /**
     * The file descriptor referencing the master half of a pseudo-terminal pair, resulting from calling
     * [JNI.process].
     */
    private var mTerminalFileDescriptor = 0
    
    private val mMainThreadHandler: Handler = object : Handler(Looper.getMainLooper()) {
        val mReceiveBuffer: ByteArray = ByteArray(BUFFER_SIZE)

        private fun getBytes(exitCode: Int): ByteArray {
            val builder = StringBuilder("\r\n[Process completed")
            if (exitCode > 0) {
                builder.append(" (code ").append(exitCode).append(')')
            } else {
                builder.append(" (signal ").append(-exitCode).append(')')
            }
            builder.append(" - press Enter]")
            return builder.toString().toByteArray(StandardCharsets.UTF_8)
        }

        override fun handleMessage(msg: Message) {
            val bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false)
            if (bytesRead > 0) {
                emulator.append(mReceiveBuffer, bytesRead)
                notifyScreenUpdate()
            }
            if (msg.what == MSG_PROCESS_EXITED) {
                val exitCode = msg.obj as Int
                cleanupResources(exitCode)
                val bytesToWrite = getBytes(exitCode)
                emulator.append(bytesToWrite, bytesToWrite.size)
                notifyScreenUpdate()
            }
        }
    }

    init {
        val processId = IntArray(1)
        mTerminalFileDescriptor = process(
            failsafe, processId, 4, 4
        )
        mShellPid = processId[0]
        val terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor)
        Thread {
            try {//"TermSessionInputReader[pid=" + mShellPid + "]"
                FileInputStream(terminalFileDescriptorWrapped).use { termIn ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read: Int = termIn.read(buffer)
                        if (read == -1) return@Thread
                        if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) return@Thread
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                    }

                }
            } catch (e: Exception) {
                // Ignore, just shutting down.
            }

        }.start()
        Thread { //"TermSessionOutputWriter[pid=" + mShellPid + "]"
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                FileOutputStream(terminalFileDescriptorWrapped).use { termOut ->
                    while (true) {
                        val bytesToWrite: Int = mTerminalToProcessIOQueue.read(buffer, true)
                        if (bytesToWrite == -1) return@Thread
                        termOut.write(buffer, 0, bytesToWrite)
                    }
                }
            } catch (e: IOException) {//ignore
            }
        }.start()

        Thread { //"TermSessionWaiter[pid=" + mShellPid + "]"
            val processExitCode = waitFor(mShellPid)
            mMainThreadHandler.sendMessage(
                mMainThreadHandler.obtainMessage(
                    MSG_PROCESS_EXITED, processExitCode
                )
            )
        }.start()
    }

    /**
     * Inform the attached pty of the new size and reflow or initialize the emulator.
     */
    fun updateSize(columns: Int, rows: Int) {
        size(mTerminalFileDescriptor, rows, columns)
        emulator.resize(columns, rows)
    }


    /**
     * Write data to the shell process.
     */
    fun write(data: ByteArray?, offset: Int, count: Int) {
        if (mShellPid > 0) mTerminalToProcessIOQueue.write(data ?: return, offset, count)
    }

    fun write(data: String?) {
        if (data == null) return
        val bytes = data.toByteArray(Charsets.UTF_8)
        write(bytes, 0, bytes.size)
    }

    /**
     * Write the Unicode code point to the terminal encoded in UTF-8.
     */
    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        var bufferPosition = 0
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27
        if (codePoint <=  /* 7 bits */
            127
        ) {
            mUtf8InputBuffer[bufferPosition++] = codePoint.toByte()
        } else if (codePoint <=  /* 11 bits */
            2047
        ) {/* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] =
                (192 or (codePoint shr 6)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        } else if (codePoint <=  /* 16 bits */
            65535
        ) {/* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] =
                (224 or (codePoint shr 12)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] =
                (128 or ((codePoint shr 6) and 63)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        } else {/* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 *//* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] =
                (240 or (codePoint shr 18)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] =
                (128 or ((codePoint shr 12) and 63)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] =
                (128 or ((codePoint shr 6) and 63)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        }
        write(mUtf8InputBuffer, 0, bufferPosition)
    }

    /**
     * Notify the [.mClient] that the console has changed.
     */
    private fun notifyScreenUpdate() {
        if (console.currentSession == this) console.onScreenUpdated()
    }


    /**
     * Finish this terminal session by sending SIGKILL to the shell.
     */
    fun finishIfRunning() {
        if (isRunning) {
            try {
                Os.kill(mShellPid, OsConstants.SIGKILL)
            } catch (ignored: ErrnoException) {
            }
        }
    }

    /**
     * Cleanup resources when the process exits.
     */
    private fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            mShellPid = -1
            mShellExitStatus = exitStatus
        }
        // Stop the reader and writer threads, and close the I/O streams
        mTerminalToProcessIOQueue.close()
        mProcessToTerminalIOQueue.close()
        close(mTerminalFileDescriptor)
    }

    val isRunning: Boolean
        get() = synchronized(this) {
            return mShellPid != -1
        }


    fun onCopyTextToClipboard(text: String): Unit = console.onCopyTextToClipboard(text)


    private fun wrapFileDescriptor(fileDescriptor: Int): FileDescriptor {
        val result = FileDescriptor()
        val descriptorField: Field = try {
            FileDescriptor::class.java.getDeclaredField("descriptor")
        } catch (e: NoSuchFieldException) {
            // For desktop java:
            FileDescriptor::class.java.getDeclaredField("fd")
        }
        descriptorField.isAccessible = true
        descriptorField[result] = fileDescriptor
        return result
    }
}

private const val MSG_NEW_INPUT = 1

private const val MSG_PROCESS_EXITED = 4
