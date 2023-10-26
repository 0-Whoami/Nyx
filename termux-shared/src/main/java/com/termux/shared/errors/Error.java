package com.termux.shared.errors;

import com.termux.shared.markdown.MarkdownUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Error implements Serializable {

    /**
     * The error type.
     */
    private String type;

    /**
     * The error code.
     */
    private int code;

    /**
     * The error message.
     */
    private String message;

    /**
     * The error exceptions.
     */
    private List<Throwable> throwablesList = new ArrayList<>();

    public Error() {
        InitError(null, null, null, null);
    }

    public Error(String type, Integer code, String message, List<Throwable> throwablesList) {
        InitError(type, code, message, throwablesList);
    }

    public Error(String type, Integer code, String message, Throwable throwable) {
        InitError(type, code, message, Collections.singletonList(throwable));
    }

    public Error(String type, Integer code, String message) {
        InitError(type, code, message, null);
    }

    public Error(Integer code, String message, List<Throwable> throwablesList) {
        InitError(null, code, message, throwablesList);
    }

    public Error(Integer code, String message, Throwable throwable) {
        InitError(null, code, message, Collections.singletonList(throwable));
    }

    public Error(Integer code, String message) {
        InitError(null, code, message, null);
    }

    public Error(String message, Throwable throwable) {
        InitError(null, null, message, Collections.singletonList(throwable));
    }

    public Error(String message, List<Throwable> throwablesList) {
        InitError(null, null, message, throwablesList);
    }

    public Error(String message) {
        InitError(null, null, message, null);
    }

    private void InitError(String type, Integer code, String message, List<Throwable> throwablesList) {
        if (type != null && !type.isEmpty())
            this.type = type;
        else
            this.type = Errno.TYPE;
        if (code != null && code > Errno.ERRNO_SUCCESS.getCode())
            this.code = code;
        else
            this.code = Errno.ERRNO_SUCCESS.getCode();
        this.message = message;
        if (throwablesList != null)
            this.throwablesList = throwablesList;
    }

    public Error setLabel() {
        return this;
    }

    public String getType() {
        return type;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isStateFailed() {
        return code > Errno.ERRNO_SUCCESS.getCode();
    }


    /**
     * Get a minimal {@link String} for {@link Error} error parameters.
     *
     * @param error The {@link Error} to convert.
     * @return Returns the {@link String}.
     */
    public static String getMinimalErrorString(final Error error) {
        if (error == null)
            return "null";
        return error.getMinimalErrorString();
    }

    public String getMinimalErrorString() {
        return "(" + getCode() + ") " +
                getType() + ": " + getMessage();
    }

    /**
     * Get a markdown {@link String} for {@link Error}.
     *
     * @param error The {@link Error} to convert.
     * @return Returns the markdown {@link String}.
     */
    public static String getErrorMarkdownString(final Error error) {
        if (error == null)
            return "null";
        return error.getErrorMarkdownString();
    }

    public String getErrorMarkdownString() {
        StringBuilder markdownString = new StringBuilder();
        markdownString.append(MarkdownUtils.getSingleLineMarkdownStringEntry("Error Code", getCode(), "-"));
        markdownString.append("\n").append(MarkdownUtils.getMultiLineMarkdownStringEntry((Errno.TYPE.equals(getType()) ? "Error Message" : "Error Message (" + getType() + ")"), message, "-"));
        if (throwablesList != null && !throwablesList.isEmpty())
            markdownString.append("\n\n");
        return markdownString.toString();
    }


}
