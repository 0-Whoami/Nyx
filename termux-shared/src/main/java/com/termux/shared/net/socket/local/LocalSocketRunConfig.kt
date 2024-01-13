package com.termux.shared.net.socket.local

import com.termux.shared.file.FileUtils.getCanonicalPath
import java.nio.charset.StandardCharsets


/**
 * Run config for [LocalSocketManager].
 */
class LocalSocketRunConfig(
    /**
     * The [LocalSocketManager] title.
     */
    val title: String, path: String,
    /**
     * The [LocalSocketManager] client for the [LocalSocketManager].
     */
    val localSocketManagerClient: LocalSocketManager
) {
    /**
     * Get [.mTitle].
     */

    /**
     * Get [.mPath].
     */
    /**
     * The [LocalServerSocket] path.
     *
     *
     * For a filesystem socket, this must be an absolute path to the socket file. Creation of a new
     * socket will fail if the server starter app process does not have write and search (execute)
     * permission on the directory in which the socket is created. The client process must have write
     * permission on the socket to connect to it. Other app will not be able to connect to socket
     * if its created in private app data directory.
     *
     *
     * For an abstract namespace socket, the first byte must be a null `\0` character. Note that on
     * Android 9+, if server app is using `targetSdkVersion` `28`, then other apps will not be able
     * to connect to it due to selinux restrictions.
     * > Per-app SELinux domains
     * > Apps that target Android 9 or higher cannot share data with other apps using world-accessible
     * Unix permissions. This change improves the integrity of the Android Application Sandbox,
     * particularly the requirement that an app's private data is accessible only by that app.[* https://developer.android.com/about/versions/pie/android-9.0-ch](
      )anges-28[* https://github.com/android/ndk/iss](
      )ues/1469[* https://stackoverflow.com/questions/63806516/avc-denied-connectto-when-using-uds-on-an](
      )droid-10
     *
     *
     * Max allowed length is 108 bytes as per sun_path size (UNIX_PATH_MAX) on Linux.
     */
    var path: String? = null

    /**
     * If abstract namespace [LocalServerSocket] instead of filesystem.
     */
    private val mAbstractNamespaceSocket =
        path.toByteArray(StandardCharsets.UTF_8)[0].toInt() == 0

    /**
     * Get [.mLocalSocketManagerClient].
     */

    /**
     * The [LocalServerSocket] file descriptor.
     * Value will be `>= 0` if socket has been created successfully and `-1` if not created or closed.
     */
    private var mFD = -1


    /**
     * Create an new instance of [LocalSocketRunConfig].
     *
     * @param title                    The [.mTitle] value.
     * @param path                     The [.mPath] value.
     * @param localSocketManagerClient The [.mLocalSocketManagerClient] value.
     */
    init {
        if (mAbstractNamespaceSocket) this.path = path
        else this.path = getCanonicalPath(path, null)
    }


    val isAbstractNamespaceSocket: Boolean
        /**
         * Get [.mAbstractNamespaceSocket].
         */
        get() = !mAbstractNamespaceSocket

    var fD: Int
        /**
         * Get [.mFD].
         */
        get() = mFD
        /**
         * Set [.mFD]. Value must be greater than 0 or -1.
         */
        set(fd) {
            mFD = if (fd >= 0) fd
            else -1
        }
}
