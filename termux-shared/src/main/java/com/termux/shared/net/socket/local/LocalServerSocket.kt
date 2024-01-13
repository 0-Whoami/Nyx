package com.termux.shared.net.socket.local

import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils.deleteSocketFile
import com.termux.shared.file.FileUtils.getCanonicalPath
import com.termux.shared.file.FileUtils.validateDirectoryFileExistenceAndPermissions
import com.termux.shared.jni.models.JniResult.Companion.getErrorString
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets


/**
 * The server socket for [LocalSocketManager].
 */
class LocalServerSocket internal constructor(
    /**
     * The [LocalSocketManager] instance for the local socket.
     */
    private val mLocalSocketManager: LocalSocketManager
) : Closeable {
    /**
     * The [LocalSocketRunConfig] containing run config for the [LocalServerSocket].
     */
    private val mLocalSocketRunConfig: LocalSocketRunConfig =
        mLocalSocketManager.localSocketRunConfig

    /**
     * The [ClientSocketListener] [Thread] for the [LocalServerSocket].
     */
    private val mClientSocketListener: Thread

    /**
     * Create an new instance of [LocalServerSocket].
     *
     * @param localSocketManager The [.mLocalSocketManager] value.
     */
    init {
        //mLocalSocketManagerClient = mLocalSocketRunConfig.getLocalSocketManagerClient();
        mClientSocketListener = Thread(ClientSocketListener())
    }

    /**
     * Start server by creating server socket.
     */
    @Synchronized
    fun start(): Error? {
        var path = mLocalSocketRunConfig.path
        if (path.isNullOrEmpty()) {
            return LocalSocketErrno.ERRNO_SERVER_SOCKET_PATH_NULL_OR_EMPTY.error
        }
        if (mLocalSocketRunConfig.isAbstractNamespaceSocket) {
            path = getCanonicalPath(path, null)
        }
        // On Linux, sun_path is 108 bytes (UNIX_PATH_MAX) in size, so do an early check here to
        // prevent useless parent directory creation since createServerSocket() call will fail since
        // there is a native check as well.
        if (path.toByteArray(StandardCharsets.UTF_8).size > 108) {
            return LocalSocketErrno.ERRNO_SERVER_SOCKET_PATH_TOO_LONG.error
        }
        val backlog = 50
        var error: Error?
        // If server socket is not in abstract namespace
        if (mLocalSocketRunConfig.isAbstractNamespaceSocket) {
            if (!(path.isNotEmpty() && path[0] == '/')) return LocalSocketErrno.ERRNO_SERVER_SOCKET_PATH_NOT_ABSOLUTE.error
            // Create the server socket file parent directory and set SERVER_SOCKET_PARENT_DIRECTORY_PERMISSIONS if missing
            val socketParentPath = File(path).parent
            error = validateDirectoryFileExistenceAndPermissions(
                mLocalSocketRunConfig.title + " server socket file parent",
                socketParentPath,
                null,
                true,
                SERVER_SOCKET_PARENT_DIRECTORY_PERMISSIONS,
                setPermissions = true,
                setMissingPermissionsOnly = true,
                ignoreErrorsIfPathIsInParentDirPath = false,
                ignoreIfNotExecutable = false
            )
            if (error != null) return error
            // Delete the server socket file to stop any existing servers and for bind() to succeed
            error = deleteServerSocketFile()
            if (error != null) return error
        }
        // Create the server socket
        val result =
            LocalSocketManager.createServerSocket(path.toByteArray(StandardCharsets.UTF_8), backlog)
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_CREATE_SERVER_SOCKET_FAILED.error
        }
        val fd = result.intData
        if (fd < 0) {
            return LocalSocketErrno.ERRNO_SERVER_SOCKET_FD_INVALID.error
        }
        // Update fd to signify that server socket has been created successfully
        mLocalSocketRunConfig.fD = fd
        //mClientSocketListener.setUncaughtExceptionHandler(null);
        try {
            // Start listening to server clients
            mClientSocketListener.start()
        } catch (ignored: Exception) {
        }
        return null
    }

    /**
     * Implementation for [Closeable.close] to close server socket.
     */
    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        val fd = mLocalSocketRunConfig.fD
        if (fd >= 0) {
            val result = LocalSocketManager.closeSocket(fd)
            if (result == null || result.retval != 0) {
                throw IOException(getErrorString(result))
            }
            // Update fd to signify that server socket has been closed
            mLocalSocketRunConfig.fD = -1
        }
    }

    /**
     * Delete server socket file if not an abstract namespace socket. This will cause any existing
     * running server to stop.
     */
    private fun deleteServerSocketFile(): Error? {
        return if (mLocalSocketRunConfig.isAbstractNamespaceSocket) deleteSocketFile(
            mLocalSocketRunConfig.title + " server socket file",
            mLocalSocketRunConfig.path,
            true
        )
        else null
    }

    /**
     * Listen and accept new [LocalClientSocket].
     */
    private fun accept(): LocalClientSocket? {
        var clientFD: Int
        while (true) {
            // If server socket closed
            val fd = mLocalSocketRunConfig.fD
            if (fd < 0) {
                return null
            }
            var result = LocalSocketManager.accept(fd)
            if (result == null || result.retval != 0) {
                continue
            }
            clientFD = result.intData
            if (clientFD < 0) {
                continue
            }
            result = LocalSocketManager.getPeerCred(clientFD)
            if (result == null || result.retval != 0) {
                LocalClientSocket.closeClientSocket(clientFD)
                continue
            }
            LocalClientSocket.closeClientSocket(clientFD)
            continue
        }
    }

    /**
     * The [LocalClientSocket] listener [Runnable] for [LocalServerSocket].
     */
    internal inner class ClientSocketListener : Runnable {
        override fun run() {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    var clientSocket: LocalClientSocket? = null
                    try {
                        // Listen for new client socket connections
                        clientSocket = accept()
                        // If server socket is closed, then stop listener thread.
                        if (clientSocket == null) break
                        var error = clientSocket.setReadTimeout()
                        if (error != null) {
                            clientSocket.closeClientSocket()
                            continue
                        }
                        error = clientSocket.setWriteTimeout()
                        if (error != null) {
                            clientSocket.closeClientSocket()
                            continue
                        }
                        // Start new thread for client logic and pass control to ILocalSocketManager implementation
                        mLocalSocketManager.onClientAccepted()
                    } catch (t: Throwable) {
                        clientSocket?.closeClientSocket()
                    }
                }
            } catch (ignored: Exception) {
            } finally {
                try {
                    close()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    companion object {
        /**
         * The required permissions for server socket file parent directory.
         * Creation of a new socket will fail if the server starter app process does not have
         * write and search (execute) permission on the directory in which the socket is created.
         */
        // Default: "rwx"
        private const val SERVER_SOCKET_PARENT_DIRECTORY_PERMISSIONS = "rwx"
    }
}
