package com.termux.shared.interact;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.termux.shared.R;
import com.termux.shared.data.DataUtils;


public class ShareUtils {


    /**
     * Open the system app chooser that allows the user to select which app to send the intent.
     *
     * @param context The context for operations.
     * @param intent The intent that describes the choices that should be shown.
     * @param title The title for choose menu.
     */
    public static void openSystemAppChooser(final Context context, final Intent intent, final String title) {
        if (context == null)
            return;
        final Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, title);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(chooserIntent);
        } catch (Exception ignored) {
        }
    }

    /**
     * Share text.
     *
     * @param context The context for operations.
     * @param subject The subject for sharing.
     * @param text The text to share.
     * @param title The title for share menu.
     */
    public static void shareText(final Context context, final String subject, final String text,  final String title) {
        if (context == null || text == null)
            return;
        final Intent shareTextIntent = new Intent(Intent.ACTION_SEND);
        shareTextIntent.setType("text/plain");
        shareTextIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        shareTextIntent.putExtra(Intent.EXTRA_TEXT, DataUtils.getTruncatedCommandOutput(text, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, true, false, false));
        openSystemAppChooser(context, shareTextIntent, DataUtils.isNullOrEmpty(title) ? context.getString(R.string.title_share_with) : title);
    }


    /**
     * Copy the text to primary clip of the clipboard.
     *
     * @param context The context for operations.
     * @param clipDataLabel The label to show to the user describing the copied text.
     * @param text The text to copy.
     */
    public static void copyTextToClipboard(Context context,final String clipDataLabel, final String text) {
        if (context == null || text == null)
            return;
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null)
            return;
        clipboardManager.setPrimaryClip(ClipData.newPlainText(clipDataLabel, DataUtils.getTruncatedCommandOutput(text, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, true, false, false)));

    }

    /**
     * Wrapper for {@link #getTextFromClipboard(Context, boolean)} that returns primary text {@link String}
     * if its set and not empty.
     */

    public static String getTextStringFromClipboardIfSet(Context context, boolean coerceToText) {
        CharSequence textCharSequence = getTextFromClipboard(context, coerceToText);
        if (textCharSequence == null)
            return null;
        String textString = textCharSequence.toString();
        return !textString.isEmpty() ? textString : null;
    }


    public static CharSequence getTextFromClipboard(Context context, boolean coerceToText) {
        if (context == null)
            return null;
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null)
            return null;
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null)
            return null;
        ClipData.Item clipItem = clipData.getItemAt(0);
        if (clipItem == null)
            return null;
        return coerceToText ? clipItem.coerceToText(context) : clipItem.getText();
    }

    /**
     * Open a url.
     *
     * @param context The context for operations.
     * @param url The url to open.
     */
    public static void openUrl(final Context context, final String url) {
        if (context == null || url == null || url.isEmpty())
            return;
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // If no activity found to handle intent, show system chooser
            openSystemAppChooser(context, intent, context.getString(R.string.title_open_url_with));
        } catch (Exception ignored) {
        }
    }

}
