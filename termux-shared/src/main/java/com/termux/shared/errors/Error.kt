package com.termux.shared.errors

class Error(code: Int) {
    /**
     * The error code.
     */
    private var code = 0

    init {
        InitError(code)
    }

    private fun InitError(code: Int) {
        if (code > Errno.ERRNO_SUCCESS.code) this.code = code
        else this.code = Errno.ERRNO_SUCCESS.code
    }

    fun setLabel(): Error {
        return this
    }


    val isStateFailed: Boolean
        get() = code > Errno.ERRNO_SUCCESS.code
}
