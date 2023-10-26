package com.termux.shared.shell.am;

import androidx.annotation.NonNull;

import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.net.socket.local.ILocalSocketManager;
import com.termux.shared.net.socket.local.LocalSocketRunConfig;

import java.io.Serializable;

/**
 * Run config for {@link AmSocketServer}.
 */
public class AmSocketServerRunConfig extends LocalSocketRunConfig implements Serializable {

    /**
     * Create an new instance of {@link AmSocketServerRunConfig}.
     *
     * @param title The {@link #mTitle} value.
     * @param path The {@link #mPath} value.
     * @param localSocketManagerClient The {@link #mLocalSocketManagerClient} value.
     */
    public AmSocketServerRunConfig(@NonNull String title, @NonNull String path, @NonNull ILocalSocketManager localSocketManagerClient) {
        super(title, path, localSocketManagerClient);
    }


    /**
     * Get a markdown {@link String} for the {@link AmSocketServerRunConfig}.
     */
    @NonNull
    public String getMarkdownString() {
        return super.getMarkdownString() + "\n\n\n" +
            "## " + "Am Command" +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("CheckDisplayOverAppsPermission", false, "-");
    }


}
