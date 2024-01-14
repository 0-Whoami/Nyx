package com.termux.terminal;

/**
 * A circular byte buffer allowing one producer and one consumer thread.
 */
final class ByteQueue {

    private final byte[] mBuffer;

    private int mHead;

    private int mStoredBytes;

    private boolean mOpen = true;

    ByteQueue() {
        this.mBuffer = new byte[4096];
    }

    public synchronized void close() {
        this.mOpen = false;
        this.notifyAll();
    }

    public synchronized int read(final byte[] buffer, final boolean block) {
        while (0 == mStoredBytes && this.mOpen) {
            if (block) {
                try {
                    this.wait();
                } catch (final InterruptedException e) {
                    // Ignore.
                }
            } else {
                return 0;
            }
        }
        if (!this.mOpen)
            return -1;
        int totalRead = 0;
        final int bufferLength = this.mBuffer.length;
        final boolean wasFull = bufferLength == this.mStoredBytes;
        int length = buffer.length;
        int offset = 0;
        while (0 < length && 0 < mStoredBytes) {
            final int oneRun = Math.min(bufferLength - this.mHead, this.mStoredBytes);
            final int bytesToCopy = Math.min(length, oneRun);
            System.arraycopy(this.mBuffer, this.mHead, buffer, offset, bytesToCopy);
            this.mHead += bytesToCopy;
            if (this.mHead >= bufferLength)
                this.mHead = 0;
            this.mStoredBytes -= bytesToCopy;
            length -= bytesToCopy;
            offset += bytesToCopy;
            totalRead += bytesToCopy;
        }
        if (wasFull)
            this.notifyAll();
        return totalRead;
    }

    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     * <p/>
     * Returns whether the output was totally written, false if it was closed before.
     */
    public boolean write(final byte[] buffer, int offset, int lengthToWrite) {
        if (lengthToWrite + offset > buffer.length) {
            throw new IllegalArgumentException("length + offset > buffer.length");
        } else if (0 >= lengthToWrite) {
            throw new IllegalArgumentException("length <= 0");
        }
        int bufferLength = this.mBuffer.length;
        synchronized (this) {
            while (0 < lengthToWrite) {
                while (bufferLength == this.mStoredBytes && this.mOpen) {
                    try {
                        this.wait();
                    } catch (final InterruptedException e) {
                        // Ignore.
                    }
                }
                if (!this.mOpen)
                    return false;
                boolean wasEmpty = 0 == mStoredBytes;
                int bytesToWriteBeforeWaiting = Math.min(lengthToWrite, bufferLength - this.mStoredBytes);
                lengthToWrite -= bytesToWriteBeforeWaiting;
                while (0 < bytesToWriteBeforeWaiting) {
                    int tail = this.mHead + this.mStoredBytes;
                    final int oneRun;
                    if (tail >= bufferLength) {
                        // Buffer: [.............]
                        // ________________H_______T
                        // =>
                        // Buffer: [.............]
                        // ___________T____H
                        // onRun= _____----_
                        tail = tail - bufferLength;
                        oneRun = this.mHead - tail;
                    } else {
                        oneRun = bufferLength - tail;
                    }
                    final int bytesToCopy = Math.min(oneRun, bytesToWriteBeforeWaiting);
                    System.arraycopy(buffer, offset, this.mBuffer, tail, bytesToCopy);
                    offset += bytesToCopy;
                    bytesToWriteBeforeWaiting -= bytesToCopy;
                    this.mStoredBytes += bytesToCopy;
                }
                if (wasEmpty)
                    this.notifyAll();
            }
        }
        return true;
    }
}
