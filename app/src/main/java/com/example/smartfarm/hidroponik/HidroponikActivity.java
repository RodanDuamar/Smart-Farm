package com.example.smartfarm.hidroponik;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import com.example.smartfarm.base.FirebaseMonitorHelper;
import com.example.smartfarm.base.NotificationHelper;

import org.json.JSONObject;

import java.util.Locale;

public class HidroponikActivity extends BaseSmartFarmActivity {

    // --- SINKRONISASI FIRESTORE & LOGGING ---
    private static final String FIREBASE_COLLECTION = "sensor_hidroponik";

    // --- DEKLARASI UI COMPONENTS ---
    private TextView tvTdsRealtime, tvPhRealtime, tvModeStatus, tvStatusTds, tvStatusPh;
    private TextView tvTdsMin, tvTdsMax;
    private ProgressBar progressTds, progressPh;
    private SwitchCompat switchPompaA, switchPompaB, switchPompaAir, switchAuto;
    private EditText etPpmTarget, etPpmTargetMax;
    private View btnUpdateParameter, cardKontrolPompa;

    // FIX ID: Komponen Custom View & TextView Pendukung sesuai XML asli Anda
    private TankIndicatorView tankNutrisiUtama, tankVitaminA, tankVitaminB;
    private TextView tvTankNutrisiUtamaPercent, tvTankNutrisiUtamaStatus, tvTankNutrisiVolume;
    private TextView tvTankAStatus, tvTankBStatus;

    // --- WARNING NOTIFICATION BANNER ---
    private View cardWarningBanner;
    private View warningVitaminA, warningVitaminB, warningTangkiKosong, warningPompaTidakAktif;
    private TextView tvWarningVitaminA, tvWarningVitaminB, tvWarningTangkiKosong, tvWarningPompa;
    private View btnDismissWarning;
    private boolean warningDismissedByUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        initViews();
        setupMQTT();
        setupControlListeners();

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
            Log.d("MQTT_SYNC", "Meminta sinkronisasi data awal ke ESP32...");
        } catch (Exception e) {
            Log.e("MQTT_SYNC", "Gagal meminta status: " + e.getMessage());
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

        // FIX SINKRONISASI ID XML: Menghubungkan variabel dengan ID asli di XML
        tankVitaminA              = findViewById(R.id.tankVitaminA);
        tankVitaminB              = findViewById(R.id.tankVitaminB);
        tankNutrisiUtama          = findViewById(R.id.tankNutrisiUtama);

        tvTankAStatus             = findViewById(R.id.tvTankAStatus);
        tvTankBStatus             = findViewById(R.id.tvTankBStatus);
        tvTankNutrisiUtamaPercent = findViewById(R.id.tvTankNutrisiUtamaPercent);
        tvTankNutrisiUtamaStatus  = findViewById(R.id.tvTankNutrisiUtamaStatus);
        tvTankNutrisiVolume       = findViewById(R.id.tvTankNutrisiVolume);

        // Warning Banner Components
        cardWarningBanner      = findViewById(R.id.cardWarningBanner);
        warningVitaminA        = findViewById(R.id.warningVitaminA);
        warningVitaminB        = findViewById(R.id.warningVitaminB);
        warningTangkiKosong    = findViewById(R.id.warningTangkiKosong);
        warningPompaTidakAktif = findViewById(R.id.warningPompaTidakAktif);
        tvWarningVitaminA      = findViewById(R.id.tvWarningVitaminA);
        tvWarningVitaminB      = findViewById(R.id.tvWarningVitaminB);
        tvWarningTangkiKosong  = findViewById(R.id.tvWarningTangkiKosong);
        tvWarningPompa         = findViewById(R.id.tvWarningPompa);
        btnDismissWarning      = findViewById(R.id.btnDismissWarning);

        // Dismiss button listener
        if (btnDismissWarning != null) {
            btnDismissWarning.setOnClickListener(v -> {
                warningDismissedByUser = true;
                if (cardWarningBanner != null) cardWarningBanner.setVisibility(View.GONE);
            });
        }
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
        return "Android_Hidroponik_Client_" + System.currentTimeMillis();
    }

    @Override
    protected String[] getSubscriptionTopics() {
        return new String[]{
                "nutrisi/sensor",
                "nutrisi/status",
                "nutrisi/stock/nut",
                "nutrisi/stock/vita",
                "nutrisi/stock/vitb"
        };
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        runOnUiThread(() -> {
            try {
                Log.d("MQTT_DATA", "Topic: " + topic + " | Payload: " + payload);
                JSONObject json = new JSONObject(payload);

                // ========================================================
                // 1. PARSING KAPASITAS TANGKI BERDASARKAN TOPIK ESP32
                // ========================================================
                if (topic.contains("nutrisi/stock/")) {
                    if (json.has("stock_pct")) {
                        int stockPercent = json.optInt("stock_pct", 0);

                        // Percabangan disesuaikan dengan ID komponen layout XML asli Anda
                        if (topic.equals("nutrisi/stock/nut")) {
                            if (tankNutrisiUtama != null) tankNutrisiUtama.setPercentageAnimated(stockPercent, 800);
                            if (tvTankNutrisiUtamaPercent != null) tvTankNutrisiUtamaPercent.setText(stockPercent + " %");

                            // Perbarui teks status kondisi di kolom info kiri tangki utama
                            if (tvTankNutrisiUtamaStatus != null) {
                                if (stockPercent <= 10) tvTankNutrisiUtamaStatus.setText("KRITIS");
                                else if (stockPercent <= 20) tvTankNutrisiUtamaStatus.setText("RENDAH");
                                else tvTankNutrisiUtamaStatus.setText("NORMAL");
                            }

                            // Jika firmware ESP32 mengirim tinggi air aktual (level_cm)
                            if (json.has("level_cm") && tvTankNutrisiVolume != null) {
                                double cm = json.optDouble("level_cm", 0.0);
                                tvTankNutrisiVolume.setText(String.format(Locale.getDefault(), "%.1f cm", cm));
                            }

                            // === WARNING: Tangki Nutrisi Utama Kosong (<=10%) ===
                            if (stockPercent <= 10) {
                                showWarningItem(warningTangkiKosong, true);
                                if (tvWarningTangkiKosong != null) {
                                    tvWarningTangkiKosong.setText("Tangki nutrisi utama kosong! (" + stockPercent + "%)");
                                }
                                NotificationHelper.sendWarningNotification(this,
                                        NotificationHelper.CHANNEL_HIDROPONIK,
                                        NotificationHelper.NOTIF_TANGKI_NUTRISI_KOSONG,
                                        "⚠ Tangki Nutrisi Kosong",
                                        "Tangki nutrisi utama hampir kosong! Level: " + stockPercent + "%",
                                        HidroponikActivity.class);
                            } else {
                                showWarningItem(warningTangkiKosong, false);
                                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_TANGKI_NUTRISI_KOSONG);
                            }

                        } else if (topic.equals("nutrisi/stock/vita")) {
                            if (tankVitaminA != null) tankVitaminA.setPercentageAnimated(stockPercent, 800);
                            if (tvTankAStatus != null) tvTankAStatus.setText(stockPercent + " %");

                            // === WARNING: Stok Vitamin A Hampir Habis (<=20%) ===
                            if (stockPercent <= 20) {
                                showWarningItem(warningVitaminA, true);
                                if (tvWarningVitaminA != null) {
                                    tvWarningVitaminA.setText("Stok Vitamin A hampir habis! (" + stockPercent + "%)");
                                }
                                NotificationHelper.sendWarningNotification(this,
                                        NotificationHelper.CHANNEL_HIDROPONIK,
                                        NotificationHelper.NOTIF_STOK_VITAMIN_A_RENDAH,
                                        "⚠ Stok Vitamin A Rendah",
                                        "Stok Vitamin A hampir habis! Level: " + stockPercent + "%",
                                        HidroponikActivity.class);
                            } else {
                                showWarningItem(warningVitaminA, false);
                                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_STOK_VITAMIN_A_RENDAH);
                            }

                        } else if (topic.equals("nutrisi/stock/vitb")) {
                            if (tankVitaminB != null) tankVitaminB.setPercentageAnimated(stockPercent, 800);
                            if (tvTankBStatus != null) tvTankBStatus.setText(stockPercent + " %");

                            // === WARNING: Stok Vitamin B Hampir Habis (<=20%) ===
                            if (stockPercent <= 20) {
                                showWarningItem(warningVitaminB, true);
                                if (tvWarningVitaminB != null) {
                                    tvWarningVitaminB.setText("Stok Vitamin B hampir habis! (" + stockPercent + "%)");
                                }
                                NotificationHelper.sendWarningNotification(this,
                                        NotificationHelper.CHANNEL_HIDROPONIK,
                                        NotificationHelper.NOTIF_STOK_VITAMIN_B_RENDAH,
                                        "⚠ Stok Vitamin B Rendah",
                                        "Stok Vitamin B hampir habis! Level: " + stockPercent + "%",
                                        HidroponikActivity.class);
                            } else {
                                showWarningItem(warningVitaminB, false);
                                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_STOK_VITAMIN_B_RENDAH);
                            }
                        }
                    }
                    return; // Mengakhiri eksekusi karena ini pesan data tangki
                }

                // ========================================================
                // 2. PARSING DATA SENSOR UTAMA & LOGIKANYA
                // ========================================================

                // Sinkronisasi Batas Target (PPM Min/Max)
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

                // Parsing Data Sensor TDS (PPM)
                if (json.has("ppm")) {
                    int ppm = json.optInt("ppm");
                    tvTdsRealtime.setText(String.valueOf(ppm));
                    progressTds.setProgress(ppm);

                    FirebaseMonitorHelper.getInstance().logSensorData(FIREBASE_COLLECTION, "ppm", ppm);

                    if (ppm < 800) {
                        tvStatusTds.setText("RENDAH");
                        NotificationHelper.sendWarningNotification(this, NotificationHelper.CHANNEL_HIDROPONIK, NotificationHelper.NOTIF_NUTRISI_KURANG, "Peringatan Nutrisi", "Kadar PPM terlalu rendah: " + ppm, HidroponikActivity.class);
                    } else if (ppm > 1200) {
                        tvStatusTds.setText("TINGGI");
                        NotificationHelper.sendWarningNotification(this, NotificationHelper.CHANNEL_HIDROPONIK, NotificationHelper.NOTIF_NUTRISI_BERLEBIH, "Peringatan Nutrisi", "Kadar PPM terlalu tinggi: " + ppm, HidroponikActivity.class);
                    } else {
                        tvStatusTds.setText("NORMAL");
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_NUTRISI_KURANG);
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_NUTRISI_BERLEBIH);
                    }
                }

                // Parsing Data Sensor pH
                if (json.has("ph")) {
                    double ph = json.optDouble("ph");
                    tvPhRealtime.setText(String.format(Locale.getDefault(), "%.1f", ph));
                    progressPh.setProgress((int) (ph * 10));

                    FirebaseMonitorHelper.getInstance().logSensorData(FIREBASE_COLLECTION, "ph", ph);

                    if (ph < 5.5) {
                        tvStatusPh.setText("ASAM");
                        NotificationHelper.sendWarningNotification(this, NotificationHelper.CHANNEL_HIDROPONIK, NotificationHelper.NOTIF_PH_AIR_ASAM, "Peringatan pH", "Air terlalu asam: " + ph, HidroponikActivity.class);
                    } else if (ph > 6.5) {
                        tvStatusPh.setText("BASA");
                        NotificationHelper.sendWarningNotification(this, NotificationHelper.CHANNEL_HIDROPONIK, NotificationHelper.NOTIF_PH_AIR_BASA, "Peringatan pH", "Air terlalu basa: " + ph, HidroponikActivity.class);
                    } else {
                        tvStatusPh.setText("IDEAL");
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_AIR_ASAM);
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_AIR_BASA);
                    }
                }

                // Sinkronisasi Saklar Realtime
                if (json.has("manual")) {
                    boolean isManual = json.optBoolean("manual");
                    switchAuto.setChecked(!isManual);
                    updateUIState(!isManual);
                }

                if (json.has("dosing1")) switchPompaA.setChecked(json.optBoolean("dosing1"));
                if (json.has("dosing2")) switchPompaB.setChecked(json.optBoolean("dosing2"));
                if (json.has("water_pump")) {
                    boolean pumpActive = json.optBoolean("water_pump");
                    switchPompaAir.setChecked(pumpActive);

                    // === WARNING: Pompa Utama Tidak Aktif ===
                    // Hanya tampilkan warning jika dalam mode OTOMATIS dan pompa mati
                    boolean isAutoMode = switchAuto.isChecked();
                    if (!pumpActive && isAutoMode) {
                        showWarningItem(warningPompaTidakAktif, true);
                        if (tvWarningPompa != null) {
                            tvWarningPompa.setText("Pompa air utama tidak aktif dalam mode otomatis!");
                        }
                        NotificationHelper.sendWarningNotification(this,
                                NotificationHelper.CHANNEL_HIDROPONIK,
                                NotificationHelper.NOTIF_POMPA_TIDAK_AKTIF,
                                "⚠ Pompa Tidak Aktif",
                                "Pompa air utama tidak aktif saat mode otomatis!",
                                HidroponikActivity.class);
                    } else {
                        showWarningItem(warningPompaTidakAktif, false);
                        NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_POMPA_TIDAK_AKTIF);
                    }
                }

            } catch (Exception e) {
                Log.e("MQTT_PARSE_ERROR", "Gagal membaca struktur data dari alat: " + e.getMessage());
            }
        });
    }

    // ========================================================
    // WARNING BANNER HELPER METHODS
    // ========================================================

    /**
     * Menampilkan atau menyembunyikan item warning individual,
     * lalu memperbarui visibilitas card banner utama.
     */
    private void showWarningItem(View warningItem, boolean show) {
        if (warningItem != null) {
            warningItem.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        // Jika ada warning baru yang muncul, reset dismiss state agar banner tampil lagi
        if (show) {
            warningDismissedByUser = false;
        }
        updateWarningBannerVisibility();
    }

    /**
     * Memperbarui visibilitas card warning banner utama.
     * Banner tampil jika ada minimal satu warning item yang visible
     * DAN belum di-dismiss oleh user.
     */
    private void updateWarningBannerVisibility() {
        if (cardWarningBanner == null) return;

        boolean hasAnyWarning =
                (warningVitaminA != null && warningVitaminA.getVisibility() == View.VISIBLE) ||
                (warningVitaminB != null && warningVitaminB.getVisibility() == View.VISIBLE) ||
                (warningTangkiKosong != null && warningTangkiKosong.getVisibility() == View.VISIBLE) ||
                (warningPompaTidakAktif != null && warningPompaTidakAktif.getVisibility() == View.VISIBLE);

        if (hasAnyWarning && !warningDismissedByUser) {
            cardWarningBanner.setVisibility(View.VISIBLE);
        } else if (!hasAnyWarning) {
            cardWarningBanner.setVisibility(View.GONE);
            warningDismissedByUser = false; // Reset dismiss state ketika semua warning hilang
        }
    }
}