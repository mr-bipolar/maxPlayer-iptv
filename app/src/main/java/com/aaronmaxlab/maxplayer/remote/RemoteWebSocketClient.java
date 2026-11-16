
package com.aaronmaxlab.maxplayer.remote;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;

public class RemoteWebSocketClient extends WebSocketClient {

    private static final String TAG = "RemoteWebSocketClient";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onAuthenticationFailed();
        void onMessageReceived(String message);
        void onError(Exception error);
    }

    private final String token;
    private final Listener listener;

    private boolean authenticated = false;

    public RemoteWebSocketClient(
            String ip,
            int port,
            String token,
            Listener listener
    ) {
        super(URI.create("ws://" + ip + ":" + port));

        this.token = token;
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        Log.d(TAG, "WebSocket opened. Sending authentication.");

        try {
            JSONObject auth = new JSONObject();
            auth.put("type", "auth");
            auth.put("token", token);

            send(auth.toString());

        } catch (Exception e) {
            Log.e(TAG, "Authentication message failed", e);
            if (listener != null) listener.onError(e);
            close();
        }
    }

    @Override
    public void onMessage(String message) {
        Log.d(TAG, "Server message received");

        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");

            if ("auth_success".equals(type)) {
                authenticated = true;

                if (listener != null) {
                    listener.onConnected();
                }

            } else if ("auth_failed".equals(type)) {
                authenticated = false;

                if (listener != null) {
                    listener.onAuthenticationFailed();
                }

                close();

            } else if (authenticated) {
                if (listener != null) {
                    listener.onMessageReceived(message);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Invalid server message", e);
            if (listener != null) listener.onError(e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        authenticated = false;
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    @Override
    public void onError(Exception error) {
        Log.e(TAG, "WEBSOCKET ERROR ", error);

        if (listener != null) {
            listener.onError(error);
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void sendText(String text) {
        if (!authenticated || !isOpen()) {
            Log.w(TAG, "Cannot send text: not authenticated");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("type", "text");
            json.put("text", text);

            send(json.toString());

        } catch (Exception e) {
            Log.e(TAG, "Failed to send text", e);
            if (listener != null) listener.onError(e);
        }
    }

    public void sendKey(String key) {

        if (!authenticated || !isOpen()) {
            Log.w(TAG, "Cannot send key: not authenticated");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("type", "key");
            json.put("key", key);

            send(json.toString());

        } catch (Exception e) {
            Log.e(TAG, "Failed to send key", e);

            if (listener != null) {
                listener.onError(e);
            }
        }
    }
}
