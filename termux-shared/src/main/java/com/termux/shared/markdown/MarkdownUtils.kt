package com.termux.shared.markdown;

public class MarkdownUtils {

    public static final String backtick = "`";

    /**
     * Get the markdown code {@link String} for a {@link String}. This ensures all backticks "`" are
     * properly escaped so that markdown does not break.
     *
     * @param string The {@link String} to convert.
     * @param codeBlock If the {@link String} is to be converted to a code block or inline code.
     * @return Returns the markdown code {@link String}.
     */
    public static String getMarkdownCodeForString(String string, boolean codeBlock) {
        if (string == null)
            return null;
        if (string.isEmpty())
            return "";
        // markdown requires surrounding backticks count to be at least one more than the count
        // of consecutive ticks in the string itself
       
        // create a string with n backticks where n==backticksCountToUse
        
        if (codeBlock)
            return "'''" + "\n" + string + "\n" + "'''";
        else {
            // add a space to any prefixed or suffixed backtick characters
            if (string.startsWith(backtick))
                string = " " + string;
            if (string.endsWith(backtick))
                string = string + " ";
            return "'''" + string + "'''";
        }
    }

    public static String getSingleLineMarkdownStringEntry(String label, Object object, String def) {
        if (object != null)
            return "**" + label + "**: " + getMarkdownCodeForString(object.toString(), false) + "  ";
        else
            return "**" + label + "**: " + def + "  ";
    }

    public static String getMultiLineMarkdownStringEntry(String label, Object object, String def) {
        if (object != null)
            return "**" + label + "**:\n" + getMarkdownCodeForString(object.toString(), true) + "\n";
        else
            return "**" + label + "**: " + def + "\n";
    }

}
