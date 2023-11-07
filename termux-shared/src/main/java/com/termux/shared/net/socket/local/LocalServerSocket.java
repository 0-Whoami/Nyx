package com.termux.shared.net.socket.local;

import androidx.annotation.NonNull;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.jni.models.JniResult;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The server socket for {@link LocalSocketManager}.
 */
public class LocalServerSocket implements Closeable {

    /**
     * The {@link LocalSocketManager} instance for the local socket.
     */
    @NonNull
    protected final LocalSocketManager mLocalSocketManager;

    /**
     * The {@link LocalSocketRunConfig} containing run config for the {@link LocalServerSocket}.
     */
    @NonNull
    protected final LocalSocketRunConfig mLocalSocketRunConfig;

    /**
     * The {@link ILocalSocketManager} client for the {@link LocalSocketManager}.
     */
    @NonNull
    protected final ILocalSocketManager mLocalSocketManagerClient;

    /**
     * The {@link ClientSocketListener} {@link Thread} for the {@link LocalServerSocket}.
     */
    @NonNull
    protected final Thread mClientSocketListener;

    /**
     * The required permissions for server socket file parent directory.
     * Creation of a new socket will fail if the server starter app process does not have
     * write and search (execute) permission on the directory in which the socket is created.
     */
    // Default: "rwx"
    public static final String SERVER_SOCKET_PARENT_DIRECTORY_PERMISSIONS = "rwx";

    /**
     * Create an new instance of {@link LocalServerSocket}.
     *
     * @param localSocketManager The {@link #mLocalSocketManager} value.
     */
    protected LocalServerSocket(@NonNull LocalSocketManager localSocketManager) {
        mLocalSocketManager = localSocketManager;
        mLocalSocketRunConfig = localSocketManager.getLocalSocketRunConfig();
        mLocalSocketManagerClient = mLocalSocketRunConfig.getLocalSocketManagerClient();
        mClientSocketListener = new Thread(new ClientSocketListener());
    }

    /**
     * Start server by creating server socket.
     */
    public synchronized Error start() {
        String path = mLocalSocketRunConfig.getPath();
        if (path == null || path.isEmpty()) {
            return LocalSocketErrno.ERRNO_SERVER_SOCKET_PATH_NULL_OR_EMPTY.getError(mLocalSocketRunConfig.getTitle());
        }
        if (mLocalSocketRunConfig.isAbstractNamespaceSocket()) {
            path = FileUtils.getCanonicalPath(path, null);
        }
        // On Linux, sun_path is 108 bytes (UNIX_PATH_MAX) in size, so do an early check here to
        // prevent useless parent directory creation since createServerSocket() call will fail since
        // there is a native check as well.
        if (path.getBytes(StandardCharsets.UTF_8).length > 108) {
            return LocalSocketErrno.ERRNO_SERVER_SOCKET_PATH_TOO_LONG.getError(mLocalSocketRunConfig.getTitle(), path);
        }
        int backlog = mLocalSocketRunConfig.getBacklog().intValue();
        if (backlog <= 0) {
            return LocalSocketErrno.ERRNO_SERVER_SOCKET_BACKLOG_INVALID.getError(mLocalSocketRunConfig.getTitle(), Integer.valueOf(backlog));
        }
        Error error;
        // If server socket is not in abstract namespace
        if (mLocalSocketRunConfig.isAbstractNamespaceSocket()) {
            if (!path.startsWith("/"))
                return LocalSocketErrno.ERRNO_SERVER_SOCKET_PATH_NOT_ABSOLUTE.getError(mLocalSocketRunConfig.getTitle(), path);
            // Create the server socket file parent directory and set SERVER_SOCKET_PARENT_DIRECTORY_PERMISSIONS if missing
            String socketParentPath = new File(path).getParent();
            error = FileUtils.validateDirectoryFileExistenceAndPermissions(mLocalSocketRunConfig.getTitle() + " server socket file parent", socketParentPath, null, true, SERVER_SOCKET_PARENT_DIRECTORY_PERMISSIONS, true, true, false, false);
            if (error != null)
                return error;
            // Delete the server socket file to stop any existing servers and for bind() to succeed
            error = deleteServerSocketFile();
            if (error != null)
                return error;
        }
        // Create the server socket
        JniResult result = LocalSocketManager.createServerSocket( " (server)", path.getBytes(StandardCharsets.UTF_8), backlog);
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_CREATE_SERVER_SOCKET_FAILED.getError(mLocalSocketRunConfig.getTitle(), JniResult.getErrorString(result));
        }
        int fd = result.intData;
        if (fd < 0) {
            return LocalSocketErrno.ERRNO_SERVER_SOCKET_FD_INVALID.getError(Integer.valueOf(fd), mLocalSocketRunConfig.getTitle());
        }
        // Update fd to signify that server socket has been created successfully
        mLocalSocketRunConfig.setFD(fd);
        mClientSocketListener.setUncaughtExceptionHandler(mLocalSocketManager.getLocalSocketManagerClientThreadUEH());
        try {
            // Start listening to server clients
            mClientSocketListener.start();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Stop server.
     */
    public synchronized Error stop() {
        try {
            // Stop the LocalClientSocket listener.
            mClientSocketListener.interrupt();
        } catch (Exception ignored) {
        }
        Error error = closeServerSocket();
        if (error != null)
            return error;
        return deleteServerSocketFile();
    }

    /**
     * Close server socket.
     */
    public synchronized Error closeServerSocket() {
        try {
            close();
        } catch (IOException e) {
            return LocalSocketErrno.ERRNO_CLOSE_SERVER_SOCKET_FAILED_WITH_EXCEPTION.getError(e, mLocalSocketRunConfig.getTitle(), e.getMessage());
        }
        return null;
    }

    /**
     * Implementation for {@link Closeable#close()} to close server socket.
     */
    @Override
    public synchronized void close() throws IOException {
        int fd = mLocalSocketRunConfig.getFD().intValue();
        if (fd >= 0) {
            JniResult result = LocalSocketManager.closeSocket(" (server)", fd);
            if (result == null || result.retval != 0) {
                throw new IOException(JniResult.getErrorString(result));
            }
            // Update fd to signify that server socket has been closed
            mLocalSocketRunConfig.setFD(-1);
        }
    }

    /**
     * Delete server socket file if not an abstract namespace socket. This will cause any existing
     * running server to stop.
     */
    private Error deleteServerSocketFile() {
        if (mLocalSocketRunConfig.isAbstractNamespaceSocket())
            return FileUtils.deleteSocketFile(mLocalSocketRunConfig.getTitle() + " server socket file", mLocalSocketRunConfig.getPath(), true);
        else
            return null;
    }

    /**
     * Listen and accept new {@link LocalClientSocket}.
     */
    public LocalClientSocket accept() {
        int clientFD;
        while (true) {
            // If server socket closed
            int fd = mLocalSocketRunConfig.getFD().intValue();
            if (fd < 0) {
                return null;
            }
            JniResult result = LocalSocketManager.accept( " (client)", fd);
            if (result == null || result.retval != 0) {
                mLocalSocketManager.onError(LocalSocketErrno.ERRNO_ACCEPT_CLIENT_SOCKET_FAILED.getError(mLocalSocketRunConfig.getTitle(), JniResult.getErrorString(result)));
                continue;
            }
            clientFD = result.intData;
            if (clientFD < 0) {
                mLocalSocketManager.onError(LocalSocketErrno.ERRNO_CLIENT_SOCKET_FD_INVALID.getError(Integer.valueOf(clientFD), mLocalSocketRunConfig.getTitle()));
                continue;
            }
            PeerCred peerCred = new PeerCred();
            result = LocalSocketManager.getPeerCred( " (client)", clientFD, peerCred);
            if (result == null || result.retval != 0) {
                mLocalSocketManager.onError(LocalSocketErrno.ERRNO_GET_CLIENT_SOCKET_PEER_UID_FAILED.getError(mLocalSocketRunConfig.getTitle(), JniResult.getErrorString(result)));
                LocalClientSocket.closeClientSocket(mLocalSocketManager, clientFD);
                continue;
            }
            int peerUid = peerCred.uid;
            if (peerUid < 0) {
                mLocalSocketManager.onError(LocalSocketErrno.ERRNO_CLIENT_SOCKET_PEER_UID_INVALID.getError(Integer.valueOf(peerUid), mLocalSocketRunConfig.getTitle()));
                LocalClientSocket.closeClientSocket(mLocalSocketManager, clientFD);
                continue;
            }
            LocalClientSocket clientSocket = new LocalClientSocket(mLocalSocketManager, clientFD, peerCred);
            // Only allow connection if the peer has the same uid as server app's user id or root user id
            if (peerUid != mLocalSocketManager.getContext().getApplicationInfo().uid && peerUid != 0) {
                mLocalSocketManager.onDisallowedClientConnected(clientSocket, LocalSocketErrno.ERRNO_CLIENT_SOCKET_PEER_UID_DISALLOWED.getError(clientSocket.getPeerCred().getMinimalString(), mLocalSocketManager.getLocalSocketRunConfig().getTitle()));
                clientSocket.closeClientSocket();
                continue;
            }
            return clientSocket;
        }
    }

    /**
     * The {@link LocalClientSocket} listener {@link Runnable} for {@link LocalServerSocket}.
     */
    protected class ClientSocketListener implements Runnable {

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    LocalClientSocket clientSocket = null;
                    try {
                        // Listen for new client socket connections
                        clientSocket = accept();
                        // If server socket is closed, then stop listener thread.
                        if (clientSocket == null)
                            break;
                        Error error;
                        error = clientSocket.setReadTimeout();
                        if (error != null) {
                            mLocalSocketManager.onError(clientSocket, error);
                            clientSocket.closeClientSocket();
                            continue;
                        }
                        error = clientSocket.setWriteTimeout();
                        if (error != null) {
                            mLocalSocketManager.onError(clientSocket, error);
                            clientSocket.closeClientSocket();
                            continue;
                        }
                        // Start new thread for client logic and pass control to ILocalSocketManager implementation
                        mLocalSocketManager.onClientAccepted(clientSocket);
                    } catch (Throwable t) {
                        mLocalSocketManager.onError(clientSocket, LocalSocketErrno.ERRNO_CLIENT_SOCKET_LISTENER_FAILED_WITH_EXCEPTION.getError(t, mLocalSocketRunConfig.getTitle(), t.getMessage()));
                        if (clientSocket != null)
                            clientSocket.closeClientSocket();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
