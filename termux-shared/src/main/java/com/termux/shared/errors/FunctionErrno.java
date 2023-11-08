package com.termux.shared.errors;

/**
 * The {@link Class} that defines function error messages and codes.
 */
public class FunctionErrno extends Errno {

    public static final String TYPE = "Function Error";

    /* Errors for null or empty parameters (100-150) */
    public static final Errno ERRNO_NULL_OR_EMPTY_PARAMETER = new Errno(TYPE, 100, "The %1$s parameter passed to \"%2$s\" is null or empty.");

    FunctionErrno(final String type, final int code, final String message) {
        super(type, code, message);
    }
}
