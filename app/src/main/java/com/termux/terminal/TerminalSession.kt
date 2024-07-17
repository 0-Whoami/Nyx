package com.termux.terminal

import android.system.Os
import android.system.OsConstants
import com.termux.data.ConfigManager.transcriptRows
import com.termux.data.console
import com.termux.terminal.JNI.process
import com.termux.terminal.JNI.size
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Field

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
class TerminalSession(failsafe : Boolean) {
    /**
     * Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue
     */
    private val mUtf8InputBuffer = ByteArray(5)

    val emulator : TerminalEmulator = TerminalEmulator(this, 4, 4, transcriptRows)

    /**
     * The pid of the shell process. 0 if not started and -1 if finished running.
     */
    private val mShellPid : Int

    /**
     * The file descriptor referencing the master half of a pseudo-terminal pair, resulting from calling
     * [JNI.process].
     */
    private val mTerminalFileDescriptor : Int

    private val termOut : FileOutputStream

    init {
        val processId = IntArray(1)
        mTerminalFileDescriptor = process(failsafe, processId, 4, 4)
        mShellPid = processId[0]
        val terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor)
        Thread {
            try { //"TermSessionInputReader[pid=" + mShellPid + "]"
                val termIn = FileInputStream(terminalFileDescriptorWrapped)
                val buffer = ByteArray(2048)
                while (true) {
                    val read : Int = termIn.read(buffer)
                    if (read == -1) return@Thread
                    emulator.append(buffer, read)
                    notifyScreenUpdate()
                }

            } catch (_ : Throwable) { // Ignore, just shutting down.
            }
        }.start()
        termOut = FileOutputStream(terminalFileDescriptorWrapped)
    }

    /**
     * Inform the attached pty of the new size and reflow or initialize the emulator.
     */
    fun updateSize(columns : Int, rows : Int) {
        size(mTerminalFileDescriptor, rows, columns)
        emulator.resize(columns, rows)
    }


    /**
     * Write data to the shell process.
     */
    fun write(data : ByteArray, count : Int) {
        if (mShellPid > 0) termOut.write(data, 0, count)
    }

    fun write(data : String?) {
        if (data == null) return
        val bytes = data.toByteArray()
        write(bytes, bytes.size)
    }

    /**
     * Write the Unicode code point to the terminal encoded in UTF-8.
     */
    fun writeCodePoint(prependEscape : Boolean, codePoint : Int) {
        var bufferPosition = 0
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27
        if (codePoint <=  /* 7 bits */
            127) {
            mUtf8InputBuffer[bufferPosition++] = codePoint.toByte()
        } else if (codePoint <=  /* 11 bits */
            2047) {/* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (192 or (codePoint shr 6)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        } else if (codePoint <=  /* 16 bits */
            65535) {/* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (224 or (codePoint shr 12)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or ((codePoint shr 6) and 63)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        } else {/* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 *//* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (240 or (codePoint shr 18)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or ((codePoint shr 12) and 63)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or ((codePoint shr 6) and 63)).toByte()/* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (128 or (codePoint and 63)).toByte()
        }
        write(mUtf8InputBuffer, bufferPosition)
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
        try {
            Os.kill(mShellPid, OsConstants.SIGKILL)
        } catch (_ : Throwable) {
        }
    }

    fun onCopyTextToClipboard(text : String) : Unit = console.onCopyTextToClipboard(text)


    private fun wrapFileDescriptor(fileDescriptor : Int) : FileDescriptor {
        val result = FileDescriptor()
        val descriptorField : Field = try {
            FileDescriptor::class.java.getDeclaredField("descriptor")
        } catch (e : NoSuchFieldException) { // For desktop java:
            FileDescriptor::class.java.getDeclaredField("fd")
        }
        descriptorField.isAccessible = true
        descriptorField[result] = fileDescriptor
        return result
    }
}
