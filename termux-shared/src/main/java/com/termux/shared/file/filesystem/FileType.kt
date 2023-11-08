package com.termux.shared.file.filesystem

/**
 * The [Enum] that defines file types.
 */
sealed class FileType(val name: String, @JvmField val value: Int) {
    // 00000000
    data object NO_EXIST:FileType("no exist", 0)

    // 00000001
    data object REGULAR:FileType("regular", 1)

    // 00000010
    data object DIRECTORY:FileType("directory", 2)

    // 00000100
    data object SYMLINK:FileType("symlink", 4)

    // 00001000
    data object SOCKET:FileType("socket", 8)

    // 00010000
    data object CHARACTER:FileType("character", 16)

    // 00100000
    data object FIFO:FileType("fifo", 32)

    // 01000000
    data object BLOCK:FileType("block", 64)

    // 10000000
    data object UNKNOWN:FileType("unknown", 128)

}
