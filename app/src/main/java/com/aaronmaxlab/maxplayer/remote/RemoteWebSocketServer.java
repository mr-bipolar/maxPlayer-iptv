
package com.aaronmaxlab.maxplayer.remote;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class RemoteWebSocketServer extends WebSocketServer {

    private static final String TAG = "RemoteWebSocketServer";

    public interface Listener {
        void onServerStarted(int port);

        void onClientConnected();

        void onClientDisconnected();

        void onMessageReceived(String message);

        void onServerError(Exception error);
    }

    private final Listener listener;

    public RemoteWebSocketServer(int port, Listener listener) {
        super(new InetSocketAddress("0.0.0.0", port));
        this.listener = listener;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "Server started on port: " + getPort());

        if (listener != null) {
            listener.onServerStarted(getPort());
        }
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        Log.d(TAG, "Client connected: " + connection.getRemoteSocketAddress());

        if (listener != null) {
            listener.onClientConnected();
        }
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        Log.d(TAG, "Received: " + message);

        if (listener != null) {
            listener.onMessageReceived(message);
        }
    }



    @Override
    public void onClose(
            WebSocket connection,
            int code,
            String reason,
            boolean remote
    ) {
        Log.d(TAG, "Client disconnected: " + reason);

        if (listener != null) {
            listener.onClientDisconnected();
        }
    }

    @Override
    public void onError(WebSocket connection, Exception error) {
        Log.e(TAG, "WebSocket error", error);

        if (listener != null) {
            listener.onServerError(error);
        }
    }


}
