package com.termux.shared.errors

import android.app.Activity

/**
 * The [Class] that defines error messages and codes.
 */
open class Errno(
    /**
     * The errno type.
     */
    private val type: String,
    /**
     * The errno code.
     */
    @JvmField val code: Int,
    /**
     * The errno message.
     */
    private val message: String
) {
    override fun toString(): String {
        return "type=$type, code=$code, message=\"$message\""
    }

    val error: Error
        get() = Error(this.code)


    companion object {
        private const val TYPE: String = "Error"

        @JvmField
        val ERRNO_SUCCESS: Errno = Errno(TYPE, Activity.RESULT_OK, "Success")
    }
}
