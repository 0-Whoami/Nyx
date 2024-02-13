package com.termux.utils.file.filesystem

/**
 * Flags to represent regular, directory and symlink file types defined by [FileType]
 */
val FILE_TYPE_NORMAL_FLAGS: Int =
    FileType.REGULAR.value or FileType.DIRECTORY.value or FileType.SYMLINK.value

/**
 * Checks the type of file that exists at `filePath`.
 *
 *
 * Returns:
 * -  if `filePath` is `null`, empty, an exception is raised
 * or no file exists at `filePath`.
 * -  if file at `filePath` is a regular file.
 * -  if file at `filePath` is a directory file.
 * -  if file at `filePath` is a symlink file and `followLinks` is `false`.
 * -  if file at `filePath` is a character special file.
 * -  if file at `filePath` is a fifo special file.
 * -  if file at `filePath` is a block special file.
 * -  if file at `filePath` is of unknown type.
 *
 *
 * The  can also be used for checking
 * symlinks but [FileAttributes] will provide access to more attributes if necessary,
 * used to reliably check for non-existence and exclude the other 3 file types. commons.io is
 * also not compatible with android < 8 for man[things.
 * 
 * 
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/](y)java/jav[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/](a/io/File.java;l=793
      )io/UnixF[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/nati](ileSystem.java;l=248
      )ve/UnixF[* https://cs.android.com/android/_/android/platform/libcore/+/001ac51d61ad](ileSystem_md.c;l=121
      )7443ba51[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/luni/src/main/](8bf2cf7e086efe698c6d
      )java/lib[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/luni/src/main/java/](core/io/Os.java;l=51
      )libcore/[* https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/ap](io/Libcore.java;l=45
      )p/ActivityThread.java;l=7530
 *
 * @param filePath    The `path` for file to check.
 * @param followLinks The `boolean` that decides if symlinks will be followed while
 * finding type. If set to `true`, then type of symlink target will
 * be returned if file at `filePath` is a symlink. If set to
 * `false`, then type of file at `filePath` itself will be
 * returned.
 * @return Returns the [FileType] of file.
 */
fun getFileType(filePath: String?, followLinks: Boolean): FileType {
    if (filePath.isNullOrEmpty()) return FileType.NO_EXIST
    return try {
        val fileAttributes = FileAttributes.get(filePath, followLinks)
        getFileType(fileAttributes)
    } catch (e: Exception) {
        // If not a ENOENT (No such file or directory) exception
        FileType.NO_EXIST
    }
}

fun getFileType(fileAttributes: FileAttributes): FileType {
    return if (fileAttributes.isRegularFile) FileType.REGULAR
    else if (fileAttributes.isDirectory) FileType.DIRECTORY
    else if (fileAttributes.isSymbolicLink) FileType.SYMLINK
    else if (fileAttributes.isSocket) FileType.SOCKET
    else if (fileAttributes.isCharacter) FileType.CHARACTER
    else if (fileAttributes.isFifo) FileType.FIFO
    else if (fileAttributes.isBlock) FileType.BLOCK
    else FileType.UNKNOWN
}

