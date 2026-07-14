package com.svpn.app;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class XrayCoreWrapper {
    private static final String TAG = "SVPN_XrayCore";
    private static Process xrayProcess = null;

    public interface XrayStatusListener {
        void onStart();
        void onStop();
        void onError(String message);
    }

    /**
     * Initializes and extracts the Xray binary asset if needed.
     */
    public static void initializeCore(Context context) {
        try {
            File binDir = context.getDir("bin", Context.MODE_PRIVATE);
            File xrayExe = new File(binDir, "xray");
            if (!xrayExe.exists()) {
                // In a production app, compile and copy the xray binary for the specific architecture (arm64-v8a, etc.)
                // This wrapper creates the placeholder executable or loads go-mobile library.
                xrayExe.createNewFile();
                xrayExe.setExecutable(true, true);
                Log.d(TAG, "Xray binary initialized.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Xray Core", e);
        }
    }

    /**
     * Start connection based on Selected Profile
     */
    public static void startVpn(Context context, ConfigParser.VpnProfile profile, XrayStatusListener listener) {
        Log.i(TAG, "Starting tunnel to: " + profile.name + " (" + profile.address + ":" + profile.port + ")");
        
        // 1. Prepare standard Xray Config JSON matching the profile type
        String xrayJsonConfig = generateXrayJson(profile);
        
        // 2. Write configuration to internal file
        try {
            File configFile = new File(context.getFilesDir(), "config.json");
            FileOutputStream fos = new FileOutputStream(configFile);
            fos.write(xrayJsonConfig.getBytes("UTF-8"));
            fos.close();
            Log.d(TAG, "Config JSON written to " + configFile.getAbsolutePath());
        } catch (Exception e) {
            listener.onError("Failed to write config.json: " + e.getMessage());
            return;
        }

        // 3. Request VPN Service permission & Launch VpnService
        Intent intent = VpnService.prepare(context);
        if (intent != null) {
            // Need permission activity call from MainActivity
            listener.onError("VPN_PERMISSION_REQUIRED");
        } else {
            // Permission already granted, start background VpnService
            Intent serviceIntent = new Intent(context, SvpnVpnService.class);
            serviceIntent.setAction(SvpnVpnService.ACTION_CONNECT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            listener.onStart();
        }
    }

    public static void stopVpn(Context context, XrayStatusListener listener) {
        Log.i(TAG, "Stopping SVPN service...");
        Intent serviceIntent = new Intent(context, SvpnVpnService.class);
        serviceIntent.setAction(SvpnVpnService.ACTION_DISCONNECT);
        context.startService(serviceIntent);
        listener.onStop();
    }

    private static String generateXrayJson(ConfigParser.VpnProfile profile) {
        // Generates dynamic outbound routing config matching the selected protocol (VLESS, VMess, Trojan, SS)
        String outboundJson = "";
        
        if ("vless".equalsIgnoreCase(profile.type)) {
            outboundJson = "{\n" +
                    "      \"protocol\": \"vless\",\n" +
                    "      \"settings\": {\n" +
                    "        \"vnext\": [{\n" +
                    "          \"address\": \"" + profile.address + "\",\n" +
                    "          \"port\": " + profile.port + ",\n" +
                    "          \"users\": [{\n" +
                    "            \"id\": \"" + profile.id + "\",\n" +
                    "            \"encryption\": \"none\"\n" +
                    "          }]\n" +
                    "        }]\n" +
                    "      },\n" +
                    "      \"streamSettings\": {\n" +
                    "        \"network\": \"ws\",\n" +
                    "        \"security\": \"tls\"\n" +
                    "      }\n" +
                    "    }";
        } else if ("ss".equalsIgnoreCase(profile.type)) {
            // Shadowsocks parsing of method & password
            String method = "aes-256-gcm";
            String password = profile.id;
            if (profile.id.contains(":")) {
                String[] parts = profile.id.split(":", 2);
                method = parts[0];
                password = parts[1];
            }
            outboundJson = "{\n" +
                    "      \"protocol\": \"shadowsocks\",\n" +
                    "      \"settings\": {\n" +
                    "        \"servers\": [{\n" +
                    "          \"address\": \"" + profile.address + "\",\n" +
                    "          \"port\": " + profile.port + ",\n" +
                    "          \"method\": \"" + method + "\",\n" +
                    "          \"password\": \"" + password + "\"\n" +
                    "        }]\n" +
                    "      }\n" +
                    "    }";
        } else if ("trojan".equalsIgnoreCase(profile.type)) {
            outboundJson = "{\n" +
                    "      \"protocol\": \"trojan\",\n" +
                    "      \"settings\": {\n" +
                    "        \"servers\": [{\n" +
                    "          \"address\": \"" + profile.address + "\",\n" +
                    "          \"port\": " + profile.port + ",\n" +
                    "          \"password\": \"" + profile.id + "\"\n" +
                    "        }]\n" +
                    "      }\n" +
                    "    }";
        } else {
            // Fallback generic VMess
            outboundJson = "{\n" +
                    "      \"protocol\": \"vmess\",\n" +
                    "      \"settings\": {\n" +
                    "        \"vnext\": [{\n" +
                    "          \"address\": \"" + profile.address + "\",\n" +
                    "          \"port\": " + profile.port + ",\n" +
                    "          \"users\": [{\n" +
                    "            \"id\": \"" + profile.id + "\"\n" +
                    "          }]\n" +
                    "        }]\n" +
                    "      }\n" +
                    "    }";
        }

        return "{\n" +
                "  \"log\": {\n" +
                "    \"loglevel\": \"warning\"\n" +
                "  },\n" +
                "  \"inbounds\": [{\n" +
                "    \"port\": 10808,\n" +
                "    \"listen\": \"127.0.0.1\",\n" +
                "    \"protocol\": \"socks\",\n" +
                "    \"settings\": {\n" +
                "      \"auth\": \"noauth\",\n" +
                "      \"udp\": true\n" +
                "    }\n" +
                "  }],\n" +
                "  \"outbounds\": [" + outboundJson + "]\n" +
                "}";
    }
}
