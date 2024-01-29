package com.termux.shared.file.filesystem

/**
 * The [Enum] that defines file types.
 */
sealed class FileType(val value: Int) {
    // 00000000
    data object NO_EXIST : FileType(0)

    // 00000001
    data object REGULAR : FileType(1)

    // 00000010
    data object DIRECTORY : FileType(2)

    // 00000100
    data object SYMLINK : FileType(4)

    // 00001000
    data object SOCKET : FileType(8)

    // 00010000
    data object CHARACTER : FileType(16)

    // 00100000
    data object FIFO : FileType(32)

    // 01000000
    data object BLOCK : FileType(64)

    // 10000000
    data object UNKNOWN : FileType(128)

}
