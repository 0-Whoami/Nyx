package com.termux.shared.data;


public class DataUtils {

    /**
     * Max safe limit of data size to prevent TransactionTooLargeException when transferring data
     * inside or to other apps via transactions.
     */
    // 100KB
    public static final int TRANSACTION_SIZE_LIMIT_IN_BYTES = 100 * 1024;

    public static String getTruncatedCommandOutput(String text, int maxLength, boolean fromEnd, boolean onNewline, boolean addPrefix) {
        if (text == null)
            return null;
        String prefix = "(truncated) ";
        if (addPrefix)
            maxLength = maxLength - prefix.length();
        if (maxLength < 0 || text.length() < maxLength)
            return text;
        if (fromEnd) {
            text = text.substring(0, maxLength);
        } else {
            int cutOffIndex = text.length() - maxLength;
            if (onNewline) {
                int nextNewlineIndex = text.indexOf('\n', cutOffIndex);
                if (nextNewlineIndex != -1 && nextNewlineIndex != text.length() - 1) {
                    cutOffIndex = nextNewlineIndex + 1;
                }
            }
            text = text.substring(cutOffIndex);
        }
        if (addPrefix)
            text = prefix + text;
        return text;
    }


    /**
     * Add a space indent to a {@link String}. Each indent is 4 space characters long.
     *
     * @param string The {@link String} to add indent to.
     * @return Returns the indented {@link String}.
     */
    public static String getSpaceIndentedString(String string) {
        if (string == null || string.isEmpty())
            return string;
        else
            return getIndentedString(string, "    ");
    }

    /**
     * Add an indent to a {@link String}.
     *
     * @param string The {@link String} to add indent to.
     * @param indent The indent characters.
     * @return Returns the indented {@link String}.
     */
    public static String getIndentedString(String string, String indent) {
        if (string == null || string.isEmpty())
            return string;
        else
            return string.replaceAll("(?m)^", indent);
    }

    /**
     * Check if a string is null or empty.
     */
    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

}
