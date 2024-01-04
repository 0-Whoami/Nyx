package com.termux.shared.shell.am;


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
     * @param title                    The {@link #mTitle} value.
     * @param path                     The {@link #mPath} value.
     * @param localSocketManagerClient The {@link #mLocalSocketManagerClient} value.
     */
    public AmSocketServerRunConfig(String title, String path, ILocalSocketManager localSocketManagerClient) {
        super(title, path, localSocketManagerClient);
    }


}
