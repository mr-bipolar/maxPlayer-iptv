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
import com.aaronmaxlab.maxplayer.remote.RemoteInputController;
import com.aaronmaxlab.maxplayer.utils.AppToast;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.ClipboardUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.aaronmaxlab.maxplayer.MaxPlayerApplication;
import com.aaronmaxlab.maxplayer.remote.RemoteInputManager;

import org.json.JSONObject;

public class RemotePairingActivity extends AppCompatActivity {

    private ActivityRemotePairingBinding binding;
    private RemoteInputManager remoteInputManager;
    private RemoteInputController remoteInputController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityRemotePairingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        UIUtils.applySystemBarPadding(binding.getRoot(), false);

        ClickUtils.applyTouchEffect(binding.backQrBtn, view -> finish());
        ClickUtils.applyTouchEffect(binding.copyKeyboardOut, view -> {
            final String copyText = binding.tvInputText.getText().toString().trim();
            if(copyText.isEmpty()){
                AppToast.warning(this, "Empty textbox");
                return;
            }
            ClipboardUtils.copyText(this, copyText);
        });

        // Create pairing session before starting the server.
        String ip = NetworkUtils.getLocalIpv4Address(this);

        if (ip == null) {
            binding.btnRestartQr.setVisibility(View.VISIBLE);
            AppToast.error(this, "Unable to detect TV IP address");
            return;
        }

        MaxPlayerApplication app =
                (MaxPlayerApplication) getApplication();

        remoteInputManager = app.getRemoteInputManager();
        remoteInputController = app.getRemoteInputController();

        remoteInputManager.start(ip, remoteListener);
        remoteInputController.setPhoneConnected(
                remoteInputManager.isClientConnected()
        );

        ClickUtils.applyTouchEffect(binding.btnRestartQr, v -> regenerateQr());

       setupKeyboardActions();
    }

    @Override
    protected void onDestroy() {
        if (remoteInputManager != null) {
            remoteInputManager.setListener(null);
        }

        binding = null;

        super.onDestroy();
    }


    private void regenerateQr() {
        if (binding == null || remoteInputManager == null) {
            return;
        }

        try {
            String payload = remoteInputManager.getQrPayload();

            if (payload == null || payload.trim().isEmpty()) {
                AppToast.warning(this, "Pairing session is not ready");
                return;
            }

            Bitmap qrBitmap = QrCodeManager.generateQrCode(payload, 400);
            binding.ivPairingQr.setImageBitmap(qrBitmap);
            binding.btnRestartQr.setVisibility(View.GONE);

            Log.d("REMOTE", "QR code generated successfully");

        } catch (Exception e) {
            AppToast.error(this, "QR generation failed");
            Log.e("REMOTE", "QR generation failed", e);
        }
    }


    private final RemoteInputManager.Listener remoteListener =
            new RemoteInputManager.Listener() {

                @Override
                public void onServerStarted(int port) {
                    RemotePairingActivity.this.onServerStarted(port);
                }

                @Override
                public void onClientConnected() {
                    AppToast.success(RemotePairingActivity.this, "Phone authenticated!");
                    if (remoteInputController != null) {
                        remoteInputController.setPhoneConnected(true);
                    }
                }

                @Override
                public void onClientDisconnected() {
                    AppToast.warning(RemotePairingActivity.this, "Phone disconnected!");
                    if (remoteInputController != null) {
                        remoteInputController.setPhoneConnected(false);
                    }
                }

                @Override
                public void onMessageReceived(String message) {
                    handleRemoteMessage(message);
                }

                @Override
                public void onServerError(Exception error) {
                    Log.e("REMOTE", "Server error", error);
                }
            };



    private void onServerStarted(int port) {
        runOnUiThread(() -> {
            if (binding == null || remoteInputManager == null) {
                return;
            }

            try {
                String payload = remoteInputManager.getQrPayload();

                if (payload == null || payload.trim().isEmpty()) {
                    throw new IllegalStateException("QR payload is empty");
                }

                Bitmap qrBitmap = QrCodeManager.generateQrCode(payload, 400);

                binding.ivPairingQr.setImageBitmap(qrBitmap);
                binding.btnRestartQr.setVisibility(View.GONE);
                Log.d("QR_DEBUG", "QR generated successfully");

            } catch (Exception e) {
                binding.btnRestartQr.setVisibility(View.VISIBLE);
                binding.btnRestartQr.setFocusable(true);
                binding.btnRestartQr.setFocusableInTouchMode(true);
                binding.btnRestartQr.post(() -> binding.btnRestartQr.requestFocus());

                Log.e("QR_DEBUG", "QR generation failed", e);
                AppToast.error(this, "QR generation failed");
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
                    updateInputText(text, false);
            });

        } catch (Exception e) {
            Log.e("REMOTE", "Invalid remote message", e);
        }
    }

    private void setupKeyboardActions() {

        setupCharacterKeys(binding.tvKeyboardContainer);

        // Space
        binding.keySpace.setOnClickListener(v -> {
            appendText(" ");
        });

        // Clear
        binding.keyClear.setOnClickListener(v -> {
            updateInputText("", true);
        });

        // Backspace
        binding.keyBackspace.setOnClickListener(v -> {
            String current = binding.tvInputText.getText().toString();

            if (!current.isEmpty()) {
                updateInputText(
                        current.substring(0, current.length() - 1),
                        true
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
                        key.getId() == R.id.keyBackspace ||
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

        updateInputText(current + value, true);
    }


    private void updateInputText(String text, boolean notifyPhone) {
        if (binding == null) {
            return;
        }

        binding.tvInputText.setText(text);

        if (notifyPhone && remoteInputManager != null) {
            remoteInputManager.sendTextToClient(text);
        }
    }

}