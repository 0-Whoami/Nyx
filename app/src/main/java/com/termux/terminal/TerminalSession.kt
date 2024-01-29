package com.termux.terminal

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.termux.terminal.JNI.close
import com.termux.terminal.JNI.createSubprocess
import com.termux.terminal.JNI.setPtyWindowSize
import com.termux.terminal.JNI.waitFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 *
 *
 * The subprocess will be executed by the constructor, and when the size is made known by a call to
 * [.updateSize] terminal emulation will begin and threads will be spawned to handle the subprocess I/O.
 * All terminal emulation and callback methods will be performed on the main thread.
 *
 *
 * The child process may be exited forcefully by using the [.finishIfRunning] method.
 *
 *
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
//TODO revise this
class TerminalSession(
    /**
     * Callback which gets notified when a session finishes or changes title.
     */
    private val failsafe: Boolean,
    private var mClient: TerminalSessionClient
) {
    /**
     * A queue written to from a separate thread when the process outputs, and read by main thread to process by
     * terminal emulator.
     */
    private val mProcessToTerminalIOQueue = ByteQueue()

    /**
     * A queue written to from the main thread due to user interaction, and read by another thread which forwards by
     * writing to the [.mTerminalFileDescriptor].
     */
    private val mTerminalToProcessIOQueue = ByteQueue()

    /**
     * Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue
     */
    private val mUtf8InputBuffer = ByteArray(5)

    var emulator: TerminalEmulator =
        TerminalEmulator(this, false, 38, 17, 100)
        private set

    init {
        initializeProcess()
    }

    /**
     * The pid of the shell process. 0 if not started and -1 if finished running.
     */
    private var mShellPid = 0

    /**
     * The exit status of the shell process. Only valid if $[.mShellPid] is -1.
     */
    private var mShellExitStatus = 0

    /**
     * Whether to show bold text with bright colors.
     */
    private var mBoldWithBright = true

    /**
     * The file descriptor referencing the master half of a pseudo-terminal pair, resulting from calling
     * [JNI.createSubprocess].
     */
    private var mTerminalFileDescriptor = 0
    private val mMainThreadHandler: Handler = object : Handler(Looper.getMainLooper()) {
        val mReceiveBuffer: ByteArray = ByteArray((4 shl 10))

        private fun getBytes(exitCode: Int): ByteArray {
            var exitDescription = "\r\n[Process completed"
            if (exitCode > 0) {
                // Non-zero process exit.
                exitDescription += " (code $exitCode)"
            } else if (exitCode < 0) {
                // Negated signal.
                exitDescription += " (signal " + (-exitCode) + ")"
            }
            exitDescription += " - press Enter]"
            return exitDescription.toByteArray(StandardCharsets.UTF_8)
        }

        override fun handleMessage(msg: Message) {
            val bytesRead =
                mProcessToTerminalIOQueue.read(this.mReceiveBuffer, false)
            if (bytesRead > 0) {
                emulator.append(this.mReceiveBuffer, bytesRead)
                this@TerminalSession.notifyScreenUpdate()
            }
            if (msg.what == MSG_PROCESS_EXITED) {
                val exitCode = msg.obj as Int
                this@TerminalSession.cleanupResources(exitCode)
                val bytesToWrite = getBytes(exitCode)
                emulator.append(bytesToWrite, bytesToWrite.size)
                this@TerminalSession.notifyScreenUpdate()
                mClient.onSessionFinished(this@TerminalSession)
            }
        }
    }

    fun updateTerminalSessionClient(client: TerminalSessionClient) {
        this.mClient = client
        emulator.updateTermuxTerminalSessionClientBase()
    }

    /**
     * Inform the attached pty of the new size and reflow or initialize the emulator.
     */
    fun updateSize(columns: Int, rows: Int, fontWidth: Int, fontHeight: Int) {
        setPtyWindowSize(this.mTerminalFileDescriptor, rows, columns, fontWidth, fontHeight)
        emulator.resize(columns, rows)
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    private fun initializeProcess() {
        val processId = IntArray(1)
        this.mTerminalFileDescriptor = createSubprocess(
            failsafe,
            processId,
            17,
            38,
            12,
            12
        )
        this.mShellPid = processId[0]
        val terminalFileDescriptorWrapped = wrapFileDescriptor(this.mTerminalFileDescriptor)
        CoroutineScope(Dispatchers.IO).launch {//"TermSessionInputReader[pid=" + this.mShellPid + "]"
            try {
                FileInputStream(terminalFileDescriptorWrapped).use { termIn ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read: Int = termIn.read(buffer)
                        if (read == -1) return@launch
                        if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) return@launch
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                    }
                }
            } catch (e: Exception) {
                // Ignore, just shutting down.
            }
        }
        CoroutineScope(Dispatchers.IO).launch {//"TermSessionOutputWriter[pid=" + this.mShellPid + "]"
            val buffer = ByteArray(4096)
            try {
                FileOutputStream(terminalFileDescriptorWrapped).use { termOut ->
                    while (true) {
                        val bytesToWrite: Int =
                            mTerminalToProcessIOQueue.read(buffer, true)
                        if (bytesToWrite == -1) return@launch
                        termOut.write(buffer, 0, bytesToWrite)
                    }
                }
            } catch (e: IOException) {//ignore
            }
        }
        CoroutineScope(Dispatchers.IO).launch {//"TermSessionWaiter[pid=" + this.mShellPid + "]"
            val processExitCode = waitFor(this@TerminalSession.mShellPid)
            mMainThreadHandler.sendMessage(
                mMainThreadHandler.obtainMessage(
                    MSG_PROCESS_EXITED,
                    processExitCode
                )
            )
        }
    }

    /**
     * Write data to the shell process.
     */
    fun write(data: ByteArray?, offset: Int, count: Int) {
        if (this.mShellPid > 0) mTerminalToProcessIOQueue.write(data!!, offset, count)
    }

    fun write(data: String?) {
        if (data == null) return
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        this.write(bytes, 0, bytes.size)
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
        ) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (192 or (codePoint shr 6)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        } else if (codePoint <=  /* 16 bits */
            65535
        ) {
            /* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (224 or (codePoint shr 12)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or ((codePoint shr 6) and 63)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        } else {
            /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (240 or (codePoint shr 18)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or ((codePoint shr 12) and 63)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or ((codePoint shr 6) and 63)).toByte()
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        }
        this.write(this.mUtf8InputBuffer, 0, bufferPosition)
    }

    /**
     * Notify the [.mClient] that the screen has changed.
     */
    private fun notifyScreenUpdate() {
        mClient.onTextChanged(this)
    }

    /**
     * Finish this terminal session by sending SIGKILL to the shell.
     */
    fun finishIfRunning() {
        if (this.isRunning) {
            try {
                Os.kill(this.mShellPid, OsConstants.SIGKILL)
            } catch (ignored: ErrnoException) {
            }
        }
    }

    /**
     * Cleanup resources when the process exits.
     */
    private fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            this.mShellPid = -1
            this.mShellExitStatus = exitStatus
        }
        // Stop the reader and writer threads, and close the I/O streams
        mTerminalToProcessIOQueue.close()
        mProcessToTerminalIOQueue.close()
        close(this.mTerminalFileDescriptor)
    }

    val isRunning: Boolean
        get() {
            synchronized(this) {
                return this.mShellPid != -1
            }
        }

    val exitStatus: Int
        /**
         * Only valid if not [.isRunning].
         */
        get() {
            synchronized(this) {
                return this.mShellExitStatus
            }
        }

    fun onCopyTextToClipboard(text: String) {
        mClient.onCopyTextToClipboard(text)
    }


    fun onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard()
    }


    companion object {
        private const val MSG_NEW_INPUT = 1

        private const val MSG_PROCESS_EXITED = 4
        private fun wrapFileDescriptor(fileDescriptor: Int): FileDescriptor {
            val result = FileDescriptor()
            try {
                val descriptorField: Field = try {
                    FileDescriptor::class.java.getDeclaredField("descriptor")
                } catch (e: NoSuchFieldException) {
                    // For desktop java:
                    FileDescriptor::class.java.getDeclaredField("fd")
                }
                descriptorField.isAccessible = true
                descriptorField[result] = fileDescriptor
            } catch (e: NoSuchFieldException) {
                exitProcess(1)
            } catch (e: IllegalAccessException) {
                exitProcess(1)
            } catch (e: IllegalArgumentException) {
                exitProcess(1)
            }
            return result
        }
    }
}
