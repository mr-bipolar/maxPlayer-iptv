package com.aaronmaxlab.maxplayer.keyboard;

import org.json.JSONException;
import org.json.JSONObject;

public class RemoteQrParser {

    private static final String EXPECTED_TYPE = "maxplayer_remote";
    private static final int SUPPORTED_VERSION = 1;

    public static RemoteDeviceInfo parse(String content)
            throws JSONException {

        JSONObject json = new JSONObject(content);

        String type = json.getString("type");
        int version = json.getInt("version");
        String ip = json.getString("ip");
        int port = json.getInt("port");
        String token = json.getString("token");

        if (!EXPECTED_TYPE.equals(type)) {
            throw new JSONException("Invalid QR code type");
        }

        if (version != SUPPORTED_VERSION) {
            throw new JSONException("Unsupported QR version");
        }

        if (ip.trim().isEmpty() || port < 1 || port > 65535) {
            throw new JSONException("Invalid TV address");
        }

        if (token.trim().isEmpty()) {
            throw new JSONException("Invalid pairing token");
        }

        return new RemoteDeviceInfo(type, version, ip, port, token);
    }
}