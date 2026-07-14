package com.svpn.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SvpnVpnService extends VpnService implements Runnable {
    private static final String TAG = "SVPN_VpnService";
    
    public static final String ACTION_CONNECT = "com.svpn.app.CONNECT";
    public static final String ACTION_DISCONNECT = "com.svpn.app.DISCONNECT";
    private static final String CHANNEL_ID = "SVPN_CHANNEL";

    private Thread mThread;
    private ParcelFileDescriptor mInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                startVpn();
            } else if (ACTION_DISCONNECT.equals(action)) {
                stopVpn();
            }
        }
        return START_STICKY;
    }

    private void startVpn() {
        createNotificationChannel();
        
        Intent disconnectIntent = new Intent(this, SvpnVpnService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        PendingIntent pDisconnectIntent = PendingIntent.getService(this, 0, disconnectIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SVPN is Active")
                .setContentText("Securing your internet connection via Xray Core...")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pDisconnectIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        startForeground(1, notification);

        if (mThread != null && mThread.isAlive()) {
            mThread.interrupt();
        }
        mThread = new Thread(this, "SVPN-Thread");
        mThread.start();
    }

    private void stopVpn() {
        if (mInterface != null) {
            try {
                mInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
            mInterface = null;
        }
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void run() {
        try {
            // Establish the local IP route configurations for local capture
            Builder builder = new Builder();
            builder.setSession("SVPN Session")
                   .setMtu(1500)
                   .addAddress("10.8.0.2", 32)
                   .addRoute("0.0.0.0", 0) // Capture all outbound Traffic
                   .addDnsServer("1.1.1.1")
                   .addDnsServer("8.8.8.8");

            mInterface = builder.establish();
            Log.i(TAG, "VPN interface established successfully: " + mInterface);

            // In production, loop to route Packets from mInterface (TUN) into the local Xray SOCKS5/HTTP inbound
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "VPN service interrupted.");
        } catch (Exception e) {
            Log.e(TAG, "Error running VPN service", e);
        } finally {
            stopVpn();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SVPN Background Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpn();
    }
}
