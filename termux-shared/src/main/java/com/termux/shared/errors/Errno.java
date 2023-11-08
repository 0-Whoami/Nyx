package com.termux.shared.errors;

import android.app.Activity;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * The {@link Class} that defines error messages and codes.
 */
public class Errno {

    private static final HashMap<String, Errno> map = new HashMap<>();

    public static final String TYPE = "Error";

    public static final Errno ERRNO_SUCCESS = new Errno(TYPE, Activity.RESULT_OK, "Success");

    /**
     * The errno type.
     */
    protected final String type;

    /**
     * The errno code.
     */
    protected final int code;

    /**
     * The errno message.
     */
    protected final String message;


    public Errno(@NonNull final String type, final int code, @NonNull final String message) {
        this.type = type;
        this.code = code;
        this.message = message;
        map.put(type + ":" + code, this);
    }

    @NonNull
    @Override
    public String toString() {
        return "type=" + type + ", code=" + code + ", message=\"" + message + "\"";
    }

    public int getCode() {
        return code;
    }

    public Error getError() {
        return new Error(type, getCode(), message);
    }

    public Error getError(Object... args) {
        try {
            return new Error(type, getCode(), String.format(message, args));
        } catch (Exception e) {
            // Return unformatted message as a backup
            return new Error(type, getCode(), message + ": " + Arrays.toString(args));
        }
    }

    public Error getError(Throwable throwable, Object... args) {
        if (throwable == null)
            return getError(args);
        else
            return getError(Collections.singletonList(throwable), args);
    }

    public Error getError(List<Throwable> throwablesList, Object... args) {
        try {
            if (throwablesList == null)
                return new Error(type, getCode(), String.format(message, args));
            else
                return new Error(type, getCode(), String.format(message, args), throwablesList);
        } catch (Exception e) {
             // Return unformatted message as a backup
            return new Error(type, getCode(), message + ": " + Arrays.toString(args), throwablesList);
        }
    }

}
