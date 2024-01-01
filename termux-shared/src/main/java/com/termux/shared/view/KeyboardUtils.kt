package com.termux.shared.view

import android.app.Activity
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager

object KeyboardUtils {
    @JvmStatic
    fun setSoftKeyboardVisibility(
        showSoftKeyboardRunnable: Runnable,
        activity: Activity?,
        view: View,
        visible: Boolean
    ) {
        if (visible) {
            // A Runnable with a delay is used, otherwise soft keyboard may not automatically open
            // on some devices, but still may fail
            view.postDelayed(showSoftKeyboardRunnable, 500)
        } else {
            view.removeCallbacks(showSoftKeyboardRunnable)
            hideSoftKeyboard(activity, view)
        }
    }

    /**
     * Show the soft keyboard. The `0` value is passed as `flags` so that keyboard is
     * forcefully shown.
     *
     *
     * This is also important for soft keyboard to be shown on app startup when a hardware keyboard
     * is connected, and user has disabled the `Show on-screen keyboard while hardware keyboard
     * is connected` toggle in Android "Language and Input" settings but the current soft keyboard app
     * overrides the default implementation of [InputMethodService.onEvaluateInputViewShown]
     * and returns `true`.
     * [](  * <a href=)//cs.android.com/android/platform/superproject/+/android-11.0.0_r3:frameworks/base/core/java/android/inputmethodservice/InputMethodService.java;l=">...">...1751
     *
     *
     * Also check [InputMethodService.onShowInputRequested] which must return
     * `true`, which can be done by failing its `((flags&InputMethod.SHOW_EXPLICIT) == 0)`
     * check by passing `0` as `flags`.[* https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:frameworks/base/core/java/android/inputmethodservice/InputMethodService.jav](
     )a;l=2022
     */
    @JvmStatic
    fun showSoftKeyboard(context: Context?, view: View?) {
        if (context == null || view == null) return
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(view, 0)
    }

    @JvmStatic
    fun hideSoftKeyboard(context: Context?, view: View?) {
        if (context == null || view == null) return
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    @JvmStatic
    fun clearDisableSoftKeyboardFlags(activity: Activity?) {
        if (activity != null && activity.window != null) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    @JvmStatic
    fun areDisableSoftKeyboardFlagsSet(activity: Activity?): Boolean {
        if (activity == null || activity.window == null) return true
        return (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) == 0
    }

    @JvmStatic
    fun setSoftKeyboardAlwaysHiddenFlags(activity: Activity?) {
        if (activity != null && activity.window != null) activity.window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }

    @JvmStatic
    fun setSoftInputModeAdjustResize(activity: Activity?) {
        if (activity != null && activity.window != null) activity.window.setDecorFitsSystemWindows(
            false
        )
    }
}
