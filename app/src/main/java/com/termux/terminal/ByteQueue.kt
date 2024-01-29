package com.termux.terminal

import kotlin.math.min

/**
 * A circular byte buffer allowing one producer and one consumer thread.
 */
internal class ByteQueue : Object() {
    private val mBuffer = ByteArray(4096)

    private var mHead = 0

    private var mStoredBytes = 0

    private var mOpen = true

    fun close() {
        synchronized(this) {
            mOpen = false
            (this).notifyAll()
        }
    }

    fun read(buffer: ByteArray, block: Boolean): Int {
        synchronized(this) {
            while (0 == this.mStoredBytes && mOpen) {
                if (block) {
                    try {
                        (this).wait()
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
            while (0 < length && 0 < this.mStoredBytes) {
                val oneRun = min((bufferLength - mHead).toDouble(), mStoredBytes.toDouble())
                    .toInt()
                val bytesToCopy = min(length.toDouble(), oneRun.toDouble()).toInt()
                System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy)
                mHead += bytesToCopy
                if (mHead >= bufferLength) mHead = 0
                mStoredBytes -= bytesToCopy
                length -= bytesToCopy
                offset += bytesToCopy
                totalRead += bytesToCopy
            }
            if (wasFull) (this).notifyAll()
            return totalRead
        }
    }

    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     *
     *
     * Returns whether the output was totally written, false if it was closed before.
     */
    fun write(buffer: ByteArray, offset: Int, lengthToWrite: Int): Boolean {
//        var offset = offset
        var lengthToWrite1 = lengthToWrite
//        require(lengthToWrite + offset <= buffer.size) { "length + offset > buffer.length" }
//        require(0 < lengthToWrite) { "length <= 0" }
        val bufferLength = mBuffer.size
        synchronized(this) {
            while (0 < lengthToWrite1) {
                while (bufferLength == mStoredBytes && mOpen) {
                    try {
                        (this).wait()
                    } catch (e: InterruptedException) {
                        // Ignore.
                    }
                }
                if (!mOpen) return false
                val wasEmpty = 0 == this.mStoredBytes
                var bytesToWriteBeforeWaiting =
                    min(lengthToWrite1.toDouble(), (bufferLength - mStoredBytes).toDouble())
                        .toInt()
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
                    val bytesToCopy = min(oneRun.toDouble(), bytesToWriteBeforeWaiting.toDouble())
                        .toInt()
                    System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy)
//                    offset += bytesToCopy
                    bytesToWriteBeforeWaiting -= bytesToCopy
                    mStoredBytes += bytesToCopy
                }
                if (wasEmpty) (this).notifyAll()
            }
        }
        return true
    }
}
