package com.termux.shared.errors

/**
 * The [Class] that defines function error messages and codes.
 */
class FunctionErrno internal constructor(type: String?, code: Int, message: String?) : Errno(
    type!!, code, message!!
) {
    companion object {
        private const val TYPE = "Function Error"

        /* Errors for null or empty parameters (100-150) */
        val ERRNO_NULL_OR_EMPTY_PARAMETER: Errno =
            Errno(TYPE, 100, "The %1\$s parameter passed to \"%2\$s\" is null or empty.")
    }
}
