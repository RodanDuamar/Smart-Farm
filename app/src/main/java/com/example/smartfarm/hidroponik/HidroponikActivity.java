package com.example.smartfarm.hidroponik;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import com.example.smartfarm.base.FirebaseMonitorHelper;
import com.example.smartfarm.base.NotificationHelper; // Import helper Anda

import org.json.JSONObject;

import java.util.Locale;

public class HidroponikActivity extends BaseSmartFarmActivity {

    // --- CONSTANTS ---
    private static final String FIREBASE_COLLECTION = "sensor_hidroponik";

    // --- DEKLARASI VARIABEL ---
    private TextView tvTdsRealtime, tvPhRealtime, tvModeStatus, tvStatusTds, tvStatusPh;
    private TextView tvTdsMin, tvTdsMax;
    private ProgressBar progressTds, progressPh;
    private TankView tankVitaminA, tankVitaminB;
    private SwitchCompat switchPompaA, switchPompaB, switchPompaAir;
    private EditText etPpmTarget, etPpmTargetMax;
    private View btnUpdateParameter, cardKontrolPompa;

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchAuto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        initViews();
        setupMQTT();
        setupControlListeners();

        // Inisialisasi Notification Channels melalui Helper
        NotificationHelper.createNotificationChannels(this);
        checkNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestStatusUpdate();
    }

    private void requestStatusUpdate() {
        try {
            publishMQTT("nutrisi/request", "get_all_status");
            Log.d("MQTT_SYNC", "Meminta data parameter dan status ke hardware...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initViews() {
        tvTdsRealtime      = findViewById(R.id.tvTdsRealtime);
        tvPhRealtime       = findViewById(R.id.tvPhRealtime);
        tvModeStatus       = findViewById(R.id.tvModeStatus);
        tvStatusTds        = findViewById(R.id.tvStatusTds);
        tvStatusPh         = findViewById(R.id.tvStatusPh);
        tvTdsMin           = findViewById(R.id.tvTdsMin);
        tvTdsMax           = findViewById(R.id.tvTdsMax);
        progressTds        = findViewById(R.id.progressTds);
        progressPh         = findViewById(R.id.progressPh);
        cardKontrolPompa   = findViewById(R.id.cardKontrolPompa);
        switchPompaA       = findViewById(R.id.switchPompaA);
        switchPompaB       = findViewById(R.id.switchPompaB);
        switchPompaAir     = findViewById(R.id.switchPompaAir);
        etPpmTarget        = findViewById(R.id.etPpmTarget);
        etPpmTargetMax     = findViewById(R.id.etPpmTargetMax);
        btnUpdateParameter = findViewById(R.id.btnUpdateParameter);
        switchAuto         = findViewById(R.id.switchAuto);
        tankVitaminA       = findViewById(R.id.tankVitaminA);
        tankVitaminB       = findViewById(R.id.tankVitaminB);
    }

    private void setupControlListeners() {
        switchAuto.setOnCheckedChangeListener((v, isChecked) -> {
            if (v.isPressed()) {
                updateUIState(isChecked);
                publishCommand("manual", !isChecked);
            }
        });

        switchPompaA.setOnClickListener(v -> publishCommand("dosing1", switchPompaA.isChecked()));
        switchPompaB.setOnClickListener(v -> publishCommand("dosing2", switchPompaB.isChecked()));
        switchPompaAir.setOnClickListener(v -> publishCommand("water_pump", switchPompaAir.isChecked()));

        btnUpdateParameter.setOnClickListener(v -> {
            String min = etPpmTarget.getText().toString();
            String max = etPpmTargetMax.getText().toString();

            if (min.isEmpty() || max.isEmpty()) {
                Toast.makeText(this, "Isi semua parameter!", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int valMin = Integer.parseInt(min);
                int valMax = Integer.parseInt(max);

                JSONObject json = new JSONObject();
                json.put("min", valMin);
                json.put("max", valMax);

                publishMQTT("nutrisi/set/ppm", json.toString());

                tvTdsMin.setText(min);
                tvTdsMax.setText(max);

                Toast.makeText(this, "Parameter Nutrisi Diperbarui!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("UI_ERROR", "Error update parameter: " + e.getMessage());
            }
        });
    }

    private void publishCommand(String key, boolean state) {
        try {
            JSONObject json = new JSONObject();
            json.put(key, state);
            publishMQTT("nutrisi/control", json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUIState(boolean isAuto) {
        runOnUiThread(() -> {
            tvModeStatus.setText(isAuto ? "OTOMATIS" : "MANUAL");
            if (cardKontrolPompa != null) {
                cardKontrolPompa.setAlpha(isAuto ? 0.5f : 1.0f);
            }
            switchPompaA.setEnabled(!isAuto);
            switchPompaB.setEnabled(!isAuto);
            switchPompaAir.setEnabled(!isAuto);
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected String getBrokerUrl() {
        return "tcp://broker.hivemq.com:1883";
    }

    @Override
    protected String getClientId() {
        return "Android_Hidroponik_Sleman_" + System.currentTimeMillis();
    }

    @Override
    protected String[] getSubscriptionTopics() {
        return new String[]{
                "nutrisi/sensor",
                "nutrisi/status",
                "nutrisi/stock/vita",
                "nutrisi/stock/vitb",
                "nutrisi/warning"
        };
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        runOnUiThread(() -> {
            try {
                JSONObject json = new JSONObject(payload);

                // 1. Handling Sinkronisasi Min/Max
                if (json.has("min")) {
                    String minVal = String.valueOf(json.optInt("min"));
                    tvTdsMin.setText(minVal);
                    if (!etPpmTarget.isFocused()) etPpmTarget.setText(minVal);
                }
                if (json.has("max")) {
                    String maxVal = String.valueOf(json.optInt("max"));
                    tvTdsMax.setText(maxVal);
                    if (!etPpmTargetMax.isFocused()) etPpmTargetMax.setText(maxVal);
                }

                // 2. Handling Data Sensor & Menggunakan NotificationHelper
                if (json.has("ppm")) {
                    int ppm = json.optInt("ppm");
                    tvTdsRealtime.setText(String.valueOf(ppm));
                    progressTds.setProgress(ppm);

                    // Log ke Firebase Firestore
                    FirebaseMonitorHelper.getInstance()
                            .logSensorData(FIREBASE_COLLECTION, "ppm", ppm);

                    if (ppm < 800) {
                        tvStatusTds.setText("RENDAH");
                        NotificationHelper.sendWarningNotification(
                                this,
                                NotificationHelper.CHANNEL_HIDROPONIK,
                                NotificationHelper.NOTIF_NUTRISI_KURANG,
                                "Peringatan Nutrisi Hidroponik",
                                "Kadar PPM hidroponik terlalu rendah (" + ppm + " ppm)",
                                HidroponikActivity.class
                        );
                    } else if (ppm > 1200) {
                        tvStatusTds.setText("TINGGI");
                        NotificationHelper.sendWarningNotification(
                                this,
                                NotificationHelper.CHANNEL_HIDROPONIK,
                                NotificationHelper.NOTIF_NUTRISI_BERLEBIH,
                                "Peringatan Nutrisi Hidroponik",
                                "Kadar PPM hidroponik terlalu tinggi (" + ppm + " ppm)",
                                HidroponikActivity.class
                        );
                    } else {
                        tvStatusTds.setText("NORMAL");
                        // Batalkan notifikasi jika kondisi sudah kembali normal
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_NUTRISI_KURANG);
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_NUTRISI_BERLEBIH);
                    }
                }

                if (json.has("ph")) {
                    double ph = json.optDouble("ph");
                    tvPhRealtime.setText(String.format(Locale.getDefault(), "%.1f", ph));
                    progressPh.setProgress((int) (ph * 10));

                    // Log ke Firebase Firestore
                    FirebaseMonitorHelper.getInstance()
                            .logSensorData(FIREBASE_COLLECTION, "ph", ph);

                    if (ph < 5.5) {
                        tvStatusPh.setText("TERLALU ASAM");
                        NotificationHelper.sendWarningNotification(
                                this,
                                NotificationHelper.CHANNEL_HIDROPONIK,
                                NotificationHelper.NOTIF_PH_AIR_ASAM,
                                "Peringatan pH Hidroponik",
                                "Air terlalu asam (pH: " + String.format(Locale.getDefault(), "%.1f", ph) + ")",
                                HidroponikActivity.class
                        );
                    } else if (ph > 6.5) {
                        tvStatusPh.setText("TERLALU BASA");
                        NotificationHelper.sendWarningNotification(
                                this,
                                NotificationHelper.CHANNEL_HIDROPONIK,
                                NotificationHelper.NOTIF_PH_AIR_BASA,
                                "Peringatan pH Hidroponik",
                                "Air terlalu basa (pH: " + String.format(Locale.getDefault(), "%.1f", ph) + ")",
                                HidroponikActivity.class
                        );
                    } else {
                        tvStatusPh.setText("IDEAL");
                        // Batalkan notifikasi pH jika sudah ideal
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_AIR_ASAM);
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_AIR_BASA);
                    }
                }

                // 3. Sinkronisasi Status Saklar & Mode
                if (json.has("manual")) {
                    boolean isManual = json.optBoolean("manual");
                    switchAuto.setChecked(!isManual);
                    updateUIState(!isManual);
                }

                if (json.has("dosing1")) switchPompaA.setChecked(json.optBoolean("dosing1"));
                if (json.has("dosing2")) switchPompaB.setChecked(json.optBoolean("dosing2"));
                if (json.has("water_pump")) switchPompaAir.setChecked(json.optBoolean("water_pump"));

                // 4. Handling Kapasitas Tanki Vitamin A & B
                //    Dari topic nutrisi/sensor: key "vita_pct" dan "vitb_pct"
                //    Dari topic nutrisi/stock/vita atau nutrisi/stock/vitb: key "stock_pct"
                if (topic.equals("nutrisi/stock/vita") && json.has("stock_pct")) {
                    int vitaPct = (int) Math.round(json.optDouble("stock_pct", 0));
                    updateTankVitaminA(vitaPct);
                } else if (topic.equals("nutrisi/stock/vitb") && json.has("stock_pct")) {
                    int vitbPct = (int) Math.round(json.optDouble("stock_pct", 0));
                    updateTankVitaminB(vitbPct);
                } else {
                    // Dari topic nutrisi/sensor (data ringkasan)
                    if (json.has("vita_pct")) {
                        int vitaPct = (int) Math.round(json.optDouble("vita_pct", 0));
                        updateTankVitaminA(vitaPct);
                    }
                    if (json.has("vitb_pct")) {
                        int vitbPct = (int) Math.round(json.optDouble("vitb_pct", 0));
                        updateTankVitaminB(vitbPct);
                    }
                }

                // 5. Handling Warning: notifikasi stok vitamin kritis
                if (json.has("vita_critical") && json.optBoolean("vita_critical")) {
                    NotificationHelper.sendWarningNotification(
                            this,
                            NotificationHelper.CHANNEL_HIDROPONIK,
                            NotificationHelper.NOTIF_STOK_VITA_KRITIS,
                            "Stok Vitamin A Kritis!",
                            "Stok cairan Vitamin A hampir habis, segera isi ulang.",
                            HidroponikActivity.class
                    );
                }
                if (json.has("vitb_critical") && json.optBoolean("vitb_critical")) {
                    NotificationHelper.sendWarningNotification(
                            this,
                            NotificationHelper.CHANNEL_HIDROPONIK,
                            NotificationHelper.NOTIF_STOK_VITB_KRITIS,
                            "Stok Vitamin B Kritis!",
                            "Stok cairan Vitamin B hampir habis, segera isi ulang.",
                            HidroponikActivity.class
                    );
                }

            } catch (Exception e) {
                Log.e("MQTT_PARSE", "Gagal sinkronisasi: " + e.getMessage());
            }
        });
    }

    // --- Helper: update TankView Vitamin A ---
    private void updateTankVitaminA(int percent) {
        if (tankVitaminA != null) {
            tankVitaminA.setPercent(percent);
        }
        FirebaseMonitorHelper.getInstance()
                .logSensorData(FIREBASE_COLLECTION, "vita_pct", percent);
    }

    // --- Helper: update TankView Vitamin B ---
    private void updateTankVitaminB(int percent) {
        if (tankVitaminB != null) {
            tankVitaminB.setPercent(percent);
        }
        FirebaseMonitorHelper.getInstance()
                .logSensorData(FIREBASE_COLLECTION, "vitb_pct", percent);
    }
}