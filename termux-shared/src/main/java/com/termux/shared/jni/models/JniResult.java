package com.termux.shared.jni.models;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

/**
 * A class that can be used to return result for JNI calls with support for multiple fields to easily
 * return success and error states.
 * <p><a href="
 ">* https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.</a>html<a href="
 ">* https://developer.android.com/training/articles/perf</a>-jni
 */
@Keep
public class JniResult {

    /**
     * The return value for the JNI call.
     * This should be 0 for success.
     */
    public int retval;

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
