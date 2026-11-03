
package com.aaronmaxlab.maxplayer.remote;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;

public class RemoteWebSocketServer extends WebSocketServer {

    private static final String TAG = "RemoteWebSocketServer";
    private final String expectedToken;
    private WebSocket authenticatedClient;

    public interface Listener {
        void onServerStarted(int port);

        void onClientConnected();

        void onClientDisconnected();

        void onMessageReceived(String message);

        void onServerError(Exception error);
    }

    private final Listener listener;

    public RemoteWebSocketServer(
            int port,
            String expectedToken,
            Listener listener
    ) {
        super(new InetSocketAddress("0.0.0.0", port));
        this.expectedToken = expectedToken;
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
    }

    @Override
    public synchronized void onMessage(WebSocket connection, String message) {

        // Already authenticated client
        if (connection == authenticatedClient) {

            if (listener != null) {
                listener.onMessageReceived(message);
            }

            return;
        }

        // Reject another client if one is already authenticated
        if (authenticatedClient != null) {
            connection.close(1008, "Another client is connected");
            return;
        }

        // First message must authenticate
        try {
            JSONObject json = new JSONObject(message);

            if (!"auth".equals(json.optString("type"))) {
                connection.close(1008, "Authentication required");
                return;
            }

            String receivedToken = json.optString("token");

            if (!expectedToken.equals(receivedToken)) {
                connection.send("{\"type\":\"auth_failed\"}");
                connection.close(1008, "Invalid token");
                return;
            }

            authenticatedClient = connection;

            connection.send("{\"type\":\"auth_success\"}");

            Log.d(TAG, "Client authenticated");

            if (listener != null) {
                listener.onClientConnected();
            }

        } catch (Exception e) {
            Log.e(TAG, "Authentication failed", e);
            connection.close(1008, "Invalid authentication message");
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

        if (connection == authenticatedClient) {
            authenticatedClient = null;

            if (listener != null) {
                listener.onClientDisconnected();
            }
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
