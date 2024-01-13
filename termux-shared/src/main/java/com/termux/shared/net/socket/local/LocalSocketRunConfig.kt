package com.termux.shared.net.socket.local;


import com.termux.shared.file.FileUtils;

import java.nio.charset.StandardCharsets;

/**
 * Run config for {@link LocalSocketManager}.
 */
public class LocalSocketRunConfig {

    /**
     * The {@link LocalSocketManager} title.
     */
    private final String mTitle;

    /**
     * The {@link LocalServerSocket} path.
     * <p>
     * For a filesystem socket, this must be an absolute path to the socket file. Creation of a new
     * socket will fail if the server starter app process does not have write and search (execute)
     * permission on the directory in which the socket is created. The client process must have write
     * permission on the socket to connect to it. Other app will not be able to connect to socket
     * if its created in private app data directory.
     * <p>
     * For an abstract namespace socket, the first byte must be a null `\0` character. Note that on
     * Android 9+, if server app is using `targetSdkVersion` `28`, then other apps will not be able
     * to connect to it due to selinux restrictions.
     * > Per-app SELinux domains
     * > Apps that target Android 9 or higher cannot share data with other apps using world-accessible
     * Unix permissions. This change improves the integrity of the Android Application Sandbox,
     * particularly the requirement that an app's private data is accessible only by that app.<a href="
     * ">* https://developer.android.com/about/versions/pie/android-9.0-ch</a>anges-28<a href="
     * ">* https://github.com/android/ndk/iss</a>ues/1469<a href="
     * ">* https://stackoverflow.com/questions/63806516/avc-denied-connectto-when-using-uds-on-an</a>droid-10
     * <p>
     * Max allowed length is 108 bytes as per sun_path size (UNIX_PATH_MAX) on Linux.
     */
    private final String mPath;

    /**
     * If abstract namespace {@link LocalServerSocket} instead of filesystem.
     */
    private final boolean mAbstractNamespaceSocket;

    /**
     * The {@link LocalSocketManager} client for the {@link LocalSocketManager}.
     */
    private final LocalSocketManager mLocalSocketManagerClient;

    /**
     * The {@link LocalServerSocket} file descriptor.
     * Value will be `>= 0` if socket has been created successfully and `-1` if not created or closed.
     */
    private int mFD = -1;


    /**
     * Create an new instance of {@link LocalSocketRunConfig}.
     *
     * @param title                    The {@link #mTitle} value.
     * @param path                     The {@link #mPath} value.
     * @param localSocketManagerClient The {@link #mLocalSocketManagerClient} value.
     */
    public LocalSocketRunConfig(String title, String path, LocalSocketManager localSocketManagerClient) {
        mTitle = title;
        mLocalSocketManagerClient = localSocketManagerClient;
        mAbstractNamespaceSocket = path.getBytes(StandardCharsets.UTF_8)[0] == 0;
        if (mAbstractNamespaceSocket)
            mPath = path;
        else
            mPath = FileUtils.getCanonicalPath(path, null);
    }

    /**
     * Get {@link #mTitle}.
     */
    public final String getTitle() {
        return mTitle;
    }


    /**
     * Get {@link #mPath}.
     */
    public final String getPath() {
        return mPath;
    }

    /**
     * Get {@link #mAbstractNamespaceSocket}.
     */
    public final boolean isAbstractNamespaceSocket() {
        return !mAbstractNamespaceSocket;
    }

    /**
     * Get {@link #mLocalSocketManagerClient}.
     */
    public final LocalSocketManager getLocalSocketManagerClient() {
        return mLocalSocketManagerClient;
    }

    /**
     * Get {@link #mFD}.
     */
    public final Integer getFD() {
        return mFD;
    }

    /**
     * Set {@link #mFD}. Value must be greater than 0 or -1.
     */
    public final void setFD(int fd) {
        if (fd >= 0)
            mFD = fd;
        else
            mFD = -1;
    }


}
