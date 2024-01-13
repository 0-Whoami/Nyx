package com.termux.shared.jni.models


class JniResult {
    /**
     * The return value for the JNI call.
     * This should be 0 for success.
     */
    @JvmField
    val retval: Int

    /**
     * Optional additional int data that needs to be returned by JNI call, like bytes read on success.
     */
    @JvmField
    var intData: Int = 0

    /**
     * Create an new instance of [JniResult].
     *
     * @param retval The [.retval] value.
     */
    private constructor(retval: Int) {
        this.retval = retval
    }

    /**
     * Create an new instance of [JniResult] from a [Throwable] with [.retval] -1.
     */
    constructor() : this(-1)

    constructor(intData: Int, retval: Int) {
        this.intData = intData
        this.retval = retval
    }

    companion object {
        /**
         * Get error [String] for [JniResult].
         *
         * @param result The [JniResult] to get error from.
         * @return Returns the error [String].
         */
        @JvmStatic
        fun getErrorString(result: JniResult?): String {
            if (result == null) return "null"
            return ""
        }
    }
}
