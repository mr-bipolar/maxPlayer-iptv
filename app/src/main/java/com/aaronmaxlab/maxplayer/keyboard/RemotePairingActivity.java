package com.aaronmaxlab.maxplayer.keyboard;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aaronmaxlab.maxplayer.R;
import com.aaronmaxlab.maxplayer.databinding.ActivityRemotePairingBinding;
import com.aaronmaxlab.maxplayer.remote.RemoteWebSocketServer;
import com.aaronmaxlab.maxplayer.utils.DeviceUtils;
import com.google.zxing.WriterException;

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

        startRemoteServer();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (remoteServer != null) {
            new Thread(() -> {
                try {
                    remoteServer.stop(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e("REMOTE", "Server stop interrupted", e);
                }
            }).start();

            remoteServer = null;
        }
    }




    private void startRemoteServer() {

        remoteServer = new RemoteWebSocketServer(
                REMOTE_PORT,
                new RemoteWebSocketServer.Listener() {

                    @Override
                    public void onServerStarted(int port) {
                        Log.d("REMOTE", "Server ready on port: " + port);
                        RemotePairingActivity.this.onServerStarted(port);
                    }

                    @Override
                    public void onClientConnected() {
                        Log.d("REMOTE", "Phone connected!");
                    }

                    @Override
                    public void onClientDisconnected() {
                        Log.d("REMOTE", "Phone disconnected!");
                    }

                    @Override
                    public void onMessageReceived(String message) {
                        Log.d("REMOTE", "Phone message: " + message);
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

            String ip = NetworkUtils.getLocalIpv4Address(
                    RemotePairingActivity.this
            );

            if (ip == null) {
                Log.e("REMOTE", "Unable to detect TV IP address");
                return;
            }

            try {
                pairingSession = new RemotePairingSession(ip, port);

                String payload = pairingSession.toQrPayload();

                Bitmap qrBitmap = QrCodeManager.generateQrCode(
                        payload,
                        400
                );

                binding.ivPairingQr.setImageBitmap(qrBitmap);

                Log.d("REMOTE", "Pairing QR generated");
                Log.d("REMOTE", "TV IP: " + ip);
                Log.d("REMOTE", "TV Port: " + port);

            } catch (Exception e) {
                Log.e("REMOTE", "QR generation failed", e);
            }
        });
    }
}