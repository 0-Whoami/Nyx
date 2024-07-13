package com.termux.terminal

import kotlin.math.min

const val BUFFER_SIZE: Int = 4096

/**
 * A circular byte buffer allowing one producer and one consumer thread.
 */
internal class ByteQueue : Object() {
    private val buffer = ByteArray(BUFFER_SIZE)

    private var head = 0

    private var storedBytes = 0

    private var isOpen = true

    fun close() =
        synchronized(this) {
            isOpen = false
            notify()
        }

    /** Read Que and Write to [buffer] */
    fun read(buffer: ByteArray, block: Boolean): Int =
        synchronized(this) {
            while (0 == storedBytes && isOpen) {
                if (block) {
                    try {
                        wait()
                    } catch (e: InterruptedException) {
                        // Ignore.
                    }
                } else {
                    return 0
                }
            }
            if (!isOpen) return -1
            var totalRead = 0
            val bufferLength = this.buffer.size
            val wasFull = bufferLength == storedBytes
            var length = buffer.size
            var offset = 0
            while (0 < length && 0 < storedBytes) {
                val bytesToCopy = min(length, min(bufferLength - head, storedBytes))
                System.arraycopy(this.buffer, head, buffer, offset, bytesToCopy)
                head += bytesToCopy
                if (head >= bufferLength) head = 0
                storedBytes -= bytesToCopy
                length -= bytesToCopy
                offset += bytesToCopy
                totalRead += bytesToCopy
            }
            if (wasFull) notify()
            return totalRead
        }


    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     * Returns whether the output was totally written, false if it was closed before.
     */
    fun write(buffer: ByteArray, offset: Int, lengthToWrite: Int): Boolean {
        var lengthToWrite1 = lengthToWrite
        var offset1 = offset
        val bufferLength = this.buffer.size
        synchronized(this) {
            while (0 < lengthToWrite1) {
                while (bufferLength == storedBytes && isOpen) {
                    try {
                        wait()
                    } catch (e: InterruptedException) {
                        // Ignore.
                    }
                }
                if (!isOpen) return false
                val wasEmpty = 0 == storedBytes
                var bytesToWriteBeforeWaiting = min(lengthToWrite1, bufferLength - storedBytes)
                lengthToWrite1 -= bytesToWriteBeforeWaiting
                while (0 < bytesToWriteBeforeWaiting) {
                    var tail = head + storedBytes
                    var oneRun: Int
                    if (tail >= bufferLength) {
                        tail -= bufferLength
                        oneRun = head - tail
                    } else {
                        oneRun = bufferLength - tail
                    }
                    val bytesToCopy = min(oneRun, bytesToWriteBeforeWaiting)
                    System.arraycopy(buffer, offset1, this.buffer, tail, bytesToCopy)
                    offset1 += bytesToCopy
                    bytesToWriteBeforeWaiting -= bytesToCopy
                    storedBytes += bytesToCopy
                }
                if (wasEmpty) notify()
            }
        }
        return true
    }
}
