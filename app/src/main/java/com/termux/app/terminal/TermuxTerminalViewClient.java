package com.termux.app.terminal;

import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.ListView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.data.DataUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.termux.data.TermuxUrlUtils;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

public class TermuxTerminalViewClient extends TermuxTerminalViewClientBase {

    final TermuxActivity mActivity;

    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private Runnable mShowSoftKeyboardRunnable;

    private boolean mShowSoftKeyboardIgnoreOnce;

    private int MIN_FONTSIZE;

    private int MAX_FONTSIZE;

    private int DEFAULT_FONTSIZE;
    public TermuxTerminalViewClient(TermuxActivity activity, TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mActivity = activity;
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public void onCreate() {
        setDefaultFontSizes(mActivity);
        mActivity.getTerminalView().setTextSize(DEFAULT_FONTSIZE);
        mActivity.getTerminalView().setKeepScreenOn(true);
    }


    /**
     * Should be called when mActivity.onResume() is called
     */
    public void onResume() {
        // Show the soft keyboard if required
        setSoftKeyboardState(true,true);
        // Start terminal cursor blinking if enabled
        // If emulator is already set, then start blinker now, otherwise wait for onEmulatorSet()
        // event to start it. This is needed since onEmulatorSet() may not be called after
        // TermuxActivity is started after device display timeout with double tap and not power button.
        //setTerminalCursorBlinkerState(true);

    }



    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            boolean increase = scale > 1.f;
            changeFontSize(increase);
            return 1.0f;
        }
        return scale;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        TerminalEmulator term = mActivity.getCurrentSession().getEmulator();
        int[] columnAndRow = mActivity.getTerminalView().getColumnAndRow(e, true);
        String wordAtTap = term.getScreen().getWordAtLocation(columnAndRow[0], columnAndRow[1]);
        LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(wordAtTap);
        if (!urlSet.isEmpty()) {
            String url = (String) urlSet.iterator().next();
            ShareUtils.openUrl(mActivity, url);
            return;
        }
        if (!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
                KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
        }
    }


    @Override
    public boolean isTerminalViewSelected() {
        return mActivity.getTerminalView().hasFocus();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession currentSession) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning()) {
            mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (keyCode == KeyEvent.KEYCODE_BACK && mActivity.getTerminalView().mEmulator == null) {
            mActivity.finishActivityIfNotFinishing();
            return true;
        }
return false;
    }




    @Override
    public boolean onCodePoint(final int codePoint, boolean ctrlDown, TerminalSession session) {
        if (ctrlDown) {
            if (codePoint == 106 && /* Ctrl+j or \n */
                !session.isRunning()) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
                return true;
            }
        }
        return false;
    }

    public void changeFontSize(boolean increase) {
        int fontSize = DEFAULT_FONTSIZE;
        fontSize += (increase ? 1 : -1) * 2;
        fontSize = Math.max(MIN_FONTSIZE, Math.min(fontSize, MAX_FONTSIZE));
        mActivity.getTerminalView().setTextSize(fontSize);
    }
    public void setDefaultFontSizes(Context context) {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());
        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        // min
        MIN_FONTSIZE = (int) (dipInPixels);
        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        int defaultFontSize = Math.round(8 * dipInPixels);
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (defaultFontSize % 2 == 1)
            defaultFontSize--;
        // default
        DEFAULT_FONTSIZE = defaultFontSize;
        // max
        MAX_FONTSIZE = 256;

    }
    public void setSoftKeyboardState(boolean isStartup, boolean isReloadTermuxProperties) {
        boolean noShowKeyboard = false;
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
            if (isStartup ) {
                // Required to keep keyboard hidden when Termux app is switched back from another app
                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(mActivity);
                KeyboardUtils.hideSoftKeyboard(mActivity, mActivity.getTerminalView());
                mActivity.getTerminalView().requestFocus();
                noShowKeyboard = true;
                // Required to keep keyboard hidden on app startup
                mShowSoftKeyboardIgnoreOnce = true;
            }

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
        if (!isReloadTermuxProperties && !noShowKeyboard) {
            // Request focus for TerminalView
            // Also show the keyboard, since onFocusChange will not be called if TerminalView already
            // had focus on startup to show the keyboard, like when opening url with context menu
            // "Select URL" long press and returning to Termux app with back button. This
            // will also show keyboard even if it was closed before opening url. #2111
            mActivity.getTerminalView().requestFocus();
            mActivity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(), 300);
        }
    }

    private Runnable getShowSoftKeyboardRunnable() {
        if (mShowSoftKeyboardRunnable == null) {
            mShowSoftKeyboardRunnable = () -> KeyboardUtils.showSoftKeyboard(mActivity, mActivity.getTerminalView());
        }
        return mShowSoftKeyboardRunnable;
    }



    public void shareSessionTranscript() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null)
            return;
        String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null)
            return;
        // See https://github.com/termux/termux-app/issues/1166.
        transcriptText = DataUtils.getTruncatedCommandOutput(transcriptText, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, true, false).trim();
        ShareUtils.shareText(mActivity, mActivity.getString(R.string.title_share_transcript), transcriptText, mActivity.getString(R.string.title_share_transcript_with));
    }

    public void showUrlSelection() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null)
            return;
        String text = ShellUtils.getTerminalSessionTranscriptText(session, true, true);
        LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(text);
        if (urlSet.isEmpty()) {
            new AlertDialog.Builder(mActivity).setMessage(R.string.title_select_url_none_found).show();
            return;
        }
        final CharSequence[] urls = urlSet.toArray(new CharSequence[0]);
        // Latest first.
        Collections.reverse(Arrays.asList(urls));
        // Click to copy url to clipboard:
        final AlertDialog dialog = new AlertDialog.Builder(mActivity).setItems(urls, (di, which) -> {
            String url = (String) urls[which];
            ShareUtils.copyTextToClipboard(mActivity, url, mActivity.getString(R.string.msg_select_url_copied_to_clipboard));
        }).setTitle(R.string.title_select_url_dialog).create();
        // Long press to open URL:
        dialog.setOnShowListener(di -> {
            // this is a ListView with your "buds" in it
            ListView lv = dialog.getListView();
            lv.setOnItemLongClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                String url = (String) urls[position];
                ShareUtils.openUrl(mActivity, url);
                return true;
            });
        });
        dialog.show();
    }

}
