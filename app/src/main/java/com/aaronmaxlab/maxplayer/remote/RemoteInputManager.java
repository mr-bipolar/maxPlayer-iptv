
package com.aaronmaxlab.maxplayer.remote;

import android.util.Log;

import com.aaronmaxlab.maxplayer.keyboard.RemotePairingSession;

import org.json.JSONObject;

public class RemoteInputManager {

    private static final String TAG = "RemoteInputManager";
    private static final int REMOTE_PORT = 8765;
    private InputListener inputListener;
    private boolean clientConnected = false;

    public interface Listener {
        void onServerStarted(int port);
        void onClientConnected();
        void onClientDisconnected();
        void onMessageReceived(String message);
        void onServerError(Exception error);
    }

    private RemotePairingSession pairingSession;
    private RemoteWebSocketServer remoteServer;
    private Listener listener;
    private KeyListener keyListener;


    public interface InputListener {
        void onRemoteTextReceived(String text);
    }

    public interface KeyListener {
        void onRemoteKeyReceived(String key);
    }

    public synchronized boolean isClientConnected() {
        return clientConnected;
    }

    public synchronized void setInputListener(InputListener listener) {
        this.inputListener = listener;
    }

    public synchronized void setKeyListener(KeyListener listener) {
        this.keyListener = listener;
    }

    private void handleRemoteMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            String type = json.optString("type");

            // Existing text input handling
            if ("text".equals(type)) {

                String text = json.optString("text", "");

                InputListener currentListener;

                synchronized (this) {
                    currentListener = inputListener;
                }

                if (currentListener != null) {
                    currentListener.onRemoteTextReceived(text);
                }

            }

            // New D-pad navigation handling
            else if ("key".equals(type)) {

                String key = json.optString("key", "");

                // Accept only supported navigation commands.
                switch (key) {
                    case "up":
                    case "down":
                    case "left":
                    case "right":
                    case "center":
                    case "back":
                        break;

                    default:
                        Log.w(TAG, "Unknown remote key: " + key);
                        return;
                }

                KeyListener currentListener;

                synchronized (this) {
                    currentListener = keyListener;
                }

                if (currentListener != null) {
                    currentListener.onRemoteKeyReceived(key);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Invalid remote message", e);
        }
    }


    public synchronized void start(String ipAddress, Listener listener) {

        this.listener = listener;

        // Reuse the existing server if it is already running.
        if (remoteServer != null) {
            notifyListener(l -> l.onServerStarted(REMOTE_PORT));
            return;
        }

        pairingSession = new RemotePairingSession(ipAddress, REMOTE_PORT);

        remoteServer = new RemoteWebSocketServer(
                REMOTE_PORT,
                pairingSession.getToken(),
                new RemoteWebSocketServer.Listener() {

                    @Override
                    public void onServerStarted(int port) {
                        notifyListener(l -> l.onServerStarted(port));
                    }

                    @Override
                    public void onClientConnected() {
                        synchronized (RemoteInputManager.this) {
                            clientConnected = true;
                        }

                        notifyListener(Listener::onClientConnected);
                    }

                    @Override
                    public void onClientDisconnected() {
                        synchronized (RemoteInputManager.this) {
                            clientConnected = false;
                        }

                        notifyListener(Listener::onClientDisconnected);
                    }

                    @Override
                    public void onMessageReceived(String message) {
                        handleRemoteMessage(message);
                        notifyListener(l -> l.onMessageReceived(message));
                    }

                    @Override
                    public void onServerError(Exception error) {
                        notifyListener(l -> l.onServerError(error));
                    }
                }
        );

        remoteServer.start();
    }

    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }


    public synchronized void stop() {

        RemoteWebSocketServer server = remoteServer;

        remoteServer = null;
        pairingSession = null;
        listener = null;
        clientConnected = false;

        if (server != null) {
            new Thread(() -> {
                try {
                    server.stop(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "Server stop interrupted", e);
                }
            }).start();
        }
    }

    public synchronized String getQrPayload() {

        if (pairingSession == null) {
            return null;
        }

        try {
            return pairingSession.toQrPayload();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create QR payload", e);
            return null;
        }
    }

    public synchronized boolean isRunning() {
        return remoteServer != null;
    }

    public synchronized void sendTextToClient(String text) {

        if (remoteServer != null) {
            remoteServer.sendTextToClient(text);
        }
    }

    private void notifyListener(java.util.function.Consumer<Listener> action) {

        Listener currentListener;

        synchronized (this) {
            currentListener = listener;
        }

        if (currentListener != null) {
            action.accept(currentListener);
        }
    }
}
