package com.termux.shared.data;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;

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
     * Get the {@code float} from a {@link String}.
     *
     * @param value The {@link String} value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the {@code float} value after parsing the {@link String} value, otherwise
     * returns default if failed to read a valid value, like in case of an exception.
     */
    public static float getFloatFromString(String value, float def) {
        if (value == null)
            return def;
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Get the {@code int} from a {@link String}.
     *
     * @param value The {@link String} value.
     * @param def The default value if failed to read a valid value.
     * @return Returns the {@code int} value after parsing the {@link String} value, otherwise
     * returns default if failed to read a valid value, like in case of an exception.
     */
    public static int getIntFromString(String value, int def) {
        if (value == null)
            return def;
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return def;
        }
    }


    /**
     * If value is not in the range [min, max], set it to either min or max.
     */
    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * Add a space indent to a {@link String}. Each indent is 4 space characters long.
     *
     * @param string The {@link String} to add indent to.
     * @param count The indent count.
     * @return Returns the indented {@link String}.
     */
    public static String getSpaceIndentedString(String string, int count) {
        if (string == null || string.isEmpty())
            return string;
        else
            return getIndentedString(string, "    ", count);
    }

    /**
     * Add an indent to a {@link String}.
     *
     * @param string The {@link String} to add indent to.
     * @param indent The indent characters.
     * @param count The indent count.
     * @return Returns the indented {@link String}.
     */
    public static String getIndentedString(String string, @NonNull String indent, int count) {
        if (string == null || string.isEmpty())
            return string;
        else
            return string.replaceAll("(?m)^", Strings.repeat(indent, Math.max(count, 1)));
    }

    /**
     * Check if a string is null or empty.
     */
    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

}
