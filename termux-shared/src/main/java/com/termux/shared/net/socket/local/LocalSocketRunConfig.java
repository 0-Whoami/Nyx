package com.termux.shared.net.socket.local;

import androidx.annotation.NonNull;
import com.termux.shared.file.FileUtils;
import com.termux.shared.markdown.MarkdownUtils;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Run config for {@link LocalSocketManager}.
 */
public class LocalSocketRunConfig implements Serializable {

    /**
     * The {@link LocalSocketManager} title.
     */
    protected final String mTitle;

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
     ">* https://developer.android.com/about/versions/pie/android-9.0-ch</a>anges-28<a href="
     ">* https://github.com/android/ndk/iss</a>ues/1469<a href="
     ">* https://stackoverflow.com/questions/63806516/avc-denied-connectto-when-using-uds-on-an</a>droid-10
     * <p>
     * Max allowed length is 108 bytes as per sun_path size (UNIX_PATH_MAX) on Linux.
     */
    protected final String mPath;

    /**
     * If abstract namespace {@link LocalServerSocket} instead of filesystem.
     */
    protected final boolean mAbstractNamespaceSocket;

    /**
     * The {@link ILocalSocketManager} client for the {@link LocalSocketManager}.
     */
    protected final ILocalSocketManager mLocalSocketManagerClient;

    /**
     * The {@link LocalServerSocket} file descriptor.
     * Value will be `>= 0` if socket has been created successfully and `-1` if not created or closed.
     */
    protected int mFD = -1;

    /**
     * The {@link LocalClientSocket} receiving (SO_RCVTIMEO) timeout in milliseconds.
     * <p>
     <a href="  * <a href="https://manpages.debian.org/testing/manpages/socket.7.en">...</a>.">...</a>html
     <a href="  * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;">...</a>l=55
     * Defaults to {@link #DEFAULT_RECEIVE_TIMEOUT}.
     */
    protected Integer mReceiveTimeout;

    public static final int DEFAULT_RECEIVE_TIMEOUT = 10000;

    /**
     * The {@link LocalClientSocket} sending (SO_SNDTIMEO) timeout in milliseconds.
     * <p>
     <a href="  * <a href="https://manpages.debian.org/testing/manpages/socket.7.en">...</a>.">...</a>html
     <a href="  * https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/NativeCrashListener.java;">...</a>l=55
     * Defaults to {@link #DEFAULT_SEND_TIMEOUT}.
     */
    protected Integer mSendTimeout;

    public static final int DEFAULT_SEND_TIMEOUT = 10000;

    /**
     * The {@link LocalClientSocket} deadline in milliseconds. When the deadline has elapsed after
     * creation time of client socket, all reads and writes will error out. Set to 0, for no
     * deadline.
     * Defaults to {@link #DEFAULT_DEADLINE}.
     */
    protected Long mDeadline;

    public static final int DEFAULT_DEADLINE = 0;

    /**
     * The {@link LocalServerSocket} backlog for the maximum length to which the queue of pending connections
     * for the socket may grow. This value may be ignored or may not have one-to-one mapping
     * in kernel implementation. Value must be greater than 0.
     * <p>
     <a href="  * <a href="https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/net/LocalSocketManager.java">...</a>;">...</a>l=31
     * Defaults to {@link #DEFAULT_BACKLOG}.
     */
    protected Integer mBacklog;

    public static final int DEFAULT_BACKLOG = 50;

    /**
     * Create an new instance of {@link LocalSocketRunConfig}.
     *
     * @param title The {@link #mTitle} value.
     * @param path The {@link #mPath} value.
     * @param localSocketManagerClient The {@link #mLocalSocketManagerClient} value.
     */
    public LocalSocketRunConfig(@NonNull String title, @NonNull String path, @NonNull ILocalSocketManager localSocketManagerClient) {
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
    public String getTitle() {
        return mTitle;
    }


    /**
     * Get {@link #mPath}.
     */
    public String getPath() {
        return mPath;
    }

    /**
     * Get {@link #mAbstractNamespaceSocket}.
     */
    public boolean isAbstractNamespaceSocket() {
        return !mAbstractNamespaceSocket;
    }

    /**
     * Get {@link #mLocalSocketManagerClient}.
     */
    public ILocalSocketManager getLocalSocketManagerClient() {
        return mLocalSocketManagerClient;
    }

    /**
     * Get {@link #mFD}.
     */
    public Integer getFD() {
        return Integer.valueOf(mFD);
    }

    /**
     * Set {@link #mFD}. Value must be greater than 0 or -1.
     */
    public void setFD(int fd) {
        if (fd >= 0)
            mFD = fd;
        else
            mFD = -1;
    }

    /**
     * Get {@link #mReceiveTimeout} if set, otherwise {@link #DEFAULT_RECEIVE_TIMEOUT}.
     */
    public Integer getReceiveTimeout() {
        return Integer.valueOf(mReceiveTimeout != null ? mReceiveTimeout : DEFAULT_RECEIVE_TIMEOUT);
    }

    /**
     * Get {@link #mSendTimeout} if set, otherwise {@link #DEFAULT_SEND_TIMEOUT}.
     */
    public Integer getSendTimeout() {
        return Integer.valueOf(mSendTimeout != null ? mSendTimeout : DEFAULT_SEND_TIMEOUT);
    }

    /**
     * Get {@link #mDeadline} if set, otherwise {@link #DEFAULT_DEADLINE}.
     */
    public Long getDeadline() {
        return Long.valueOf(mDeadline != null ? mDeadline : DEFAULT_DEADLINE);
    }

    /**
     * Get {@link #mBacklog} if set, otherwise {@link #DEFAULT_BACKLOG}.
     */
    public Integer getBacklog() {
        return Integer.valueOf(mBacklog != null ? mBacklog : DEFAULT_BACKLOG);
    }

    /**
     * Get a markdown {@link String} for the {@link LocalSocketRunConfig}.
     */
    @NonNull
    public String getMarkdownString() {
        return "## " + mTitle + " Socket Server Run Config" +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("Path", mPath, "-") +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("AbstractNamespaceSocket", Boolean.valueOf(mAbstractNamespaceSocket), "-") +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("LocalSocketManagerClient", mLocalSocketManagerClient.getClass().getName(), "-") +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("FD", Integer.valueOf(mFD), "-") +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("ReceiveTimeout", getReceiveTimeout(), "-") +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("SendTimeout", getSendTimeout(), "-") +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("Deadline", getDeadline(), "-") +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("Backlog", getBacklog(), "-");
    }

}
