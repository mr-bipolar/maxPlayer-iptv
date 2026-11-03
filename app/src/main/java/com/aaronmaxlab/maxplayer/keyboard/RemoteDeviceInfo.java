package com.aaronmaxlab.maxplayer.keyboard;

public class RemoteDeviceInfo {

    private final String type;
    private final int version;
    private final String ip;
    private final int port;
    private final String token;

    public RemoteDeviceInfo(
            String type,
            int version,
            String ip,
            int port,
            String token
    ) {
        this.type = type;
        this.version = version;
        this.ip = ip;
        this.port = port;
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public int getVersion() {
        return version;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }
}