package com.termux.shared.errors;

public class Error {

    /**
     * The error code.
     */
    private int code;

    public Error(Integer code) {
        InitError(code);
    }

    private void InitError(Integer code) {
        if (code != null && code > Errno.ERRNO_SUCCESS.getCode())
            this.code = code;
        else
            this.code = Errno.ERRNO_SUCCESS.getCode();
    }

    public final Error setLabel() {
        return this;
    }


    public final boolean isStateFailed() {
        return code > Errno.ERRNO_SUCCESS.getCode();
    }

}
