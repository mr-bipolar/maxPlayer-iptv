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

public class RemoteKeyboardActivity extends AppCompatActivity {

   private ActivityRemoteKeyboardBinding binding;
    private static final String TAG = "RemoteKeyboard";

    private RemoteWebSocketClient remoteClient;

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

        ClickUtils.applyTouchEffect(binding.btnConnect, view -> startQrScanner());

        setupRemoteInput();

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

                if (result.getContents() == null) {
                    Toast.makeText(
                            this,
                            "QR scanning cancelled",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                try {
                    RemoteDeviceInfo deviceInfo =
                            RemoteQrParser.parse(result.getContents());

                    connectToTv(deviceInfo);

                } catch (Exception e) {
                    Toast.makeText(
                            this,
                            "Invalid MaxPlayer QR code",
                            Toast.LENGTH_LONG
                    ).show();
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
                        Log.d(TAG, "TV message received");
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

        remoteClient = client;
        client.connect();
    }

    private void setupRemoteInput() {
        binding.editRemoteInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (remoteClient != null) {
                    remoteClient.sendText(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateConnectionUI(ConnectionState state) {

        boolean connected = state == ConnectionState.CONNECTED;
        boolean idle = state == ConnectionState.IDLE;

        binding.btnScanQr.setVisibility(
                connected || state == ConnectionState.CONNECTING
                        ? View.GONE : View.VISIBLE
        );

        binding.layoutConnectionStatus.setVisibility(
                idle ? View.GONE : View.VISIBLE
        );

        switch (state) {
            case CONNECTING:
                binding.txtConnectionStatus.setText("Connecting...");
                break;

            case CONNECTED:
                binding.txtConnectionStatus.setText("Connected");
                break;

            case DISCONNECTED:
                binding.txtConnectionStatus.setText("Disconnected");
                break;

            case IDLE:
                break;
        }

        binding.btnDisconnect.setVisibility(
                connected || state == ConnectionState.CONNECTING
                        ? View.VISIBLE : View.GONE
        );
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