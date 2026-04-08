package com.example.smartfarm.hidroponik;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import com.example.smartfarm.base.NotificationHelper;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Activity untuk sistem monitoring Hidroponik.
 * Extends BaseSmartFarmActivity untuk reuse MQTT logic.
 *
 * Monitoring:
 * - pH Air (ideal: 5.5 - 6.5)
 * - Nutrisi Air / TDS (ideal: 800 - 1500 PPM)
 *
 * Fitur notifikasi: mengirim peringatan ketika kondisi sensor abnormal.
 */
public class HidroponikActivity extends BaseSmartFarmActivity {

    private static final String TAG = "Hidroponik";

    private TextView tvNutrisiAir, tvPhAir, tvStatusPhAir, tvStatusNutrisiAir;
    private ProgressBar progressPhAir, progressNutrisiAir, progressVitaminA, progressVitaminB;
//    private MaterialSwitch switchNutrisiAir, switchLarutan;

    // Permission launcher untuk Android 13+
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                } else {
                    Log.w(TAG, "Notification permission denied");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        // Setup notifikasi
        NotificationHelper.createNotificationChannels(this);
        requestNotificationPermission();

        setupMQTT();
        initViews();
//        setupSwitchListeners();
    }

    @Override
    protected String getClientId() {
        return "AndroidSmartFarm_Hidroponik";
    }

    @Override
    protected String getSubscriptionTopic() {
        return "smartfarm/hidroponik/#";
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        switch (topic) {
            case "smartfarm/hidroponik/nutrisi_air":
                updateNutrisiAir(payload);
                break;
            case "smartfarm/hidroponik/larutan":
                updatePhAir(payload);
                break;
            default:
                Log.w(TAG, "Unknown topic: " + topic);
                break;
        }
    }

    /**
     * Meminta izin notification untuk Android 13 (API 33) ke atas.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void initViews() {
        tvPhAir = findViewById(R.id.tvPhAir);
        tvNutrisiAir = findViewById(R.id.tvNutrisiAir);
        progressPhAir = findViewById(R.id.progressPhAir);
        progressNutrisiAir = findViewById(R.id.progressNutrisiAir);
        tvStatusPhAir = findViewById(R.id.tvStatusPhAir);
        tvStatusNutrisiAir = findViewById(R.id.tvStatusNutrisiAir);

//        progressVitaminA = findViewById(R.id.progressVitaminA);
//        progressVitaminB = findViewById(R.id.progressVitaminB);
    }

//    private void setupSwitchListeners() {
//        switchNutrisiAir.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            publishMQTT("smartfarm/kontrol/nutrisi_air", isChecked ? "ON" : "OFF");
//        });
//
//        switchLarutan.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            publishMQTT("smartfarm/kontrol/larutan", isChecked ? "ON" : "OFF");
//        });
//    }


    private void updatePhAir(String payload) {
        try {
            float pH = Float.parseFloat(payload);
            tvPhAir.setText(String.valueOf(pH));
            progressPhAir.setProgress(Math.round(pH * 10));

            if (pH >= 5.5 && pH <= 6.5) {
                tvStatusPhAir.setText("Ideal");
                tvStatusPhAir.setTextColor(getColor(R.color.status_good));

                // Kondisi kembali normal, hapus notifikasi sebelumnya
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_AIR_ASAM);
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_AIR_BASA);

            } else if (pH < 5.5) {
                tvStatusPhAir.setText("Asam");
                tvStatusPhAir.setTextColor(getColor(R.color.status_danger));

                // Kirim notifikasi peringatan pH air asam
                NotificationHelper.sendWarningNotification(
                        this,
                        NotificationHelper.CHANNEL_HIDROPONIK,
                        NotificationHelper.NOTIF_PH_AIR_ASAM,
                        "⚠️ pH Air Hidroponik Asam!",
                        "pH air saat ini " + pH + " (di bawah 5.5). "
                                + "Kondisi terlalu asam, tambahkan larutan pH Up!",
                        HidroponikActivity.class
                );
            } else {
                tvStatusPhAir.setText("Basa");
                tvStatusPhAir.setTextColor(getColor(R.color.status_warning));

                // Kirim notifikasi peringatan pH air basa
                NotificationHelper.sendWarningNotification(
                        this,
                        NotificationHelper.CHANNEL_HIDROPONIK,
                        NotificationHelper.NOTIF_PH_AIR_BASA,
                        "⚠️ pH Air Hidroponik Basa!",
                        "pH air saat ini " + pH + " (di atas 6.5). "
                                + "Kondisi terlalu basa, tambahkan larutan pH Down!",
                        HidroponikActivity.class
                );
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid pH Air value: " + payload);
        }
    }

    private void updateNutrisiAir(String payload) {
        try {
            float nutrisi = Float.parseFloat(payload);
            tvNutrisiAir.setText(String.valueOf(nutrisi));
            progressNutrisiAir.setProgress(Math.round(nutrisi));

            if (nutrisi >= 800 && nutrisi <= 1500) {
                tvStatusNutrisiAir.setText("Ideal");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_good));

                // Kondisi kembali normal, hapus notifikasi sebelumnya
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_NUTRISI_KURANG);
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_NUTRISI_BERLEBIH);

            } else if (nutrisi < 800) {
                tvStatusNutrisiAir.setText("Kurang Nutrisi");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_danger));

                // Kirim notifikasi peringatan nutrisi kurang
                NotificationHelper.sendWarningNotification(
                        this,
                        NotificationHelper.CHANNEL_HIDROPONIK,
                        NotificationHelper.NOTIF_NUTRISI_KURANG,
                        "⚠️ Nutrisi Air Rendah!",
                        "Nutrisi air saat ini " + Math.round(nutrisi) + " PPM (di bawah 800 PPM). "
                                + "Kadar nutrisi terlalu rendah, tambahkan larutan nutrisi AB Mix!",
                        HidroponikActivity.class
                );
            } else {
                tvStatusNutrisiAir.setText("Berlebihan Nutrisi");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_warning));

                // Kirim notifikasi peringatan nutrisi berlebih
                NotificationHelper.sendWarningNotification(
                        this,
                        NotificationHelper.CHANNEL_HIDROPONIK,
                        NotificationHelper.NOTIF_NUTRISI_BERLEBIH,
                        "⚠️ Nutrisi Air Berlebihan!",
                        "Nutrisi air saat ini " + Math.round(nutrisi) + " PPM (di atas 1500 PPM). "
                                + "Kadar nutrisi terlalu tinggi, encerkan dengan menambahkan air bersih!",
                        HidroponikActivity.class
                );
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid Nutrisi value: " + payload);
        }
    }
}
