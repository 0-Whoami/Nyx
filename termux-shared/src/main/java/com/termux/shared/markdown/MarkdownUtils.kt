package com.termux.shared.markdown

object MarkdownUtils {
    private const val backtick: String = "`"

    /**
     * Get the markdown code [String] for a [String]. This ensures all backticks "`" are
     * properly escaped so that markdown does not break.
     *
     * @param string The [String] to convert.
     * @param codeBlock If the [String] is to be converted to a code block or inline code.
     * @return Returns the markdown code [String].
     */
    private fun getMarkdownCodeForString(string: String?, codeBlock: Boolean): String? {
        var string = string ?: return null
        if (string.isEmpty()) return ""

        // markdown requires surrounding backticks count to be at least one more than the count
        // of consecutive ticks in the string itself

        // create a string with n backticks where n==backticksCountToUse
        if (codeBlock) return "'''\n$string\n'''"
        else {
            // add a space to any prefixed or suffixed backtick characters
            if (string.startsWith(backtick)) string = " $string"
            if (string.endsWith(backtick)) string = "$string "
            return "'''$string'''"
        }
    }

    @JvmStatic
    fun getSingleLineMarkdownStringEntry(label: String, `object`: Any?, def: String): String {
        return if (`object` != null) "**$label**: " + getMarkdownCodeForString(
            `object`.toString(),
            false
        ) + "  "
        else "**$label**: $def  "
    }

    @JvmStatic
    fun getMultiLineMarkdownStringEntry(label: String, `object`: Any?, def: String): String {
        return if (`object` != null) "**$label**:\n" + getMarkdownCodeForString(
            `object`.toString(),
            true
        ) + "\n"
        else "**$label**: $def\n"
    }
}
