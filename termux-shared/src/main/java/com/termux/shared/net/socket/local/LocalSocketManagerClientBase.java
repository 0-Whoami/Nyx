package com.termux.shared.net.socket.local;


/**
 * Base helper implementation for {@link ILocalSocketManager}.
 */
public abstract class LocalSocketManagerClientBase implements ILocalSocketManager {

    @Override
    public void onClientAccepted(LocalSocketManager localSocketManager, LocalClientSocket clientSocket) {
        // Just close socket and let child class handle any required communication
        clientSocket.closeClientSocket();
    }

}
