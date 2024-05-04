package com.termux.terminal

import kotlin.math.min

const val BUFFER_SIZE = 4096

/**
 * A circular byte buffer allowing one producer and one consumer thread.
 */
internal class ByteQueue : Object() {
    private val mBuffer = ByteArray(BUFFER_SIZE)

    private var mHead = 0

    private var mStoredBytes = 0

    private var mOpen = true

    fun close() =
        synchronized(this) {
            mOpen = false
            notify()
        }

    /** Read Que and Write to [buffer] */
    fun read(buffer: ByteArray, block: Boolean): Int =
        synchronized(this) {
            while (0 == mStoredBytes && mOpen) {
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
            if (!mOpen) return -1
            var totalRead = 0
            val bufferLength = mBuffer.size
            val wasFull = bufferLength == mStoredBytes
            var length = buffer.size
            var offset = 0
            while (0 < length && 0 < mStoredBytes) {
                val bytesToCopy = min(length, min(bufferLength - mHead, mStoredBytes))
                System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy)
                mHead += bytesToCopy
                if (mHead >= bufferLength) mHead = 0
                mStoredBytes -= bytesToCopy
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
        val bufferLength = mBuffer.size
        synchronized(this) {
            while (0 < lengthToWrite1) {
                while (bufferLength == mStoredBytes && mOpen) {
                    try {
                        wait()
                    } catch (e: InterruptedException) {
                        // Ignore.
                    }
                }
                if (!mOpen) return false
                val wasEmpty = 0 == mStoredBytes
                var bytesToWriteBeforeWaiting = min(lengthToWrite1, bufferLength - mStoredBytes)
                lengthToWrite1 -= bytesToWriteBeforeWaiting
                while (0 < bytesToWriteBeforeWaiting) {
                    var tail = mHead + mStoredBytes
                    var oneRun: Int
                    if (tail >= bufferLength) {
                        tail -= bufferLength
                        oneRun = mHead - tail
                    } else {
                        oneRun = bufferLength - tail
                    }
                    val bytesToCopy = min(oneRun, bytesToWriteBeforeWaiting)
                    System.arraycopy(buffer, offset1, mBuffer, tail, bytesToCopy)
                    offset1 += bytesToCopy
                    bytesToWriteBeforeWaiting -= bytesToCopy
                    mStoredBytes += bytesToCopy
                }
                if (wasEmpty) notify()
            }
        }
        return true
    }
}
