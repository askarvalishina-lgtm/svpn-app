package com.svpn.app;

import android.util.Base64;
import android.util.Log;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class ConfigParser {
    private static final String TAG = "SVPN_ConfigParser";

    public static class VpnProfile {
        public String name;
        public String type; // vless, vmess, ss, trojan
        public String address;
        public int port;
        public String id; // UUID, password, or key
        public String rawUri;
        public boolean isSelected = false;

        @Override
        public String toString() {
            return name + " (" + type.toUpperCase() + ")";
        }
    }

    public static List<VpnProfile> parseSubscription(String input) {
        List<VpnProfile> profiles = new ArrayList<>();
        if (input == null || input.isEmpty()) return profiles;

        String decoded = input;
        // Check if it's base64 encoded subscription
        try {
            byte[] data = Base64.decode(input.trim(), Base64.DEFAULT);
            decoded = new String(data, "UTF-8");
            Log.d(TAG, "Decoded Base64 subscription successfully");
        } catch (Exception e) {
            Log.d(TAG, "Input is not base64 or failed to decode, trying raw lines");
        }

        String[] lines = decoded.split("\\r?\\n");
        int index = 1;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            VpnProfile profile = parseUri(line, index++);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    public static VpnProfile parseUri(String uri, int fallbackIndex) {
        try {
            VpnProfile profile = new VpnProfile();
            profile.rawUri = uri;

            if (uri.startsWith("vless://")) {
                profile.type = "vless";
                parseVlessOrVmess(uri, profile, fallbackIndex);
                return profile;
            } else if (uri.startsWith("vmess://")) {
                profile.type = "vmess";
                parseVmessJson(uri, profile, fallbackIndex);
                return profile;
            } else if (uri.startsWith("ss://")) {
                profile.type = "ss";
                parseShadowsocks(uri, profile, fallbackIndex);
                return profile;
            } else if (uri.startsWith("trojan://")) {
                profile.type = "trojan";
                parseTrojan(uri, profile, fallbackIndex);
                return profile;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing URI: " + uri, e);
        }
        return null;
    }

    private static void parseVlessOrVmess(String uri, VpnProfile profile, int index) throws Exception {
        // vless://uuid@host:port?query#name
        String withoutScheme = uri.substring(8);
        String mainPart = withoutScheme;
        String name = "VLESS Profile " + index;

        if (withoutScheme.contains("#")) {
            int hashIndex = withoutScheme.indexOf("#");
            mainPart = withoutScheme.substring(0, hashIndex);
            try {
                name = URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8");
            } catch (Exception ignored) {}
        }
        profile.name = name;

        int atIndex = mainPart.indexOf("@");
        if (atIndex != -1) {
            profile.id = mainPart.substring(0, atIndex);
            String rest = mainPart.substring(atIndex + 1);
            int colonIndex = rest.indexOf(":");
            if (colonIndex != -1) {
                profile.address = rest.substring(0, colonIndex);
                String portAndQuery = rest.substring(colonIndex + 1);
                int qIndex = portAndQuery.indexOf("?");
                if (qIndex != -1) {
                    profile.port = Integer.parseInt(portAndQuery.substring(0, qIndex));
                } else {
                    profile.port = Integer.parseInt(portAndQuery);
                }
            }
        }
    }

    private static void parseVmessJson(String uri, VpnProfile profile, int index) {
        // vmess://base64(json)
        profile.name = "VMess Profile " + index;
        try {
            String base64Part = uri.substring(8);
            byte[] data = Base64.decode(base64Part, Base64.DEFAULT);
            String jsonStr = new String(data, "UTF-8");
            // Basic regex parsing to avoid needing a heavy JSON library in this parser helper
            profile.address = getJsonValue(jsonStr, "add");
            String portStr = getJsonValue(jsonStr, "port");
            profile.port = portStr.isEmpty() ? 443 : Integer.parseInt(portStr);
            profile.id = getJsonValue(jsonStr, "id");
            String ps = getJsonValue(jsonStr, "ps");
            if (!ps.isEmpty()) {
                profile.name = ps;
            }
        } catch (Exception e) {
            profile.address = "unknown";
            profile.port = 443;
            profile.id = "";
        }
    }

    private static void parseShadowsocks(String uri, VpnProfile profile, int index) throws Exception {
        // ss://base64(method:password)@host:port#name
        String withoutScheme = uri.substring(5);
        String mainPart = withoutScheme;
        String name = "Shadowsocks " + index;

        if (withoutScheme.contains("#")) {
            int hashIndex = withoutScheme.indexOf("#");
            mainPart = withoutScheme.substring(0, hashIndex);
            try {
                name = URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8");
            } catch (Exception ignored) {}
        }
        profile.name = name;

        int atIndex = mainPart.indexOf("@");
        if (atIndex != -1) {
            String userInfoBase64 = mainPart.substring(0, atIndex);
            try {
                byte[] decodedBytes = Base64.decode(userInfoBase64, Base64.DEFAULT);
                profile.id = new String(decodedBytes, "UTF-8"); // "method:password"
            } catch (Exception e) {
                profile.id = userInfoBase64;
            }

            String rest = mainPart.substring(atIndex + 1);
            int colonIndex = rest.indexOf(":");
            if (colonIndex != -1) {
                profile.address = rest.substring(0, colonIndex);
                profile.port = Integer.parseInt(rest.substring(colonIndex + 1));
            }
        }
    }

    private static void parseTrojan(String uri, VpnProfile profile, int index) throws Exception {
        // trojan://password@host:port#name
        String withoutScheme = uri.substring(9);
        String mainPart = withoutScheme;
        String name = "Trojan " + index;

        if (withoutScheme.contains("#")) {
            int hashIndex = withoutScheme.indexOf("#");
            mainPart = withoutScheme.substring(0, hashIndex);
            try {
                name = URLDecoder.decode(withoutScheme.substring(hashIndex + 1), "UTF-8");
            } catch (Exception ignored) {}
        }
        profile.name = name;

        int atIndex = mainPart.indexOf("@");
        if (atIndex != -1) {
            profile.id = mainPart.substring(0, atIndex);
            String rest = mainPart.substring(atIndex + 1);
            int colonIndex = rest.indexOf(":");
            if (colonIndex != -1) {
                profile.address = rest.substring(0, colonIndex);
                profile.port = Integer.parseInt(rest.substring(colonIndex + 1));
            }
        }
    }

    private static String getJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"?([^\",}]+)\"?";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1).trim().replace("\"", "");
        }
        return "";
    }
}
