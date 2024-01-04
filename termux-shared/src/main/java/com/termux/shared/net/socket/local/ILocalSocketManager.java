package com.termux.shared.net.socket.local;


/**
 * The interface for the {@link LocalSocketManager} for callbacks to manager client/server starter.
 */
public interface ILocalSocketManager {

    /**
     * This is called if a {@link LocalServerSocket} connects to the server which has the
     * the server app's user id or root user id. It is the responsibility of the interface
     * implementation to close the client socket with a call to
     * once its done processing.
     * <p>
     * The  can be used to get the {@link PeerCred} object
     * containing info for the connected client/peer.
     *
     * @param localSocketManager The {@link LocalSocketManager} for the server.
     * @param clientSocket       The {@link LocalClientSocket} that connected.
     */
    void onClientAccepted(LocalSocketManager localSocketManager, LocalClientSocket clientSocket);
}
