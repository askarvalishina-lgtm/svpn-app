package com.svpn.app;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConfigFetcher {
    private static final String TAG = "SVPN_ConfigFetcher";
    private static final String API_URL = "https://supercretn.ps.fhgdps.com/svpn.php";

    public interface ConfigCallback {
        void onSuccess(String rawConfig);
        void onError(Exception e);
    }

    public static void fetchConfig(final ConfigCallback callback) {
        new AsyncTask<Void, Void, Object>() {
            @Override
            protected Object doInBackground(Void... voids) {
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL(API_URL);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setConnectTimeout(10000);
                    urlConnection.setReadTimeout(10000);
                    urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

                    int responseCode = urlConnection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine).append("\n");
                        }
                        in.close();
                        return response.toString().trim();
                    } else {
                        return new Exception("HTTP Error: " + responseCode);
                    }
                } catch (Exception e) {
                    return e;
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }
            }

            @Override
            protected void onPostExecute(Object result) {
                if (result instanceof String) {
                    callback.onSuccess((String) result);
                } else if (result instanceof Exception) {
                    callback.onError((Exception) result);
                }
            }
        }.execute();
    }
}
