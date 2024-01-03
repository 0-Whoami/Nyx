package com.termux.shared.net.socket.local;

import androidx.annotation.NonNull;

/**
 * Base helper implementation for {@link ILocalSocketManager}.
 */
public abstract class LocalSocketManagerClientBase implements ILocalSocketManager {

    @Override
    public void onClientAccepted(@NonNull LocalSocketManager localSocketManager, @NonNull LocalClientSocket clientSocket) {
        // Just close socket and let child class handle any required communication
        clientSocket.closeClientSocket();
    }

}
