package com.termux.shared.jni.models;

import androidx.annotation.NonNull;


public class JniResult {

    /**
     * The return value for the JNI call.
     * This should be 0 for success.
     */
    public final int retval;

    /**
     * Optional additional int data that needs to be returned by JNI call, like bytes read on success.
     */
    public int intData;

    /**
     * Create an new instance of {@link JniResult}.
     *
     * @param retval The {@link #retval} value.
     */
    public JniResult(int retval) {
        this.retval = retval;
    }

    /**
     * Create an new instance of {@link JniResult} from a {@link Throwable} with {@link #retval} -1.
     *
     */
    public JniResult() {
        this(-1);
    }

    /**
     * Get error {@link String} for {@link JniResult}.
     *
     * @param result The {@link JniResult} to get error from.
     * @return Returns the error {@link String}.
     */
    @NonNull
    public static String getErrorString(final JniResult result) {
        if (result == null)
            return "null";
        return "";
    }

}
