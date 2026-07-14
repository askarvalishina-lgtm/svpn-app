package com.svpn.app;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "SVPN_MainActivity";
    private static final int VPN_REQUEST_CODE = 4224;

    private TextView tvStatus, tvServerInfo, tvSubtitle;
    private Button btnConnect, btnRefresh;
    private ListView lvProfiles;
    
    private List<ConfigParser.VpnProfile> profileList = new ArrayList<>();
    private ProfileAdapter profileAdapter;
    private ConfigParser.VpnProfile selectedProfile = null;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Dynamic immersive view
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        setContentView(R.layout.activity_main);

        // UI Initialization
        tvStatus = findViewById(R.id.tvStatus);
        tvServerInfo = findViewById(R.id.tvServerInfo);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        btnConnect = findViewById(R.id.btnConnect);
        btnRefresh = findViewById(R.id.btnRefresh);
        lvProfiles = findViewById(R.id.lvProfiles);

        profileAdapter = new ProfileAdapter(this, profileList);
        lvProfiles.setAdapter(profileAdapter);

        // Core extraction placeholder setup
        XrayCoreWrapper.initializeCore(this);

        // Load dynamic configuration from API on launch
        fetchVpnConfigs();

        // Handle item selection from list
        lvProfiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                for (int i = 0; i < profileList.size(); i++) {
                    profileList.get(i).isSelected = (i == position);
                }
                selectedProfile = profileList.get(position);
                profileAdapter.notifyDataSetChanged();
                
                tvServerInfo.setText("Выбран: " + selectedProfile.name);
                Toast.makeText(MainActivity.this, "Выбран профиль: " + selectedProfile.name, Toast.LENGTH_SHORT).show();
            }
        });

        // Trigger connection action
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected) {
                    disconnectVpn();
                } else {
                    connectVpn();
                }
            }
        });

        // Force reload configuration manually
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchVpnConfigs();
            }
        });
    }

    private void fetchVpnConfigs() {
        tvSubtitle.setText("Получение конфигураций...");
        ConfigFetcher.fetchConfig(new ConfigFetcher.ConfigCallback() {
            @Override
            public void onSuccess(String rawConfig) {
                profileList.clear();
                List<ConfigParser.VpnProfile> parsed = ConfigParser.parseSubscription(rawConfig);
                if (parsed.isEmpty()) {
                    // Fallback to static values if endpoint fails or parses empty
                    addFallbackProfiles();
                } else {
                    profileList.addAll(parsed);
                }

                // Autoselect the first one
                if (!profileList.isEmpty()) {
                    profileList.get(0).isSelected = true;
                    selectedProfile = profileList.get(0);
                    tvServerInfo.setText("Выбран: " + selectedProfile.name);
                }

                profileAdapter.notifyDataSetChanged();
                tvSubtitle.setText("Конфигурации успешно загружены!");
                Toast.makeText(MainActivity.this, "Получено " + profileList.size() + " профилей!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed loading configs", e);
                tvSubtitle.setText("Ошибка загрузки API. Загружены встроенные.");
                addFallbackProfiles();
                profileAdapter.notifyDataSetChanged();
            }
        });
    }

    private void addFallbackProfiles() {
        profileList.clear();
        
        ConfigParser.VpnProfile vless = new ConfigParser.VpnProfile();
        vless.name = "⚡ SVPN Ultra VLESS (Франкфурт)";
        vless.type = "vless";
        vless.address = "de-xray.svpn.net";
        vless.port = 443;
        vless.id = "6e33db38-8c10-466d-9642-430bde83196a";
        vless.rawUri = "vless://6e33db38-8c10-466d-9642-430bde83196a@de-xray.svpn.net:443?encryption=none&security=tls&type=ws#⚡ SVPN Ultra VLESS (Франкфурт)";
        profileList.add(vless);

        ConfigParser.VpnProfile ss = new ConfigParser.VpnProfile();
        ss.name = "🔒 Shadowsocks Stealth (Амстердам)";
        ss.type = "ss";
        ss.address = "nl-ss.svpn.net";
        ss.port = 8388;
        ss.id = "aes-256-gcm:supersecretpassword";
        ss.rawUri = "ss://YWVzLTI1Ni1nY206c3VwZXJzZWNyZXRwYXNzd29yZA==@nl-ss.svpn.net:8388#🔒 Shadowsocks Stealth (Амстердам)";
        profileList.add(ss);

        ConfigParser.VpnProfile trojan = new ConfigParser.VpnProfile();
        trojan.name = "🚀 Trojan High-Speed (Хельсинки)";
        trojan.type = "trojan";
        trojan.address = "fi-trojan.svpn.net";
        trojan.port = 443;
        trojan.id = "trojansvpnkey2026";
        trojan.rawUri = "trojan://trojansvpnkey2026@fi-trojan.svpn.net:443#🚀 Trojan High-Speed (Хельсинки)";
        profileList.add(trojan);

        if (!profileList.isEmpty()) {
            profileList.get(0).isSelected = true;
            selectedProfile = profileList.get(0);
            tvServerInfo.setText("Выбран: " + selectedProfile.name);
        }
    }

    private void connectVpn() {
        if (selectedProfile == null) {
            Toast.makeText(this, "Выберите профиль перед подключением!", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("ПОДКЛЮЧЕНИЕ...");
        tvStatus.setTextColor(Color.parseColor("#FFCC00"));

        XrayCoreWrapper.startVpn(this, selectedProfile, new XrayCoreWrapper.XrayStatusListener() {
            @Override
            public void onStart() {
                isConnected = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText("ЗАЩИЩЕНО");
                        tvStatus.setTextColor(Color.parseColor("#00FF66"));
                        btnConnect.setText("ОТКЛЮЧИТЬ");
                        btnConnect.setBackgroundResource(R.drawable.bg_button_disconnect);
                    }
                });
            }

            @Override
            public void onStop() {
                isConnected = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDisconnectedUi();
                    }
                });
            }

            @Override
            public void onError(String message) {
                if ("VPN_PERMISSION_REQUIRED".equals(message)) {
                    Intent intent = VpnService.prepare(MainActivity.this);
                    if (intent != null) {
                        startActivityForResult(intent, VPN_REQUEST_CODE);
                    }
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Ошибка подключения: " + message, Toast.LENGTH_LONG).show();
                            updateDisconnectedUi();
                        }
                    });
                }
            }
        });
    }

    private void disconnectVpn() {
        XrayCoreWrapper.stopVpn(this, new XrayCoreWrapper.XrayStatusListener() {
            @Override
            public void onStart() {}

            @Override
            public void onStop() {
                isConnected = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDisconnectedUi();
                    }
                });
            }

            @Override
            public void onError(String message) {}
        });
    }

    private void updateDisconnectedUi() {
        tvStatus.setText("ОТКЛЮЧЕНО");
        tvStatus.setTextColor(Color.parseColor("#FF5C5C"));
        btnConnect.setText("ПОДКЛЮЧИТЬСЯ");
        btnConnect.setBackgroundResource(R.drawable.bg_button_connect);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            connectVpn();
        } else {
            Toast.makeText(this, "Требуется разрешение на VPN!", Toast.LENGTH_SHORT).show();
            updateDisconnectedUi();
        }
    }
}
