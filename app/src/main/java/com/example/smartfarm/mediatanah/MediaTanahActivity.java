package com.example.smartfarm.mediatanah;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import com.example.smartfarm.base.NotificationHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;

/**
 * Activity untuk monitoring dan kontrol sistem irigasi Media Tanah.
 * Menampilkan sensor kelembapan, pH tanah, valve kontrol, dan penjadwalan durasi.
 * Extends BaseSmartFarmActivity untuk reuse MQTT logic.
 *
 * Fitur:
 * - Monitoring sensor (kelembapan, pH)
 * - Notifikasi peringatan kondisi abnormal
 * - Penjadwalan durasi pompa & valve (pompa wajib ON saat valve dijadwalkan)
 */
public class MediaTanahActivity extends BaseSmartFarmActivity {

    private static final String TAG = "MediaTanah";

    // Sensor views
    private TextView tvKelembapan, tvPH, tvStatusKelembapan, tvStatusPH;
    private ProgressBar progressKelembapan, progressPH;

    // Switch views
    private MaterialSwitch switchPompa, switchValve1, switchValve2, switchValve3, switchValve4;
    private MaterialSwitch switchKranPembuangan, switchSumberDaya;

    // Timer buttons
    private ImageView btnTimerValve1, btnTimerValve2, btnTimerValve3, btnTimerValve4;

    // Countdown display per valve
    private TextView tvCountdownValve1, tvCountdownValve2, tvCountdownValve3, tvCountdownValve4;
    private TextView tvPompaStatus;

    // Jadwal Status Card views
    private MaterialCardView cardJadwalStatus;
    private LinearLayout layoutPompaTimerStatus, layoutValve1TimerStatus, layoutValve2TimerStatus;
    private LinearLayout layoutValve3TimerStatus, layoutValve4TimerStatus;
    private TextView tvPompaTimerStatus, tvValve1TimerStatus, tvValve2TimerStatus;
    private TextView tvValve3TimerStatus, tvValve4TimerStatus;
    private TextView btnStopAllTimers;

    // CountDownTimers per valve
    private CountDownTimer timerValve1, timerValve2, timerValve3, timerValve4;

    // Track which valves have active timers
    private boolean isTimerValve1Active = false;
    private boolean isTimerValve2Active = false;
    private boolean isTimerValve3Active = false;
    private boolean isTimerValve4Active = false;

    // Track if pump was turned on automatically by a valve timer
    private boolean pumpAutoEnabled = false;

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
        setContentView(R.layout.activity_media_tanah);

        // Setup notifikasi
        NotificationHelper.createNotificationChannels(this);
        requestNotificationPermission();

        setupMQTT();
        initViews();
        setupSwitchListeners();
        setupTimerButtons();
    }

    @Override
    protected String getClientId() {
        return "AndroidSmartFarm_MediaTanah";
    }

    @Override
    protected String getSubscriptionTopic() {
        return "smartfarm/sensor/#";
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        switch (topic) {
            case "smartfarm/sensor/kelembapan":
                updateKelembapan(payload);
                break;
            case "smartfarm/sensor/ph":
                updatePH(payload);
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
        // Sensor views
        tvKelembapan = findViewById(R.id.tvKelembapan);
        tvPH = findViewById(R.id.tvPH);
        tvStatusKelembapan = findViewById(R.id.tvStatusKelembapan);
        tvStatusPH = findViewById(R.id.tvStatusPH);
        progressKelembapan = findViewById(R.id.progressKelembapan);
        progressPH = findViewById(R.id.progressPH);

        // Switch views
        switchPompa = findViewById(R.id.switchPompa);
        switchValve1 = findViewById(R.id.switchValve1);
        switchValve2 = findViewById(R.id.switchValve2);
        switchValve3 = findViewById(R.id.switchValve3);
        switchValve4 = findViewById(R.id.switchValve4);
        switchKranPembuangan = findViewById(R.id.switchKranPembuangan);
        switchSumberDaya = findViewById(R.id.switchSumberDaya);

        // Timer buttons
        btnTimerValve1 = findViewById(R.id.btnTimerValve1);
        btnTimerValve2 = findViewById(R.id.btnTimerValve2);
        btnTimerValve3 = findViewById(R.id.btnTimerValve3);
        btnTimerValve4 = findViewById(R.id.btnTimerValve4);

        // Countdown displays
        tvCountdownValve1 = findViewById(R.id.tvCountdownValve1);
        tvCountdownValve2 = findViewById(R.id.tvCountdownValve2);
        tvCountdownValve3 = findViewById(R.id.tvCountdownValve3);
        tvCountdownValve4 = findViewById(R.id.tvCountdownValve4);
        tvPompaStatus = findViewById(R.id.tvPompaStatus);

        // Jadwal status card
        cardJadwalStatus = findViewById(R.id.cardJadwalStatus);
        layoutPompaTimerStatus = findViewById(R.id.layoutPompaTimerStatus);
        layoutValve1TimerStatus = findViewById(R.id.layoutValve1TimerStatus);
        layoutValve2TimerStatus = findViewById(R.id.layoutValve2TimerStatus);
        layoutValve3TimerStatus = findViewById(R.id.layoutValve3TimerStatus);
        layoutValve4TimerStatus = findViewById(R.id.layoutValve4TimerStatus);
        tvPompaTimerStatus = findViewById(R.id.tvPompaTimerStatus);
        tvValve1TimerStatus = findViewById(R.id.tvValve1TimerStatus);
        tvValve2TimerStatus = findViewById(R.id.tvValve2TimerStatus);
        tvValve3TimerStatus = findViewById(R.id.tvValve3TimerStatus);
        tvValve4TimerStatus = findViewById(R.id.tvValve4TimerStatus);
        btnStopAllTimers = findViewById(R.id.btnStopAllTimers);
    }

    private void setupSwitchListeners() {
        switchPompa.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Jika pompa dimatikan manual tapi ada valve timer aktif, cegah
            if (!isChecked && hasActiveValveTimers()) {
                switchPompa.setChecked(true);
                Toast.makeText(this,
                        "⚠ Pompa tidak bisa dimatikan saat valve dijadwalkan",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            publishMQTT("smartfarm/kontrol/kran_air", isChecked ? "ON" : "OFF");
            if (!isChecked) {
                pumpAutoEnabled = false;
                tvPompaStatus.setText("Manual");
                tvPompaStatus.setTextColor(getColor(R.color.text_hint));
            }
        });

        switchValve1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_insektisida", isChecked ? "ON" : "OFF");
        });

        switchValve2.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_pupuk", isChecked ? "ON" : "OFF");
        });

        switchValve3.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/valve3", isChecked ? "ON" : "OFF");
        });

        switchValve4.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/valve4", isChecked ? "ON" : "OFF");
        });

        switchKranPembuangan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_pembuangan", isChecked ? "ON" : "OFF");
        });

        // Switch Sumber Daya: OFF (Left) = Listrik Rumah, ON (Right) = Panel Surya
        switchSumberDaya.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String source = isChecked ? "SOLAR" : "PLN";
            publishMQTT("smartfarm/kontrol/sumber_daya", source);
        });

        // Stop all timers button
        btnStopAllTimers.setOnClickListener(v -> stopAllTimers());
    }

    // ==================== TIMER / SCHEDULING LOGIC ====================

    private void setupTimerButtons() {
        btnTimerValve1.setOnClickListener(v -> showDurationDialog("Valve 1", 1));
        btnTimerValve2.setOnClickListener(v -> showDurationDialog("Valve 2", 2));
        btnTimerValve3.setOnClickListener(v -> showDurationDialog("Valve 3", 3));
        btnTimerValve4.setOnClickListener(v -> showDurationDialog("Valve 4", 4));
    }

    /**
     * Menampilkan dialog untuk mengatur durasi valve.
     *
     * @param valveName Nama valve (untuk display)
     * @param valveIndex Index valve (1-4)
     */
    private void showDurationDialog(String valveName, int valveIndex) {
        // Cek apakah valve ini sudah punya timer aktif
        if (isValveTimerActive(valveIndex)) {
            // Tampilkan dialog konfirmasi untuk menghentikan timer
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Timer " + valveName + " Aktif")
                    .setMessage("Timer sedang berjalan. Hentikan timer?")
                    .setPositiveButton("Hentikan", (dialog, which) -> {
                        stopValveTimer(valveIndex);
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_duration, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        NumberPicker npMenit = dialogView.findViewById(R.id.npMenit);
        NumberPicker npDetik = dialogView.findViewById(R.id.npDetik);

        tvTitle.setText("Atur Durasi " + valveName);
        tvSubtitle.setText("Pompa akan otomatis menyala bersama " + valveName);

        // Setup NumberPickers
        npMenit.setMinValue(0);
        npMenit.setMaxValue(60);
        npMenit.setValue(5); // Default 5 menit

        npDetik.setMinValue(0);
        npDetik.setMaxValue(59);
        npDetik.setValue(0);

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Mulai", (dialog, which) -> {
                    int menit = npMenit.getValue();
                    int detik = npDetik.getValue();
                    long totalMs = (menit * 60L + detik) * 1000L;

                    if (totalMs <= 0) {
                        Toast.makeText(this, "Durasi harus lebih dari 0", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    startValveTimer(valveIndex, totalMs);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    /**
     * Memulai timer untuk valve tertentu.
     * Pompa WAJIB menyala ketika valve dijadwalkan.
     */
    private void startValveTimer(int valveIndex, long durationMs) {
        // 1. Nyalakan pompa otomatis jika belum ON
        ensurePumpOn();

        // 2. Nyalakan valve
        setValveSwitch(valveIndex, true);

        // 3. Mulai countdown
        CountDownTimer timer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                String timeStr = formatTime(millisUntilFinished);
                updateValveCountdown(valveIndex, "⏱ Sisa: " + timeStr, true);
                updateTimerStatusCard(valveIndex, timeStr);
            }

            @Override
            public void onFinish() {
                // Matikan valve
                setValveSwitch(valveIndex, false);
                setValveTimerActive(valveIndex, false);
                updateValveCountdown(valveIndex, "✅ Selesai", false);
                hideTimerStatusRow(valveIndex);

                // Cek apakah masih ada valve timer lain yang aktif
                if (!hasActiveValveTimers()) {
                    // Semua timer selesai, matikan pompa (jika auto-enabled)
                    turnOffPumpAuto();
                    cardJadwalStatus.setVisibility(View.GONE);
                }

                Toast.makeText(MediaTanahActivity.this,
                        "Timer " + getValveName(valveIndex) + " selesai",
                        Toast.LENGTH_SHORT).show();

                // Kirim notifikasi
                NotificationHelper.sendWarningNotification(
                        MediaTanahActivity.this,
                        NotificationHelper.CHANNEL_MEDIA_TANAH,
                        3000 + valveIndex,
                        "✅ Timer " + getValveName(valveIndex) + " Selesai",
                        getValveName(valveIndex) + " telah dimatikan otomatis setelah durasi selesai.",
                        MediaTanahActivity.class
                );
            }
        };

        // Simpan reference timer
        setValveTimer(valveIndex, timer);
        setValveTimerActive(valveIndex, true);
        timer.start();

        // Update UI
        showTimerStatusCard(valveIndex);

        Toast.makeText(this,
                "Timer " + getValveName(valveIndex) + " dimulai: " + formatTime(durationMs),
                Toast.LENGTH_SHORT).show();

        Log.d(TAG, "Timer started: " + getValveName(valveIndex) + " for " + formatTime(durationMs));
    }

    /**
     * Memastikan pompa ON. Jika belum ON, nyalakan otomatis.
     */
    private void ensurePumpOn() {
        if (!switchPompa.isChecked()) {
            pumpAutoEnabled = true;
            switchPompa.setChecked(true);
            tvPompaStatus.setText("Otomatis (valve aktif)");
            tvPompaStatus.setTextColor(getColor(R.color.status_info));
        } else if (!pumpAutoEnabled) {
            // Pompa sudah ON manual, tandai bahwa ada valve timer
            tvPompaStatus.setText("Manual + Valve aktif");
            tvPompaStatus.setTextColor(getColor(R.color.status_info));
        }
    }

    /**
     * Matikan pompa otomatis saat semua valve timer selesai.
     * Hanya matikan jika pompa dinyalakan secara otomatis.
     */
    private void turnOffPumpAuto() {
        if (pumpAutoEnabled) {
            pumpAutoEnabled = false;
            switchPompa.setChecked(false);
            tvPompaStatus.setText("Manual");
            tvPompaStatus.setTextColor(getColor(R.color.text_hint));
            Log.d(TAG, "Pump auto-off: all valve timers finished");
        } else {
            tvPompaStatus.setText("Manual");
            tvPompaStatus.setTextColor(getColor(R.color.text_hint));
        }
    }

    /**
     * Hentikan timer untuk valve tertentu.
     */
    private void stopValveTimer(int valveIndex) {
        CountDownTimer timer = getValveTimer(valveIndex);
        if (timer != null) {
            timer.cancel();
        }
        setValveSwitch(valveIndex, false);
        setValveTimerActive(valveIndex, false);
        setValveTimer(valveIndex, null);
        updateValveCountdown(valveIndex, "Tidak dijadwalkan", false);
        hideTimerStatusRow(valveIndex);

        // Cek apakah masih ada valve timer lain yang aktif
        if (!hasActiveValveTimers()) {
            turnOffPumpAuto();
            cardJadwalStatus.setVisibility(View.GONE);
        }

        Toast.makeText(this, "Timer " + getValveName(valveIndex) + " dihentikan",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Hentikan semua timer yang aktif.
     */
    private void stopAllTimers() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Hentikan Semua Timer")
                .setMessage("Yakin ingin menghentikan semua timer aktif? Semua valve dan pompa akan dimatikan.")
                .setPositiveButton("Hentikan Semua", (dialog, which) -> {
                    for (int i = 1; i <= 4; i++) {
                        if (isValveTimerActive(i)) {
                            CountDownTimer timer = getValveTimer(i);
                            if (timer != null) timer.cancel();
                            setValveSwitch(i, false);
                            setValveTimerActive(i, false);
                            setValveTimer(i, null);
                            updateValveCountdown(i, "Tidak dijadwalkan", false);
                        }
                    }
                    turnOffPumpAuto();
                    cardJadwalStatus.setVisibility(View.GONE);
                    Toast.makeText(this, "Semua timer dihentikan", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ==================== HELPER METHODS ====================

    private boolean hasActiveValveTimers() {
        return isTimerValve1Active || isTimerValve2Active || isTimerValve3Active || isTimerValve4Active;
    }

    private boolean isValveTimerActive(int index) {
        switch (index) {
            case 1: return isTimerValve1Active;
            case 2: return isTimerValve2Active;
            case 3: return isTimerValve3Active;
            case 4: return isTimerValve4Active;
            default: return false;
        }
    }

    private void setValveTimerActive(int index, boolean active) {
        switch (index) {
            case 1: isTimerValve1Active = active; break;
            case 2: isTimerValve2Active = active; break;
            case 3: isTimerValve3Active = active; break;
            case 4: isTimerValve4Active = active; break;
        }
    }

    private CountDownTimer getValveTimer(int index) {
        switch (index) {
            case 1: return timerValve1;
            case 2: return timerValve2;
            case 3: return timerValve3;
            case 4: return timerValve4;
            default: return null;
        }
    }

    private void setValveTimer(int index, CountDownTimer timer) {
        switch (index) {
            case 1: timerValve1 = timer; break;
            case 2: timerValve2 = timer; break;
            case 3: timerValve3 = timer; break;
            case 4: timerValve4 = timer; break;
        }
    }

    private void setValveSwitch(int index, boolean checked) {
        switch (index) {
            case 1: switchValve1.setChecked(checked); break;
            case 2: switchValve2.setChecked(checked); break;
            case 3: switchValve3.setChecked(checked); break;
            case 4: switchValve4.setChecked(checked); break;
        }
    }

    private String getValveName(int index) {
        return "Valve " + index;
    }

    private void updateValveCountdown(int index, String text, boolean isActive) {
        TextView tv;
        switch (index) {
            case 1: tv = tvCountdownValve1; break;
            case 2: tv = tvCountdownValve2; break;
            case 3: tv = tvCountdownValve3; break;
            case 4: tv = tvCountdownValve4; break;
            default: return;
        }
        tv.setText(text);
        if (isActive) {
            tv.setTextColor(getColor(R.color.status_info));
        } else if (text.contains("Selesai")) {
            tv.setTextColor(getColor(R.color.status_good));
        } else {
            tv.setTextColor(getColor(R.color.text_hint));
        }
    }

    private void showTimerStatusCard(int valveIndex) {
        cardJadwalStatus.setVisibility(View.VISIBLE);
        layoutPompaTimerStatus.setVisibility(View.VISIBLE);
        tvPompaTimerStatus.setText("Aktif (otomatis)");

        switch (valveIndex) {
            case 1: layoutValve1TimerStatus.setVisibility(View.VISIBLE); break;
            case 2: layoutValve2TimerStatus.setVisibility(View.VISIBLE); break;
            case 3: layoutValve3TimerStatus.setVisibility(View.VISIBLE); break;
            case 4: layoutValve4TimerStatus.setVisibility(View.VISIBLE); break;
        }
    }

    private void updateTimerStatusCard(int valveIndex, String timeStr) {
        switch (valveIndex) {
            case 1: tvValve1TimerStatus.setText(timeStr); break;
            case 2: tvValve2TimerStatus.setText(timeStr); break;
            case 3: tvValve3TimerStatus.setText(timeStr); break;
            case 4: tvValve4TimerStatus.setText(timeStr); break;
        }
    }

    private void hideTimerStatusRow(int valveIndex) {
        switch (valveIndex) {
            case 1: layoutValve1TimerStatus.setVisibility(View.GONE); break;
            case 2: layoutValve2TimerStatus.setVisibility(View.GONE); break;
            case 3: layoutValve3TimerStatus.setVisibility(View.GONE); break;
            case 4: layoutValve4TimerStatus.setVisibility(View.GONE); break;
        }
        // Jika tidak ada valve yang aktif, sembunyikan pompa status juga
        if (!hasActiveValveTimers()) {
            layoutPompaTimerStatus.setVisibility(View.GONE);
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        // Cancel semua timer yang aktif
        if (timerValve1 != null) timerValve1.cancel();
        if (timerValve2 != null) timerValve2.cancel();
        if (timerValve3 != null) timerValve3.cancel();
        if (timerValve4 != null) timerValve4.cancel();
        super.onDestroy();
    }

    // ==================== SENSOR MONITORING ====================

    private void updateKelembapan(String value) {
        try {
            float kelembapan = Float.parseFloat(value);
            int kelInt = Math.round(kelembapan);
            tvKelembapan.setText(String.valueOf(kelInt));
            progressKelembapan.setProgress(kelInt);

            if (kelembapan >= 40 && kelembapan <= 80) {
                tvStatusKelembapan.setText("Ideal");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_good));
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_KELEMBAPAN_RENDAH);
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_KELEMBAPAN_TINGGI);
            } else if (kelembapan < 40) {
                tvStatusKelembapan.setText("Kering");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_danger));
                NotificationHelper.sendWarningNotification(
                        this, NotificationHelper.CHANNEL_MEDIA_TANAH,
                        NotificationHelper.NOTIF_KELEMBAPAN_RENDAH,
                        "⚠️ Kelembapan Tanah Rendah!",
                        "Kelembapan tanah saat ini " + kelInt + "% (di bawah 40%). "
                                + "Tanah terlalu kering, segera lakukan penyiraman!",
                        MediaTanahActivity.class
                );
            } else {
                tvStatusKelembapan.setText("Terlalu Basah");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_warning));
                NotificationHelper.sendWarningNotification(
                        this, NotificationHelper.CHANNEL_MEDIA_TANAH,
                        NotificationHelper.NOTIF_KELEMBAPAN_TINGGI,
                        "⚠️ Kelembapan Tanah Tinggi!",
                        "Kelembapan tanah saat ini " + kelInt + "% (di atas 80%). "
                                + "Tanah terlalu basah, kurangi penyiraman!",
                        MediaTanahActivity.class
                );
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid kelembapan value: " + value);
        }
    }

    private void updatePH(String value) {
        try {
            float ph = Float.parseFloat(value);
            tvPH.setText(String.valueOf(ph));
            progressPH.setProgress(Math.round(ph * 10));

            if (ph >= 5.5 && ph <= 7.5) {
                tvStatusPH.setText("Ideal");
                tvStatusPH.setTextColor(getColor(R.color.status_good));
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_TANAH_ASAM);
                NotificationHelper.cancelNotification(this, NotificationHelper.NOTIF_PH_TANAH_BASA);
            } else if (ph < 5.5) {
                tvStatusPH.setText("Asam");
                tvStatusPH.setTextColor(getColor(R.color.status_danger));
                NotificationHelper.sendWarningNotification(
                        this, NotificationHelper.CHANNEL_MEDIA_TANAH,
                        NotificationHelper.NOTIF_PH_TANAH_ASAM,
                        "⚠️ pH Tanah Terlalu Asam!",
                        "pH tanah saat ini " + ph + " (di bawah 5.5). "
                                + "Kondisi terlalu asam, pertimbangkan menambahkan kapur!",
                        MediaTanahActivity.class
                );
            } else {
                tvStatusPH.setText("Basa");
                tvStatusPH.setTextColor(getColor(R.color.status_warning));
                NotificationHelper.sendWarningNotification(
                        this, NotificationHelper.CHANNEL_MEDIA_TANAH,
                        NotificationHelper.NOTIF_PH_TANAH_BASA,
                        "⚠️ pH Tanah Terlalu Basa!",
                        "pH tanah saat ini " + ph + " (di atas 7.5). "
                                + "Kondisi terlalu basa, pertimbangkan menambahkan pupuk organik!",
                        MediaTanahActivity.class
                );
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid pH value: " + value);
        }
    }
}
