package com.termux.shared.net.socket.local

import com.termux.shared.errors.Errno

internal class LocalSocketErrno(type: String?, code: Int, message: String?) : Errno(
    type!!, code, message!!
) {
    companion object {
        private const val TYPE = "LocalSocket Error"

        /**
         * Errors for [LocalServerSocket] (150-200)
         */
        val ERRNO_SERVER_SOCKET_PATH_NULL_OR_EMPTY: Errno =
            Errno(TYPE, 150, "The \"%1\$s\" server socket path is null or empty.")

        val ERRNO_SERVER_SOCKET_PATH_TOO_LONG: Errno = Errno(
            TYPE,
            151,
            "The \"%1\$s\" server socket path \"%2\$s\" is greater than 108 bytes."
        )

        val ERRNO_SERVER_SOCKET_PATH_NOT_ABSOLUTE: Errno = Errno(
            TYPE,
            152,
            "The \"%1\$s\" server socket path \"%2\$s\" is not an absolute file path."
        )

        val ERRNO_CREATE_SERVER_SOCKET_FAILED: Errno =
            Errno(TYPE, 154, "Create \"%1\$s\" server socket failed.\n%2\$s")

        val ERRNO_SERVER_SOCKET_FD_INVALID: Errno = Errno(
            TYPE,
            155,
            "Invalid file descriptor \"%1\$s\" returned when creating \"%2\$s\" server socket."
        )

        /**
         * Errors for [LocalClientSocket] (200-250)
         */
        val ERRNO_SET_CLIENT_SOCKET_READ_TIMEOUT_FAILED: Errno = Errno(
            TYPE,
            200,
            "Set \"%1\$s\" client socket read (SO_RCVTIMEO) timeout to \"%2\$s\" failed.\n%3\$s"
        )

        val ERRNO_SET_CLIENT_SOCKET_SEND_TIMEOUT_FAILED: Errno = Errno(
            TYPE,
            201,
            "Set \"%1\$s\" client socket send (SO_SNDTIMEO) timeout \"%2\$s\" failed.\n%3\$s"
        )
    }
}
