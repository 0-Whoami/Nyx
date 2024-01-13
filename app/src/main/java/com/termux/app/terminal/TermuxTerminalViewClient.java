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

    public TermuxTerminalViewClient(final TermuxActivity activity, final TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        mActivity = activity;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public final void onCreate() {
        this.setDefaultFontSizes(this.mActivity);
        this.mActivity.getTerminalView().setTextSize(this.DEFAULT_FONTSIZE);
        this.mActivity.getTerminalView().setKeepScreenOn(true);
        this.CURRENT_FONTSIZE = this.DEFAULT_FONTSIZE;
    }


    /**
     * Should be called when mActivity.onResume() is called
     */
    public final void onResume() {
        // Show the soft keyboard if required
        this.setSoftKeyboardState();
        // Start terminal cursor blinking if enabled
        // If emulator is already set, then start blinker now, otherwise wait for onEmulatorSet()
        // event to start it. This is needed since onEmulatorSet() may not be called after
        // TermuxActivity is started after device display timeout with double tap and not power button.
        //setTerminalCursorBlinkerState(true);

    }


    @Override
    public final float onScale(final float scale) {
        if (0.9f > scale || 1.1f < scale) {
            final boolean increase = 1.0f < scale;
            this.changeFontSize(increase);
            return 1.0f;
        }
        return scale;
    }

    @Override
    public final void onSwipe() {
        this.mActivity.getSupportFragmentManager().beginTransaction().add(R.id.compose_fragment_container, Navigation.class, null, "nav").commit();
    }

    @Override
    public final void onSingleTapUp(final MotionEvent e) {
        final TerminalEmulator term = this.mActivity.getCurrentSession().getEmulator();
        if (!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (KeyboardUtils.areDisableSoftKeyboardFlagsSet(this.mActivity))
                KeyboardUtils.showSoftKeyboard(this.mActivity, this.mActivity.getTerminalView());
        }
    }


    @Override
    public final boolean onKeyDown(final int keyCode, final KeyEvent e, final TerminalSession currentSession) {
        if (KeyEvent.KEYCODE_ENTER == keyCode && !currentSession.isRunning()) {
            this.mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession);
            return true;
        }
        return false;
    }

    @Override
    public final boolean onKeyUp(final int keyCode, final KeyEvent e) {
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (KeyEvent.KEYCODE_BACK == keyCode && null == mActivity.getTerminalView().mEmulator) {
            this.mActivity.finishActivityIfNotFinishing();
            return true;
        }
        return false;
    }


    @Override
    public final boolean onCodePoint(int codePoint, final boolean ctrlDown, final TerminalSession session) {
        if (ctrlDown) {
            if (106 == codePoint && /* Ctrl+j or \n */
                !session.isRunning()) {
                this.mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
                return true;
            }
        }
        return false;
    }

    public final void changeFontSize(final boolean increase) {
        this.CURRENT_FONTSIZE += (increase ? 1 : -1) << 1;
        this.CURRENT_FONTSIZE = Math.max(this.MIN_FONTSIZE, Math.min(this.CURRENT_FONTSIZE, this.MAX_FONTSIZE));
        this.mActivity.getTerminalView().setTextSize(this.CURRENT_FONTSIZE);
    }

    private void setDefaultFontSizes(final Context context) {
        final float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());
        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        // min
        this.MIN_FONTSIZE = (int) (dipInPixels);
        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        int defaultFontSize = Math.round(7 * dipInPixels);
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (1 == defaultFontSize % 2)
            defaultFontSize--;
        // default
        this.DEFAULT_FONTSIZE = defaultFontSize;
        // max
        this.MAX_FONTSIZE = 256;

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
        KeyboardUtils.setSoftInputModeAdjustResize(this.mActivity);
        // Clear any previous flags to disable soft keyboard in case setting updated
        KeyboardUtils.clearDisableSoftKeyboardFlags(this.mActivity);
        // If soft keyboard is to be hidden on startup
        // Required to keep keyboard hidden when Termux app is switched back from another app
        KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(this.mActivity);
        KeyboardUtils.hideSoftKeyboard(this.mActivity, this.mActivity.getTerminalView());
        this.mActivity.getTerminalView().requestFocus();
        // Required to keep keyboard hidden on app startup
        this.mShowSoftKeyboardIgnoreOnce = true;

        this.mActivity.getTerminalView().setOnFocusChangeListener((view, hasFocus) -> {
            // Force show soft keyboard if TerminalView or toolbar text input view has
            // focus and close it if they don't


            if (hasFocus) {
                if (this.mShowSoftKeyboardIgnoreOnce) {
                    this.mShowSoftKeyboardIgnoreOnce = false;
                    return;
                }

            }
            KeyboardUtils.setSoftKeyboardVisibility(this.getShowSoftKeyboardRunnable(), this.mActivity, this.mActivity.getTerminalView(), hasFocus);
        });
        // Do not force show soft keyboard if termux-reload-settings command was run with hardware keyboard
        // or soft keyboard is to be hidden or is disabled
    }

    private Runnable getShowSoftKeyboardRunnable() {
        if (null == mShowSoftKeyboardRunnable) {
            this.mShowSoftKeyboardRunnable = () -> KeyboardUtils.showSoftKeyboard(this.mActivity, this.mActivity.getTerminalView());
        }
        return this.mShowSoftKeyboardRunnable;
    }

}
