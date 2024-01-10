package com.termux.shared.data


object DataUtils {
    /**
     * Max safe limit of data size to prevent TransactionTooLargeException when transferring data
     * inside or to other apps via transactions.
     */
    // 100KB
    const val TRANSACTION_SIZE_LIMIT_IN_BYTES: Int = 100 * 1024

    @JvmStatic
    fun getTruncatedCommandOutput(
        text: String?,
        maxLength: Int,
        fromEnd: Boolean,
        onNewline: Boolean,
        addPrefix: Boolean
    ): String? {
        var text = text
        var maxLength = maxLength
        if (text == null) return null
        val prefix = "(truncated) "
        if (addPrefix) maxLength -= prefix.length
        if (maxLength < 0 || text.length < maxLength) return text
        if (fromEnd) {
            text = text.substring(0, maxLength)
        } else {
            var cutOffIndex = text.length - maxLength
            if (onNewline) {
                val nextNewlineIndex = text.indexOf('\n', cutOffIndex)
                if (nextNewlineIndex != -1 && nextNewlineIndex != text.length - 1) {
                    cutOffIndex = nextNewlineIndex + 1
                }
            }
            text = text.substring(cutOffIndex)
        }
        if (addPrefix) text = prefix + text
        return text
    }


    /**
     * Add a space indent to a [String]. Each indent is 4 space characters long.
     *
     * @param string The [String] to add indent to.
     * @return Returns the indented [String].
     */
    @JvmStatic
    fun getSpaceIndentedString(string: String?): String? {
        return if (string.isNullOrEmpty()) string
        else getIndentedString(string)
    }

    /**
     * Add an indent to a [String].
     *
     * @param string The [String] to add indent to.
     * @param indent The indent characters.
     * @return Returns the indented [String].
     */
    private fun getIndentedString(string: String?): String? {
        return if (string.isNullOrEmpty()) string
        else string.replace("(?m)^".toRegex(), " ")
    }

    /**
     * Check if a string is null or empty.
     */
    @JvmStatic
    fun isNullOrEmpty(string: String?): Boolean {
        return string.isNullOrEmpty()
    }
}
