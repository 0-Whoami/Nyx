package com.termux.shared.activity;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.errors.Error;
import com.termux.shared.errors.FunctionErrno;

public class ActivityUtils {

    /**
     * Start an {link Activity}.
     *
     * @param context The context for operations.
     * @param intent The {@link Intent} to send to start the activity.
     * @return Returns the {@code error} if starting activity was not successful, otherwise {@code null}.
     */
    public static Error startActivity(Context context, @NonNull Intent intent) {
        Error error;
        String activityName = intent.getComponent() != null ? intent.getComponent().getClassName() : "Unknown";
        if (context == null) {
            error = ActivityErrno.ERRNO_STARTING_ACTIVITY_WITH_NULL_CONTEXT.getError(activityName);
            return error;
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            error = ActivityErrno.ERRNO_START_ACTIVITY_FAILED_WITH_EXCEPTION.getError(e, activityName, e.getMessage());
            return error;
        }
        return null;
    }

    /**
     * Wrapper for { #startActivityForResult(Context, int, Intent, boolean, boolean, ActivityResultLauncher)}.
     */
    public static Error startActivityForResult(Context context, int requestCode, @NonNull Intent intent) {
        return startActivityForResult(context, requestCode, intent, null);
    }




    /**
     * Start an {link Activity} for result.
     *
     * @param context The context for operations. It must be an instance of {Activity} or
     *               {@link AppCompatActivity}. It is ignored if {@code activityResultLauncher}
     *                is not {@code null}.
     * @param requestCode The request code to use while sending intent. This must be >= 0, otherwise
     *                    exception will be raised. This is ignored if {@code activityResultLauncher}
     *                    is {@code null}.
     * @param intent The {@link Intent} to send to start the activity.
     *  logErrorMessage If an error message should be logged if failed to start activity.
     *  showErrorMessage If an error message toast should be shown if failed to start activity
     *                         in addition to logging a message. The {@code context} must not be
     *                         {@code null}.
     * @param activityResultLauncher The {ActivityResultLauncher<Intent>} to use for start the
     *                               activity. If this is {@code null}, then
     *                               {Activity#startActivityForResult(Intent, int)} will be
     *                               used instead.
     *                               Note that later is deprecated.
     * @return Returns the {@code error} if starting activity was not successful, otherwise {@code null}.
     */
    public static Error startActivityForResult(Context context, int requestCode, @NonNull Intent intent, @Nullable ActivityResultLauncher<Intent> activityResultLauncher) {
        Error error;
        String activityName = intent.getComponent() != null ? intent.getComponent().getClassName() : "Unknown";
        try {
            if (activityResultLauncher != null) {
                activityResultLauncher.launch(intent);
            } else {
                if (context == null) {
                    error = ActivityErrno.ERRNO_STARTING_ACTIVITY_WITH_NULL_CONTEXT.getError(activityName);
                    return error;
                }
                if (context instanceof AppCompatActivity)
                    ((AppCompatActivity) context).startActivityForResult(intent, requestCode);
                else {
                    error = FunctionErrno.ERRNO_PARAMETER_NOT_INSTANCE_OF.getError("context", "startActivityForResult", "Activity or AppCompatActivity");
                    return error;
                }
            }
        } catch (Exception e) {
            error = ActivityErrno.ERRNO_START_ACTIVITY_FOR_RESULT_FAILED_WITH_EXCEPTION.getError(e, activityName, e.getMessage());
           return error;
        }
        return null;
    }


    /**
     * Generic method to start an {Activity} for result.
     *
     * @param activityResultLauncher A launcher for start the process of executing an {ActivityResultContract}.
     * @param input                  The data required to {ActivityResultLauncher#launch(Object) launch} Activity.
     * @param <T>                    Type of the input required to {ActivityResultLauncher#launch(Object) launch}.
     */
    public static <T> void startActivityForResult(@NonNull ActivityResultLauncher<T> activityResultLauncher, T input) {

        String activityName = "Unknown";
        if (input instanceof Intent && ((Intent) input).getComponent() != null) {
            activityName = ((Intent) input).getComponent().getClassName();
        }
        try {
            activityResultLauncher.launch(input);
        } catch (Exception e) {
            ActivityErrno.ERRNO_START_ACTIVITY_FOR_RESULT_FAILED_WITH_EXCEPTION.getError(e, activityName, e.getMessage());
        }
    }
}
