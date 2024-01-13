package com.termux.app.terminal;

import android.content.Context;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.termux.R;
import com.termux.app.Navigation;
import com.termux.app.TermuxActivity;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalViewClient;

public class TermuxTerminalViewClient implements TerminalViewClient {

    private final TermuxActivity mActivity;

    private final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    private int MIN_FONTSIZE;
    private int MAX_FONTSIZE;
    private int CURRENT_FONTSIZE;
    private Runnable mShowSoftKeyboardRunnable;
    private boolean mShowSoftKeyboardIgnoreOnce;
    private int DEFAULT_FONTSIZE;

    public TermuxTerminalViewClient(TermuxActivity activity, TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mActivity = activity;
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public final void onCreate() {
        setDefaultFontSizes(mActivity);
        mActivity.getTerminalView().setTextSize(DEFAULT_FONTSIZE);
        mActivity.getTerminalView().setKeepScreenOn(true);
        CURRENT_FONTSIZE = DEFAULT_FONTSIZE;
    }


    /**
     * Should be called when mActivity.onResume() is called
     */
    public final void onResume() {
        // Show the soft keyboard if required
        setSoftKeyboardState();
        // Start terminal cursor blinking if enabled
        // If emulator is already set, then start blinker now, otherwise wait for onEmulatorSet()
        // event to start it. This is needed since onEmulatorSet() may not be called after
        // TermuxActivity is started after device display timeout with double tap and not power button.
        //setTerminalCursorBlinkerState(true);

    }


    @Override
    public final float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            boolean increase = scale > 1.f;
            changeFontSize(increase);
            return 1.0f;
        }
        return scale;
    }

    @Override
    public void onSwipe() {
        mActivity.getSupportFragmentManager().beginTransaction().add(R.id.compose_fragment_container, Navigation.class, null, "nav").commit();
    }

    @Override
    public final void onSingleTapUp(MotionEvent e) {
        TerminalEmulator term = mActivity.getCurrentSession().getEmulator();
        if (!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
                KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
        }
    }


    @Override
    public final boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession currentSession) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning()) {
            mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession);
            return true;
        }
        return false;
    }

    @Override
    public final boolean onKeyUp(int keyCode, KeyEvent e) {
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (keyCode == KeyEvent.KEYCODE_BACK && mActivity.getTerminalView().mEmulator == null) {
            mActivity.finishActivityIfNotFinishing();
            return true;
        }
        return false;
    }


    @Override
    public final boolean onCodePoint(final int codePoint, boolean ctrlDown, TerminalSession session) {
        if (ctrlDown) {
            if (codePoint == 106 && /* Ctrl+j or \n */
                !session.isRunning()) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
                return true;
            }
        }
        return false;
    }

    public final void changeFontSize(boolean increase) {
        CURRENT_FONTSIZE += (increase ? 1 : -1) << 1;
        CURRENT_FONTSIZE = Math.max(MIN_FONTSIZE, Math.min(CURRENT_FONTSIZE, MAX_FONTSIZE));
        mActivity.getTerminalView().setTextSize(CURRENT_FONTSIZE);
    }

    private void setDefaultFontSizes(Context context) {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());
        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        // min
        MIN_FONTSIZE = (int) (dipInPixels);
        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        int defaultFontSize = Math.round(7 * dipInPixels);
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (defaultFontSize % 2 == 1)
            defaultFontSize--;
        // default
        DEFAULT_FONTSIZE = defaultFontSize;
        // max
        MAX_FONTSIZE = 256;

    }

    private void setSoftKeyboardState() {
        // Requesting terminal view focus is necessary regardless of if soft keyboard is to be
        // disabled or hidden at startup, otherwise if hardware keyboard is attached and user
        // starts typing on hardware keyboard without tapping on the terminal first, then a colour
        // tint will be added to the terminal as highlight for the focussed view. Test with a light
        // theme. For android 8.+, the "defaultFocusHighlightEnabled" attribute is also set to false
        // in TerminalView layout to fix the issue.
        // If soft keyboard is disabled by user for Termux (check function docs for Termux behaviour info)

        // Set flag to automatically push up TerminalView when keyboard is opened instead of showing over it
        KeyboardUtils.setSoftInputModeAdjustResize(mActivity);
        // Clear any previous flags to disable soft keyboard in case setting updated
        KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
        // If soft keyboard is to be hidden on startup
        // Required to keep keyboard hidden when Termux app is switched back from another app
        KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(mActivity);
        KeyboardUtils.hideSoftKeyboard(mActivity, mActivity.getTerminalView());
        mActivity.getTerminalView().requestFocus();
        // Required to keep keyboard hidden on app startup
        mShowSoftKeyboardIgnoreOnce = true;

        mActivity.getTerminalView().setOnFocusChangeListener((view, hasFocus) -> {
            // Force show soft keyboard if TerminalView or toolbar text input view has
            // focus and close it if they don't


            if (hasFocus) {
                if (mShowSoftKeyboardIgnoreOnce) {
                    mShowSoftKeyboardIgnoreOnce = false;
                    return;
                }

            }
            KeyboardUtils.setSoftKeyboardVisibility(getShowSoftKeyboardRunnable(), mActivity, mActivity.getTerminalView(), hasFocus);
        });
        // Do not force show soft keyboard if termux-reload-settings command was run with hardware keyboard
        // or soft keyboard is to be hidden or is disabled
    }

    private Runnable getShowSoftKeyboardRunnable() {
        if (mShowSoftKeyboardRunnable == null) {
            mShowSoftKeyboardRunnable = () -> KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
        }
        return mShowSoftKeyboardRunnable;
    }

}
