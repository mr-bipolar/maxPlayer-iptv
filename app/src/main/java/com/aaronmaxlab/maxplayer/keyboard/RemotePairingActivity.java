package com.aaronmaxlab.maxplayer.keyboard;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityRemotePairingBinding;
import com.aaronmaxlab.maxplayer.remote.RemoteWebSocketServer;
import com.aaronmaxlab.maxplayer.utils.UIUtils;

import org.json.JSONObject;

public class RemotePairingActivity extends AppCompatActivity {

    private ActivityRemotePairingBinding binding;
    private RemoteWebSocketServer remoteServer;

    private static final int REMOTE_PORT = 8765;

    private RemotePairingSession pairingSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityRemotePairingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        UIUtils.applySystemBarPadding(binding.getRoot(), false);

        // Create pairing session before starting the server.
        String ip = NetworkUtils.getLocalIpv4Address(this);

        if (ip == null) {
            Log.e("REMOTE", "Unable to detect TV IP address");
            return;
        }

        pairingSession = new RemotePairingSession(ip, REMOTE_PORT);

        startRemoteServer();
        setupKeyboardActions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        RemoteWebSocketServer server = remoteServer;
        remoteServer = null;

        if (server != null) {
            new Thread(() -> {
                try {
                    server.stop(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e("REMOTE", "Server stop interrupted", e);
                }
            }).start();
        }
    }


    private void startRemoteServer() {

        remoteServer = new RemoteWebSocketServer(
                REMOTE_PORT,
                pairingSession.getToken(),
                new RemoteWebSocketServer.Listener() {

                    @Override
                    public void onServerStarted(int port) {
                        Log.d("REMOTE", "Server ready on port: " + port);
                        RemotePairingActivity.this.onServerStarted(port);
                    }

                    @Override
                    public void onClientConnected() {
                        Log.d("REMOTE", "Phone authenticated!");
                    }

                    @Override
                    public void onClientDisconnected() {
                        Log.d("REMOTE", "Phone disconnected!");
                    }

                    @Override
                    public void onMessageReceived(String message) {
                        handleRemoteMessage(message);
                    }

                    @Override
                    public void onServerError(Exception error) {
                        Log.e("REMOTE", "Server error", error);
                    }
                }
        );

        remoteServer.start();
    }


    private void onServerStarted(int port) {

        runOnUiThread(() -> {

            try {
                String ip = pairingSession.getIpAddress();

                String payload = pairingSession.toQrPayload();

                Bitmap qrBitmap = QrCodeManager.generateQrCode(
                        payload,
                        400
                );

                binding.ivPairingQr.setImageBitmap(qrBitmap);

                // Display TV IP
               // binding.txtTvIp.setText(ip);

                Log.d("REMOTE", "Pairing QR generated");
                Log.d("REMOTE", "TV IP: " + ip);
                Log.d("REMOTE", "TV Port: " + port);

            } catch (Exception e) {
                Log.e("REMOTE", "QR generation failed", e);
            }
        });
    }

    private void handleRemoteMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            if (!"text".equals(json.optString("type"))) {
                return;
            }

            String text = json.optString("text", "");

            runOnUiThread(() -> {
                if (binding != null) {
                    binding.tvInputText.setText(text);
                }
            });

        } catch (Exception e) {
            Log.e("REMOTE", "Invalid remote message", e);
        }
    }

    private void setupKeyboardActions() {

        // Handle all character keys automatically
        setupCharacterKeys(binding.tvKeyboardContainer);

        // Space
        binding.keySpace.setOnClickListener(v -> {
            appendText(" ");
        });

        // Clear
        binding.keyClear.setOnClickListener(v -> {
            binding.tvInputText.setText("");
        });

        binding.keyBackspace.setOnClickListener(v -> {
            String current = binding.tvInputText.getText().toString();

            if (!current.isEmpty()) {
                binding.tvInputText.setText(
                        current.substring(0, current.length() - 1)
                );
            }
        });

        // Add Playlist
        binding.keyAddPlaylist.setOnClickListener(v -> {
            String playlistUrl = binding.tvInputText.getText().toString().trim();

            if (playlistUrl.isEmpty()) {
                return;
            }

            // TODO: Connect your existing Add Playlist logic here.
        });
    }

    private void setupCharacterKeys(LinearLayout container) {

        for (int i = 0; i < container.getChildCount(); i++) {

            View row = container.getChildAt(i);

            if (!(row instanceof LinearLayout)) {
                continue;
            }

            LinearLayout keyRow = (LinearLayout) row;

            for (int j = 0; j < keyRow.getChildCount(); j++) {

                View key = keyRow.getChildAt(j);

                // Skip special keys
                if (key.getId() == R.id.keyClear ||
                        key.getId() == R.id.keySpace ||
                        key.getId() == R.id.keyAddPlaylist) {
                    continue;
                }

                if (!(key instanceof LinearLayout)) {
                    continue;
                }

                LinearLayout keyLayout = (LinearLayout) key;

                if (keyLayout.getChildCount() == 0) {
                    continue;
                }

                View child = keyLayout.getChildAt(0);

                if (!(child instanceof TextView)) {
                    continue;
                }

                TextView keyText = (TextView) child;

                String character = keyText.getText().toString();

                keyLayout.setOnClickListener(v -> appendText(character));
            }
        }
    }

    private void appendText(String value) {

        String current = binding.tvInputText.getText().toString();

        binding.tvInputText.setText(current + value);
    }
}