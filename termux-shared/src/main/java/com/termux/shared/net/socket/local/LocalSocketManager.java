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
 * 3. Create a {@link LocalSocketManager} instance and call {@link #start()}.
 * 4. Stop server if needed with a call to {@link #stop()}.
 */
public class LocalSocketManager {


    /**
     * The {@link ILocalSocketManager} client for the {@link LocalSocketManager}.
     */

    protected final LocalSocketManager mLocalSocketManagerClient;
    /**
     * The {@link Context} that may needed for various operations.
     */

    private final Context mContext;
    /**
     * The {@link LocalSocketRunConfig} containing run config for the {@link LocalSocketManager}.
     */

    private final LocalSocketRunConfig mLocalSocketRunConfig;

    /**
     * Create an new instance of {@link LocalSocketManager}.
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
     * Attempts to read up to data buffer length bytes from file descriptor fd into the data buffer.
     * On success, the number of bytes read is returned (zero indicates end of file).
     * It is not an error if bytes read is smaller than the number of bytes requested; this may happen
     * for example because fewer bytes are actually available right now (maybe because we were close
     * to end-of-file, or because we are reading from a pipe), or because read() was interrupted by
     * a signal. On error, the  and  will be set.
     * <p>
     * If while reading the deadline elapses but all the data has not been read, the call will fail.
     *
     * @param fd       The socket fd.
     * @param data     The data buffer to read bytes into.
     * @param deadline The deadline milliseconds since epoch.
     * @return Returns the {@link JniResult}. If reading was successful, then {@link JniResult#retval}
     * will be 0 and {@link JniResult#intData} will contain the bytes read.
     */

    public static JniResult read(int fd, byte[] data, long deadline) {
        try {
            return readNative(fd, data, deadline);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    /**
     * Attempts to send data buffer to the file descriptor. On error, the  and
     * will be set.
     * <p>
     * If while sending the deadline elapses but all the data has not been sent, the call will fail.
     *
     * @param fd       The socket fd.
     * @param data     The data buffer containing bytes to send.
     * @param deadline The deadline milliseconds since epoch.
     * @return Returns the {@link JniResult}. If sending was successful, then {@link JniResult#retval}
     * will be 0.
     */

    public static JniResult send(int fd, byte[] data, long deadline) {
        try {
            return sendNative(fd, data, deadline);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    /**
     * Gets the number of bytes available to read on the socket.
     *
     * @param fd The socket fd.
     * @return Returns the {@link JniResult}. If checking availability was successful, then
     * {@link JniResult#retval} will be 0 and {@link JniResult#intData} will contain the bytes available.
     */

    public static JniResult available(int fd) {
        try {
            return availableNative(fd);
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
     * @param fd       The socket fd.
     * @param peerCred The {@link PeerCred} object that should be filled.
     * @return Returns the {@link JniResult}. If setting timeout was successful, then
     * {@link JniResult#retval} will be 0.
     */

    public static JniResult getPeerCred(int fd, PeerCred peerCred) {
        try {
            return getPeerCredNative(fd, peerCred);
        } catch (Throwable t) {
            return new JniResult();
        }
    }

    private static native JniResult createServerSocketNative(byte[] path, int backlog);

    private static native JniResult closeSocketNative(int fd);

    private static native JniResult acceptNative(int fd);

    private static native JniResult readNative(int fd, byte[] data, long deadline);

    private static native JniResult sendNative(int fd, byte[] data, long deadline);

    private static native JniResult availableNative(int fd);

    private static native JniResult setSocketReadTimeoutNative(int fd, int timeout);

    private static native JniResult setSocketSendTimeoutNative(int fd, int timeout);

    private static native JniResult getPeerCredNative(int fd, PeerCred peerCred);

    /**
     * Wrapper to call  in a new thread.
     */
    public final void onClientAccepted(LocalClientSocket clientSocket) {
        startLocalSocketManagerClientThread(() -> mLocalSocketManagerClient.onClientAccepted(clientSocket));
    }

    /**
     * All client accept logic must be run on separate threads so that incoming client acceptance is not blocked.
     */
    private void startLocalSocketManagerClientThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        try {
            thread.start();
        } catch (Exception ignored) {
        }
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
