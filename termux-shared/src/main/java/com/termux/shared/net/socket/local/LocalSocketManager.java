package com.termux.shared.net.socket.local;

import android.content.Context;

import com.termux.shared.jni.models.JniResult;

/**
 * Manager for an AF_UNIX/SOCK_STREAM local server.
 * <p>
 * Usage:
 * 1. Implement the {@link ILocalSocketManager} that will receive call backs from the server including
 * when client connects via .
 * Optionally extend the {@link LocalSocketManagerClientBase} class that provides base implementation.
 * 2. Create a {@link LocalSocketRunConfig} instance with the run config of the server.
 * 3. Create a {@link LocalSocketManager} instance and call .
 * 4. Stop server if needed with a call to .
 */
public class LocalSocketManager {


    /**
     * The {@link ILocalSocketManager} client for the {@link LocalSocketManager}.
     */

    private final LocalSocketManager mLocalSocketManagerClient;
    /**
     * The {@link Context} that may needed for various operations.
     */

    private final Context mContext;
    /**
     * The {@link LocalSocketRunConfig} containing run config for the {@link LocalSocketManager}.
     */

    private final LocalSocketRunConfig mLocalSocketRunConfig;

    /**
     * Create an new instance of .
     *
     * @param context              The {@link #mContext} value.
     * @param localSocketRunConfig The {@link #mLocalSocketRunConfig} value.
     */
    public LocalSocketManager(Context context, LocalSocketRunConfig localSocketRunConfig) {
        mContext = context.getApplicationContext();
        mLocalSocketRunConfig = localSocketRunConfig;
        mLocalSocketManagerClient = mLocalSocketRunConfig.getLocalSocketManagerClient();
    }

    /**
     * Creates an AF_UNIX/SOCK_STREAM local server socket at {@code path}, with the specified backlog.
     *
     * @param path    The path at which to create the socket.
     *                For a filesystem socket, this must be an absolute path to the socket file.
     *                For an abstract namespace socket, the first byte must be a null `\0` character.
     *                Max allowed length is 108 bytes as per sun_path size (UNIX_PATH_MAX) on Linux.
     * @param backlog The maximum length to which the queue of pending connections for the socket
     *                may grow. This value may be ignored or may not have one-to-one mapping
     *                in kernel implementation. Value must be greater than 0.
     * @return Returns the {@link JniResult}. If server creation was successful, then
     * {@link JniResult#retval} will be 0 and {@link JniResult#intData} will contain the server socket
     * fd.
     */

    public static JniResult createServerSocket(byte[] path, int backlog) {
        try {
            return createServerSocketNative(path, backlog);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    /**
     * Closes the socket with fd.
     *
     * @param fd The socket fd.
     * @return Returns the {@link JniResult}. If closing socket was successful, then
     * {@link JniResult#retval} will be 0.
     */

    public static JniResult closeSocket(int fd) {
        try {
            return closeSocketNative(fd);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    /*
     Note: Exceptions thrown from JNI must be caught with Throwable class instead of Exception,
     otherwise exception will be sent to UncaughtExceptionHandler of the thread.
    */

    /**
     * Accepts a connection on the supplied server socket fd.
     *
     * @param fd The server socket fd.
     * @return Returns the {@link JniResult}. If accepting socket was successful, then
     * {@link JniResult#retval} will be 0 and {@link JniResult#intData} will contain the client socket
     * fd.
     */

    public static JniResult accept(int fd) {
        try {
            return acceptNative(fd);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    /**
     * Set receiving (SO_RCVTIMEO) timeout in milliseconds for socket.
     *
     * @param fd      The socket fd.
     * @param timeout The timeout value in milliseconds.
     * @return Returns the {@link JniResult}. If setting timeout was successful, then
     * {@link JniResult#retval} will be 0.
     */

    public static JniResult setSocketReadTimeout(int fd, int timeout) {
        try {
            return setSocketReadTimeoutNative(fd, timeout);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    /**
     * Set sending (SO_SNDTIMEO) timeout in milliseconds for fd.
     *
     * @param fd      The socket fd.
     * @param timeout The timeout value in milliseconds.
     * @return Returns the {@link JniResult}. If setting timeout was successful, then
     * {@link JniResult#retval} will be 0.
     */

    public static JniResult setSocketSendTimeout(int fd, int timeout) {
        try {
            return setSocketSendTimeoutNative(fd, timeout);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    /**
     * Get the {@link PeerCred} for the socket.
     *
     * @param fd The socket fd.
     * @return Returns the {@link JniResult}. If setting timeout was successful, then
     * {@link JniResult#retval} will be 0.
     */

    public static JniResult getPeerCred(int fd) {
        try {
            return getPeerCredNative(fd);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    private static native JniResult createServerSocketNative(byte[] path, int backlog);

    private static native JniResult closeSocketNative(int fd);

    private static native JniResult acceptNative(int fd);

    private static native JniResult setSocketReadTimeoutNative(int fd, int timeout);

    private static native JniResult setSocketSendTimeoutNative(int fd, int timeout);

    private static native JniResult getPeerCredNative(int fd);

    /**
     * All client accept logic must be run on separate threads so that incoming client acceptance is not blocked.
     */
    private static void startLocalSocketManagerClientThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        try {
            thread.start();
        } catch (Exception ignored) {
        }
    }

    /**
     * Wrapper to call  in a new thread.
     */
    public final void onClientAccepted() {
        startLocalSocketManagerClientThread(mLocalSocketManagerClient::onClientAccepted);
    }

    /**
     * Get {@link #mContext}.
     */
    public final Context getContext() {
        return mContext;
    }

    /**
     * Get {@link #mLocalSocketRunConfig}.
     */
    public final LocalSocketRunConfig getLocalSocketRunConfig() {
        return mLocalSocketRunConfig;
    }
}
