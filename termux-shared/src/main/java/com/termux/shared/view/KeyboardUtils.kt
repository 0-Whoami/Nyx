package com.termux.shared.view;

import android.app.Activity;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;


public class KeyboardUtils {

    public static void setSoftKeyboardVisibility(@NonNull final Runnable showSoftKeyboardRunnable, final Activity activity, final View view, final boolean visible) {
        if (visible) {
            // A Runnable with a delay is used, otherwise soft keyboard may not automatically open
            // on some devices, but still may fail
            view.postDelayed(showSoftKeyboardRunnable, 500);
        } else {
            view.removeCallbacks(showSoftKeyboardRunnable);
            hideSoftKeyboard(activity, view);
        }
    }

    /**
     * Show the soft keyboard. The {@code 0} value is passed as {@code flags} so that keyboard is
     * forcefully shown.
     * <p>
     * This is also important for soft keyboard to be shown on app startup when a hardware keyboard
     * is connected, and user has disabled the {@code Show on-screen keyboard while hardware keyboard
     * is connected} toggle in Android "Language and Input" settings but the current soft keyboard app
     * overrides the default implementation of {@link InputMethodService#onEvaluateInputViewShown()}
     * and returns {@code true}.
     <a href="  * <a href="https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:frameworks/base/core/java/android/inputmethodservice/InputMethodService.java;l=">...</a>">...</a>1751
     * <p>
     * Also check {@link InputMethodService#onShowInputRequested(int, boolean)} which must return
     * {@code true}, which can be done by failing its {@code ((flags&InputMethod.SHOW_EXPLICIT) == 0)}
     * check by passing {@code 0} as {@code flags}.<a href="
     ">* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:frameworks/base/core/java/android/inputmethodservice/InputMethodService.jav</a>a;l=2022
     */
    public static void showSoftKeyboard(final Context context, final View view) {
        if (context == null || view == null)
            return;
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.showSoftInput(view, 0);
    }

    public static void hideSoftKeyboard(final Context context, final View view) {
        if (context == null || view == null)
            return;
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void clearDisableSoftKeyboardFlags(final Activity activity) {
        if (activity != null && activity.getWindow() != null)
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    public static boolean areDisableSoftKeyboardFlagsSet(final Activity activity) {
        if (activity == null || activity.getWindow() == null)
            return true;
        return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) == 0;
    }

    public static void setSoftKeyboardAlwaysHiddenFlags(final Activity activity) {
        if (activity != null && activity.getWindow() != null)
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    public static void setSoftInputModeAdjustResize(final Activity activity) {
           if (activity != null && activity.getWindow() != null)
                activity.getWindow().setDecorFitsSystemWindows(false);

    }

}
