package com.termux.shared.errors;

import android.app.Activity;

/**
 * The {@link Class} that defines error messages and codes.
 */
public class Errno {

    public static final String TYPE = "Error";

    public static final Errno ERRNO_SUCCESS = new Errno(TYPE, Activity.RESULT_OK, "Success");

    /**
     * The errno type.
     */
    private final String type;

    /**
     * The errno code.
     */
    private final int code;

    /**
     * The errno message.
     */
    private final String message;


    public Errno(final String type, final int code, final String message) {
        this.type = type;
        this.code = code;
        this.message = message;
    }


    @Override
    public final String toString() {
        return "type=" + type + ", code=" + code + ", message=\"" + message + "\"";
    }

    public final int getCode() {
        return code;
    }

    public final Error getError() {
        return new Error(getCode());
    }


}
