
package com.aaronmaxlab.maxplayer.keyboard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.LinkAddress;

import java.net.Inet4Address;
import java.net.InetAddress;

public class NetworkUtils {

    public static String getLocalIpv4Address(Context context) {

        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (manager == null) {
            return null;
        }

        Network network = manager.getActiveNetwork();

        if (network == null) {
            return null;
        }

        LinkProperties properties =
                manager.getLinkProperties(network);

        if (properties == null) {
            return null;
        }

        for (LinkAddress linkAddress : properties.getLinkAddresses()) {

            InetAddress address = linkAddress.getAddress();

            if (address instanceof Inet4Address
                    && !address.isLoopbackAddress()
                    && !address.isLinkLocalAddress()) {

                return address.getHostAddress();
            }
        }

        return null;
    }
}
