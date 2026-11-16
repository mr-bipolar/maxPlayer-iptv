
package com.aaronmaxlab.maxplayer.keyboard;


import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;

public class RemotePairingSession {

    private static final int TOKEN_BYTES = 32;
    private static final long SESSION_DURATION = 5 * 60 * 1000L;

    private final String token;
    private final String ipAddress;
    private final int port;
    private final long expiresAt;

    public RemotePairingSession(String ipAddress, int port) {

        this.ipAddress = ipAddress;
        this.port = port;
        this.token = generateToken();
        this.expiresAt = System.currentTimeMillis() + SESSION_DURATION;
    }

    private String generateToken() {

        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);

        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }

    public String getToken() {
        return token;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String toQrPayload() throws JSONException {

        JSONObject json = new JSONObject();

        json.put("type", "maxplayer_remote");
        json.put("version", 1);
        json.put("ip", ipAddress);
        json.put("port", port);
        json.put("token", token);

        return json.toString();
    }
}
