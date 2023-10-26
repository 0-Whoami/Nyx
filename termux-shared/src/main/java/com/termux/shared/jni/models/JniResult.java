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
     * The errno value for any failed native system or library calls if {@link #retval} does not equal 0.
     * This should be 0 if no errno was set.
     * <p>
     <a href="  * <a href="<a">href="https://manpages.debian.org/testing/manpages-dev/e</a>rrno.3.en">...</a>.">...</a>html
     */
    public int errno;

    /**
     * The error message for the failure if {@link #retval} does not equal 0.
     * The message will contain errno message returned by strerror() if errno was set.
     * <p>
     <a href="  * <a href="https://manpages.debian.org/testing/manpages-dev/strerror.3.en">...</a>.">...</a>html
     */
    public String errmsg;

    /**
     * Optional additional int data that needs to be returned by JNI call, like bytes read on success.
     */
    public int intData;

    /**
     * Create an new instance of {@link JniResult}.
     *
     * @param retval The {@link #retval} value.
     * @param errno The {@link #errno} value.
     * @param errmsg The {@link #errmsg} value.
     */
    public JniResult(int retval, int errno, String errmsg) {
        this.retval = retval;
        this.errno = errno;
        this.errmsg = errmsg;
    }

    /**
     * Create an new instance of {@link JniResult}.
     *
     * @param retval The {@link #retval} value.
     * @param errno The {@link #errno} value.
     * @param errmsg The {@link #errmsg} value.
     * @param intData The {@link #intData} value.
     */
    public JniResult(int retval, int errno, String errmsg, int intData) {
        this(retval, errno, errmsg);
        this.intData = intData;
    }

    /**
     * Create an new instance of {@link JniResult} from a {@link Throwable} with {@link #retval} -1.
     *
     */
    public JniResult() {
        this(-1, 0, "");
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
        return result.getErrorString();
    }

    /**
     * Get error {@link String} for {@link JniResult}.
     */
    @NonNull
    public String getErrorString() {
        return "";
    }
}
