package pl.bezzalogowe.PhoneUAV;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkInformation {
    MainActivity main;

public NetworkInformation(MainActivity argActivity)
{
        main = argActivity;
}

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()
                            && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public void showIP(MainActivity arg) {
        AlertDialog.Builder helpBuilder = new AlertDialog.Builder(arg);
        helpBuilder.setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(arg.getResources().getString(R.string.show_ip)).setMessage("IP address: " + getLocalIpAddress())
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        AlertDialog helpDialog = helpBuilder.create();
        helpDialog.show();
    }

    public void showInfo() {
        //FIXME: Doesn't work on some phones
        AlertDialog.Builder helpBuilder = new AlertDialog.Builder(main);
        helpBuilder.setIcon(android.R.drawable.ic_dialog_info);
        WifiManager wifi;
        WifiInfo info;

        String frequency, pattern;
        int channelWLAN;

        helpBuilder.setTitle(main.getResources().getString(R.string.show_info));

        try {
            /* WiFi information */
            wifi = (WifiManager) main.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            info = wifi.getConnectionInfo();

            if (Build.VERSION.SDK_INT <= 20) {
                pattern = "Frequency:\\s*(.*)";
                Pattern r = Pattern.compile(pattern);
                Matcher m = r.matcher(info.toString());
                if (m.find()) {
                    frequency = m.group(1);
                } else {
                    frequency = "";
                }

                /** http://en.wikipedia.org/wiki/List_of_WLAN_channels */
                channelWLAN = ((Integer.valueOf(frequency) - 2412) / 5) + 1;

                if (channelWLAN < 0)
                    channelWLAN = 0;
                helpBuilder.setMessage("Wi-Fi channel: " + channelWLAN + info.toString().replace(", ", "\n"));
            } else {
                helpBuilder.setMessage("Wi-Fi frequency: " + info.getFrequency() + info.FREQUENCY_UNITS + "\n" +
                        "Strength: " + info.getRssi() + "dBm\n" +
                        "SSID: " + info.getSSID());
            }

            helpBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        AlertDialog helpDialog = helpBuilder.create();
        helpDialog.show();
    }
}
