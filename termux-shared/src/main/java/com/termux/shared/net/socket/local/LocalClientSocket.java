package com.termux.shared.net.socket.local;


import com.termux.shared.errors.Error;
import com.termux.shared.jni.models.JniResult;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * The client socket for {@link LocalSocketManager}.
 */
public class LocalClientSocket implements Closeable {

    /**
     * The {@link LocalSocketRunConfig} containing run config for the {@link LocalClientSocket}.
     */

    private final LocalSocketRunConfig mLocalSocketRunConfig;

    /**
     * The {@link PeerCred} of the {@link LocalClientSocket} containing info of client/peer.
     */

    private final PeerCred mPeerCred;
    /**
     * The {@link OutputStream} implementation for the {@link LocalClientSocket}.
     */

    private final SocketOutputStream mOutputStream;
    /**
     * The {@link InputStream} implementation for the {@link LocalClientSocket}.
     */

    private final SocketInputStream mInputStream;
    /**
     * The {@link LocalClientSocket} file descriptor.
     * Value will be `>= 0` if socket has been connected and `-1` if closed.
     */
    private int mFD;

    /**
     * Create an new instance of {@link LocalClientSocket}.
     *
     * @param localSocketManager The  value.
     * @param fd                 The {@link #mFD} value.
     * @param peerCred           The {@link #mPeerCred} value.
     */
    LocalClientSocket(LocalSocketManager localSocketManager, int fd, PeerCred peerCred) {
        mLocalSocketRunConfig = localSocketManager.getLocalSocketRunConfig();
        mOutputStream = new SocketOutputStream();
        mInputStream = new SocketInputStream();
        mPeerCred = peerCred;
        setFD(fd);
        mPeerCred.fillPeerCred(localSocketManager.getContext());
    }

    /**
     * Close client socket that exists at fd.
     */
    public static void closeClientSocket(LocalSocketManager localSocketManager, int fd) {
        new LocalClientSocket(localSocketManager, fd, new PeerCred()).closeClientSocket();
    }

    /**
     * Close client socket.
     */
    public final synchronized void closeClientSocket() {
        try {
            close();
        } catch (IOException ignored) {

        }
    }

    /**
     * Implementation for {@link Closeable#close()} to close client socket.
     */
    @Override
    public final void close() throws IOException {
        if (mFD >= 0) {
            JniResult result = LocalSocketManager.closeSocket(mFD);
            if (result == null || result.retval != 0) {
                throw new IOException(JniResult.getErrorString(result));
            }
            // Update fd to signify that client socket has been closed
            setFD(-1);
        }
    }

    /**
     * Attempts to read up to data buffer length bytes from file descriptor into the data buffer.
     * On success, the number of bytes read is returned (zero indicates end of file) in bytesRead.
     * It is not an error if bytesRead is smaller than the number of bytes requested; this may happen
     * for example because fewer bytes are actually available right now (maybe because we were close
     * to end-of-file, or because we are reading from a pipe), or because read() was interrupted by
     * a signal.
     * <p>
     * If while reading the  + the milliseconds returned by
     * elapses but all the data has not been read, an
     * error would be returned.
     * <p>
     * This is a wrapper for , which can
     * be called instead if you want to get access to errno int value instead of {@link JniResult}
     * error {@link String}.
     *
     * @param data      The data buffer to read bytes into.
     * @param bytesRead The actual bytes read.
     * @return Returns the {@code error} if reading was not successful containing {@link JniResult}
     * error {@link String}, otherwise {@code null}.
     */
    private Error read(byte[] data, MutableInt bytesRead) {
        bytesRead.value = 0;
        if (mFD < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError();
        }
        JniResult result = LocalSocketManager.read(mFD, data, 0);
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_READ_DATA_FROM_CLIENT_SOCKET_FAILED.getError();
        }
        bytesRead.value = result.intData;
        return null;
    }

    /**
     * Attempts to send data buffer to the file descriptor.
     * <p>
     * If while sending the  + the milliseconds returned by
     * elapses but all the data has not been sent, an
     * error would be returned.
     * <p>
     * This is a wrapper for , which can
     * be called instead if you want to get access to errno int value instead of {@link JniResult}
     * error {@link String}.
     *
     * @param data The data buffer containing bytes to send.
     * @return Returns the {@code error} if sending was not successful containing {@link JniResult}
     * error {@link String}, otherwise {@code null}.
     */
    private Error send(byte[] data) {
        if (mFD < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError();
        }
        JniResult result = LocalSocketManager.send(mFD, data, 0);
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_SEND_DATA_TO_CLIENT_SOCKET_FAILED.getError();
        }
        return null;
    }


    /**
     * Get available bytes on {@link #mInputStream} and optionally check if value returned by
     * has passed.
     */
    private Error available(MutableInt available) {
        available.value = 0;
        if (mFD < 0) {
            return LocalSocketErrno.ERRNO_USING_CLIENT_SOCKET_WITH_INVALID_FD.getError();
        }
        JniResult result = LocalSocketManager.available(mLocalSocketRunConfig.getFD());
        if (result == null || result.retval != 0) {
            return LocalSocketErrno.ERRNO_CHECK_AVAILABLE_DATA_ON_CLIENT_SOCKET_FAILED.getError();
        }
        available.value = result.intData;
        return null;
    }

    /**
     * Set {@link LocalClientSocket} receiving (SO_RCVTIMEO) timeout to value returned by .
     */
    public final Error setReadTimeout() {
        if (mFD >= 0) {
            JniResult result = LocalSocketManager.setSocketReadTimeout(mFD, 10000);
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_READ_TIMEOUT_FAILED.getError();
            }
        }
        return null;
    }

    /**
     * Set {@link LocalClientSocket} sending (SO_SNDTIMEO) timeout to value returned by .
     */
    public final Error setWriteTimeout() {
        if (mFD >= 0) {
            JniResult result = LocalSocketManager.setSocketSendTimeout(mFD, 10000);
            if (result == null || result.retval != 0) {
                return LocalSocketErrno.ERRNO_SET_CLIENT_SOCKET_SEND_TIMEOUT_FAILED.getError();
            }
        }
        return null;
    }

    /**
     * Set {@link #mFD}. Value must be greater than 0 or -1.
     */
    private void setFD(int fd) {
        if (fd >= 0)
            mFD = fd;
        else
            mFD = -1;
    }


    /**
     * Get {@link #mOutputStream} for the client socket. The stream will automatically close when client socket is closed.
     */
    private OutputStream getOutputStream() {
        return mOutputStream;
    }

    /**
     * Get {@link OutputStreamWriter} for {@link #mOutputStream} for the client socket. The stream will automatically close when client socket is closed.
     */

    private OutputStreamWriter getOutputStreamWriter() {
        return new OutputStreamWriter(getOutputStream());
    }

    /**
     * Get {@link #mInputStream} for the client socket. The stream will automatically close when client socket is closed.
     */
    private InputStream getInputStream() {
        return mInputStream;
    }

    /**
     * Get {@link InputStreamReader} for {@link #mInputStream} for the client socket. The stream will automatically close when client socket is closed.
     */

    private InputStreamReader getInputStreamReader() {
        return new InputStreamReader(getInputStream());
    }

    /**
     * Wrapper class to allow pass by reference of int values.
     */
    static final class MutableInt {

        int value;

        MutableInt(int value) {
            this.value = value;
        }
    }

    /**
     * The {@link InputStream} implementation for the {@link LocalClientSocket}.
     */
    protected class SocketInputStream extends InputStream {

        private final byte[] mBytes = new byte[1];

        @Override
        public final int read() throws IOException {
            MutableInt bytesRead = new MutableInt(0);
            Error error = LocalClientSocket.this.read(mBytes, bytesRead);
            if (error != null) {
                throw new IOException("writeFail");
            }
            if (bytesRead.value == 0) {
                return -1;
            }
            return mBytes[0] & 0xFF;
        }

        @Override
        public final int read(byte[] bytes) throws IOException {
            if (bytes == null) {
                throw new NullPointerException("Read buffer can't be null");
            }
            MutableInt bytesRead = new MutableInt(0);
            Error error = LocalClientSocket.this.read(bytes, bytesRead);
            if (error != null) {
                throw new IOException("readFail");
            }
            if (bytesRead.value == 0) {
                return -1;
            }
            return bytesRead.value;
        }

        @Override
        public final int available() throws IOException {
            MutableInt available = new MutableInt(0);
            Error error = LocalClientSocket.this.available(available);
            if (error != null) {
                throw new IOException("available");
            }
            return available.value;
        }
    }

    /**
     * The {@link OutputStream} implementation for the {@link LocalClientSocket}.
     */
    protected class SocketOutputStream extends OutputStream {

        private final byte[] mBytes = new byte[1];

        @Override
        public final void write(int b) throws IOException {
            mBytes[0] = (byte) b;
            Error error = LocalClientSocket.this.send(mBytes);
            if (error != null) {
                throw new IOException("w");
            }
        }

        @Override
        public final void write(byte[] bytes) throws IOException {
            Error error = LocalClientSocket.this.send(bytes);
            if (error != null) {
                throw new IOException("w");
            }
        }
    }
}
