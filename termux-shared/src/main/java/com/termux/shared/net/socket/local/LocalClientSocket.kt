package com.termux.shared.net.socket.local

import com.termux.shared.errors.Error
import com.termux.shared.jni.models.JniResult.Companion.getErrorString
import com.termux.shared.net.socket.local.LocalClientSocket
import java.io.Closeable
import java.io.IOException


/**
 * The client socket for [LocalSocketManager].
 */
class LocalClientSocket internal constructor(
    fd: Int,
) : Closeable {
    /**
     * The [LocalClientSocket] file descriptor.
     * Value will be `>= 0` if socket has been connected and `-1` if closed.
     */
    private var mFD = 0

    /**
     * Create an new instance of [LocalClientSocket].
     *
     * @param localSocketManager The  value.
     * @param fd                 The [.mFD] value.
     * @param peerCred           The [.mPeerCred] value.
     */
    init {
        setFD(fd)
    }

    /**
     * Close client socket.
     */
    @Synchronized
    fun closeClientSocket() {
        try {
            close()
        } catch (ignored: IOException) {
        }
    }

    /**
     * Implementation for [Closeable.close] to close client socket.
     */
    @Throws(IOException::class)
    override fun close() {
        if (mFD >= 0) {
            val result = LocalSocketManager.closeSocket(mFD)
            if (result == null || result.retval != 0) {
                throw IOException(getErrorString(result))
            }
            // Update fd to signify that client socket has been closed
            setFD(-1)
        }
    }


    /**
     * Set [LocalClientSocket] receiving (SO_RCVTIMEO) timeout to value returned by .
     */
    fun setReadTimeout(): Error? {
        if (mFD >= 0) {
            val result = LocalSocketManager.setSocketReadTimeout(mFD, 10000)
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_READ_TIMEOUT_FAILED.error
            }
        }
        return null
    }

    /**
     * Set [LocalClientSocket] sending (SO_SNDTIMEO) timeout to value returned by .
     */
    fun setWriteTimeout(): Error? {
        if (mFD >= 0) {
            val result = LocalSocketManager.setSocketSendTimeout(mFD, 10000)
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_SEND_TIMEOUT_FAILED.error
            }
        }
        return null
    }

    /**
     * Set [.mFD]. Value must be greater than 0 or -1.
     */
    private fun setFD(fd: Int) {
        mFD = if (fd >= 0) fd
        else -1
    }


    companion object {
        /**
         * Close client socket that exists at fd.
         */
        fun closeClientSocket(fd: Int) {
            LocalClientSocket(fd).closeClientSocket()
        }
    }
}
