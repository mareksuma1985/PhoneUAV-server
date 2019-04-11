package pl.bezzalogowe.PhoneUAV;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkPing {
    private static final String TAG = "ping";
    MainActivity main;
    ScheduledExecutorService executorPing;
    InetAddress remoteAddress;

    public NetworkPing(MainActivity argActivity) {
        main = argActivity;
    }

    public void startPinging(byte[] ipAddr) {
        try {
            remoteAddress = InetAddress.getByAddress(ipAddr);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            Log.d(TAG, "getByAddress: " + e);
        }

        Log.d(TAG, "Remote address: " + remoteAddress.getHostName());
        executorPing = Executors.newSingleThreadScheduledExecutor();
        executorPing.scheduleAtFixedRate(new Runnable() {
            public void run() {
                boolean reachable = true;
                try {
                    reachable = remoteAddress.isReachable(499);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    Log.d(TAG, "UnknownHostException: " + e);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "IOException: " + e);
                }

                if (!reachable) {
                    Log.d(TAG, "Remote address unreachable!");
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }
}

