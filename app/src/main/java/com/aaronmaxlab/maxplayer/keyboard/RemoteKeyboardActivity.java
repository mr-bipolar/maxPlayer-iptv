package com.aaronmaxlab.maxplayer.keyboard;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;


import com.aaronmaxlab.maxplayer.databinding.ActivityRemoteKeyboardBinding;
import com.aaronmaxlab.maxplayer.utils.ClickUtils;
import com.aaronmaxlab.maxplayer.utils.UIUtils;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;;
import androidx.activity.result.ActivityResultLauncher;
import android.util.Log;

import com.aaronmaxlab.maxplayer.remote.RemoteWebSocketClient;

import org.json.JSONObject;

public class RemoteKeyboardActivity extends AppCompatActivity {

   private ActivityRemoteKeyboardBinding binding;
    private static final String TAG = "RemoteKeyboard";

    private RemoteWebSocketClient remoteClient;
    private boolean updatingFromTv = false;

    private enum ConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    private int connectionGeneration = 0;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback wifiNetworkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityRemoteKeyboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UIUtils.setLightNavigationBars(this, false);

        updateConnectionUI(ConnectionState.IDLE);
        registerWifiCallback();

        ClickUtils.applyTouchEffect(binding.btnScanQr, view -> startQrScanner());

        setupRemoteInput();
        setupDpadControls();

        ClickUtils.applyTouchEffect(binding.btnClear, view ->  binding.editRemoteInput.setText(""));

        ClickUtils.applyTouchEffect(binding.btnDisconnect, v -> {
            endConnection(
                    connectionGeneration,
                    ConnectionState.IDLE
            );
        });

    }


    private final ActivityResultLauncher<ScanOptions> qrScanner =
            registerForActivityResult(new ScanContract(), result -> {
                Log.d(TAG, "QR callback received");

                String contents = result.getContents();

                Log.d(TAG, "QR contents: " + contents);

                if (contents == null) {
                    Log.e(TAG, "QR scan returned null");
                    Toast.makeText(this, "QR scan cancelled", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    RemoteDeviceInfo deviceInfo = RemoteQrParser.parse(contents);

                    Log.d(TAG, "QR parsed successfully");
                    Log.d(TAG, "TV IP: " + deviceInfo.getIp());
                    Log.d(TAG, "TV Port: " + deviceInfo.getPort());

                    Toast.makeText(this, "QR scanned successfully", Toast.LENGTH_SHORT).show();

                    connectToTv(deviceInfo);

                } catch (Exception e) {
                    Log.e(TAG, "QR parsing failed", e);
                    Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show();
                }

            });

    private void startQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan MaxPlayer TV QR code");
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        options.setCaptureActivity(MaxPlayerCaptureActivity.class);
        qrScanner.launch(options);
    }

    private void connectToTv(RemoteDeviceInfo deviceInfo) {
        Log.d(TAG, "connectToTv() called");
        final int generation = ++connectionGeneration;

        // Close previous connection safely.
        RemoteWebSocketClient previousClient = remoteClient;
        remoteClient = null;

        if (previousClient != null) {
            previousClient.close();
        }

        updateConnectionUI(ConnectionState.CONNECTING);

        RemoteWebSocketClient client = new RemoteWebSocketClient(
                // Temporary emulator testing address.
                "192.168.1.5",
                8766,
                deviceInfo.getToken(),

                new RemoteWebSocketClient.Listener() {

                    @Override
                    public void onConnected() {
                        runOnUiThread(() -> {
                            if (generation != connectionGeneration) return;

                            updateConnectionUI(ConnectionState.CONNECTED);

                            Toast.makeText(
                                    RemoteKeyboardActivity.this,
                                    "Connected to TV!",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        runOnUiThread(() -> {
                            endConnection(
                                    generation,
                                    ConnectionState.DISCONNECTED
                            );
                        });
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        runOnUiThread(() -> {
                            if (generation != connectionGeneration) return;

                            endConnection(
                                    generation,
                                    ConnectionState.DISCONNECTED
                            );

                            Toast.makeText(
                                    RemoteKeyboardActivity.this,
                                    "TV authentication failed. Please scan again.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                    @Override
                    public void onMessageReceived(String message) {
                        handleTvMessage(message);
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Connection error", error);

                        runOnUiThread(() -> {
                            endConnection(
                                    generation,
                                    ConnectionState.DISCONNECTED
                            );
                        });
                    }
                }
        );

        Log.d(TAG, "Calling WebSocket connect()");

        remoteClient = client;
        client.connect();
    }

    private void setupRemoteInput() {
        binding.editRemoteInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updatingFromTv && remoteClient != null) {
                    remoteClient.sendText(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }


    private void updateConnectionUI(ConnectionState state) {

        Log.d(TAG, "updateConnectionUI called: " + state);

        boolean connecting = state == ConnectionState.CONNECTING;
        boolean connected = state == ConnectionState.CONNECTED;
        boolean active = connecting || connected;
        setDpadEnabled(connected);

        binding.btnScanQr.setVisibility(active ? View.GONE : View.VISIBLE);
        binding.layoutConnectionStatus.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.btnDisconnect.setVisibility(active ? View.VISIBLE : View.GONE);

        if (connecting) {
            binding.txtConnectionStatus.setText("Connecting...");
        } else if (connected) {
            binding.txtConnectionStatus.setText("Connected");
        }

        Log.d(TAG, "Scan visibility: " + binding.btnScanQr.getVisibility());
        Log.d(TAG, "Status visibility: " + binding.layoutConnectionStatus.getVisibility());
        Log.d(TAG, "Disconnect visibility: " + binding.btnDisconnect.getVisibility());
    }


    private void endConnection(
            int generation,
            ConnectionState state
    ) {
        if (generation != connectionGeneration) return;

        // Invalidate any further callbacks from this connection.
        connectionGeneration++;

        RemoteWebSocketClient client = remoteClient;
        remoteClient = null;

        if (client != null) {
            client.close();
        }

        updateConnectionUI(state);
    }

    private void registerWifiCallback() {

        connectivityManager = (ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);

        wifiNetworkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onLost(Network network) {
                super.onLost(network);

                Network activeNetwork =
                        connectivityManager.getActiveNetwork();

                NetworkCapabilities capabilities =
                        activeNetwork != null
                                ? connectivityManager.getNetworkCapabilities(activeNetwork)
                                : null;

                boolean wifiStillAvailable =
                        capabilities != null &&
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);

                if (!wifiStillAvailable) {
                    runOnUiThread(() -> {

                        if (remoteClient != null) {
                            endConnection(
                                    connectionGeneration,
                                    ConnectionState.DISCONNECTED
                            );
                        }
                    });
                }
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        connectivityManager.registerNetworkCallback(
                request,
                wifiNetworkCallback
        );
    }

    private void handleTvMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            if (!"text".equals(json.optString("type"))) {
                return;
            }

            String text = json.optString("text", "");

            runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }

                updatingFromTv = true;

                binding.editRemoteInput.setText(text);
                binding.editRemoteInput.setSelection(text.length());

                updatingFromTv = false;
            });

        } catch (Exception e) {
            Log.e(TAG, "Invalid TV message", e);
        }
    }

    private void setDpadEnabled(boolean enabled) {

        binding.btnDpadUp.setEnabled(enabled);
        binding.btnDpadDown.setEnabled(enabled);
        binding.btnDpadLeft.setEnabled(enabled);
        binding.btnDpadRight.setEnabled(enabled);
        binding.btnDpadCenter.setEnabled(enabled);
        binding.btnDpadBack.setEnabled(enabled);

        float alpha = enabled ? 1.0f : 0.4f;

        binding.btnDpadUp.setAlpha(alpha);
        binding.btnDpadDown.setAlpha(alpha);
        binding.btnDpadLeft.setAlpha(alpha);
        binding.btnDpadRight.setAlpha(alpha);
        binding.btnDpadCenter.setAlpha(alpha);
        binding.btnDpadBack.setAlpha(alpha);
    }


    private void setupDpadControls() {

        ClickUtils.applyTouchEffect(binding.btnDpadUp,
                v -> sendRemoteKey("up"));

        ClickUtils.applyTouchEffect(binding.btnDpadDown,
                v -> sendRemoteKey("down"));

        ClickUtils.applyTouchEffect(binding.btnDpadLeft,
                v -> sendRemoteKey("left"));

        ClickUtils.applyTouchEffect(binding.btnDpadRight,
                v -> sendRemoteKey("right"));

        ClickUtils.applyTouchEffect(binding.btnDpadCenter,
                v -> sendRemoteKey("center"));

        ClickUtils.applyTouchEffect(binding.btnDpadBack,
                v -> sendRemoteKey("back"));
    }


    private void sendRemoteKey(String key) {

        if (remoteClient == null || !remoteClient.isAuthenticated()) {
            Log.w(TAG, "Cannot send key: TV not connected");
            return;
        }

        remoteClient.sendKey(key);
    }



    @Override
    protected void onDestroy() {
        connectionGeneration++;

        RemoteWebSocketClient client = remoteClient;
        remoteClient = null;

        if (client != null) {
            client.close();
        }

        if (connectivityManager != null && wifiNetworkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(
                        wifiNetworkCallback
                );
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Wi-Fi callback already unregistered");
            }
        }

        super.onDestroy();
    }
}