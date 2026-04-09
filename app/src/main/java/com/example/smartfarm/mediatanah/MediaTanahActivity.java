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
    private MaterialSwitch switchPompa, switchKranAir, switchKranInsek, switchKranPupuk, switchKranBuang;
    private MaterialSwitch switchSumberDaya;

    // Timer buttons
    private ImageView btnTimerKranAir, btnTimerKranInsek, btnTimerKranPupuk, btnTimerKranBuang;

    // Countdown display per valve
    private TextView tvCountdownKranAir, tvCountdownKranInsek, tvCountdownKranPupuk, tvCountdownKranBuang;
    private TextView tvPompaStatus;

    // Jadwal Status Card views
    private MaterialCardView cardJadwalStatus;
    private LinearLayout
            layoutPompaTimerStatus,
            layoutKranAirTimerStatus,
            layoutKranInsekTimerStatus,
            layoutKranPupukTimerStatus,
            layoutKranBuangTimerStatus;;
    private TextView
            tvPompaTimerStatus,
            tvKranAirTimerStatus,
            tvKranInsekTimerStatus,
            tvKranPupukTimerStatus,
            tvKranBuangTimerStatus;
    private TextView btnStopAllTimers;

    // CountDownTimers per valve
    private CountDownTimer timerKranAir, timerKranInsek, timerKranPupuk, timerKranBuang;

    // Track which valves have active timers
    private boolean isKranAirTimerActive = false;
    private boolean isKranInsekTimerActive = false;
    private boolean isKranPupukTimerActive = false;
    private boolean isKranBuangTimerActive = false;

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
        switchKranAir = findViewById(R.id.switchKranAir);
        switchKranInsek = findViewById(R.id.switchKranInsek);
        switchKranPupuk = findViewById(R.id.switchKranPupuk);
        switchKranBuang = findViewById(R.id.switchKranBuang);
        switchSumberDaya = findViewById(R.id.switchSumberDaya);

        // Timer buttons
        btnTimerKranAir = findViewById(R.id.btnTimerValve1);
        btnTimerKranInsek = findViewById(R.id.btnTimerValve2);
        btnTimerKranPupuk = findViewById(R.id.btnTimerKranPupuk);
        btnTimerKranBuang = findViewById(R.id.btnTimerKranBuang);

        // Countdown displays
        tvCountdownKranAir = findViewById(R.id.tvCountdownKranAir);
        tvCountdownKranInsek = findViewById(R.id.tvCountdownKranInsek);
        tvCountdownKranPupuk = findViewById(R.id.tvCountdownKranPupuk);
        tvCountdownKranBuang = findViewById(R.id.tvCountdownKranBuang);
        tvPompaStatus = findViewById(R.id.tvPompaStatus);

        // Jadwal status card
        cardJadwalStatus = findViewById(R.id.cardJadwalStatus);
        layoutPompaTimerStatus = findViewById(R.id.layoutPompaTimerStatus);
        layoutKranAirTimerStatus = findViewById(R.id.layoutValve1TimerStatus);
        layoutKranInsekTimerStatus = findViewById(R.id.layoutValve2TimerStatus);
        layoutKranPupukTimerStatus = findViewById(R.id.layoutValve3TimerStatus);
        layoutKranBuangTimerStatus = findViewById(R.id.layoutValve4TimerStatus);
        tvPompaTimerStatus = findViewById(R.id.tvPompaTimerStatus);
        tvKranAirTimerStatus = findViewById(R.id.tvValve1TimerStatus);
        tvKranInsekTimerStatus = findViewById(R.id.tvValve2TimerStatus);
        tvKranPupukTimerStatus = findViewById(R.id.tvValve3TimerStatus);
        tvKranBuangTimerStatus = findViewById(R.id.tvValve4TimerStatus);
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
            publishMQTT("smartfarm/kontrol/pompa", isChecked ? "ON" : "OFF");
            if (!isChecked) {
                pumpAutoEnabled = false;
                tvPompaStatus.setText("Manual");
                tvPompaStatus.setTextColor(getColor(R.color.text_hint));
            }
        });

        switchKranAir.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_air", isChecked ? "ON" : "OFF");
        });

        switchKranInsek.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_insektisida", isChecked ? "ON" : "OFF");
        });

        switchKranPupuk.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_pupuk", isChecked ? "ON" : "OFF");
        });

        switchKranBuang.setOnCheckedChangeListener((buttonView, isChecked) -> {
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
        btnTimerKranAir.setOnClickListener(v -> showDurationDialog("Kran Air", 1));
        btnTimerKranInsek.setOnClickListener(v -> showDurationDialog("Kran Insektisida", 2));
        btnTimerKranPupuk.setOnClickListener(v -> showDurationDialog("Kran Pupuk", 3));
        btnTimerKranBuang.setOnClickListener(v -> showDurationDialog("Kran Pembuangan", 4));
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
        return isKranAirTimerActive || isKranInsekTimerActive || isKranPupukTimerActive || isKranBuangTimerActive;
    }

    private boolean isValveTimerActive(int index) {
        switch (index) {
            case 1: return isKranAirTimerActive;
            case 2: return isKranInsekTimerActive;
            case 3: return isKranPupukTimerActive;
            case 4: return isKranBuangTimerActive;
            default: return false;
        }
    }

    private void setValveTimerActive(int index, boolean active) {
        switch (index) {
            case 1: isKranAirTimerActive = active; break;
            case 2: isKranInsekTimerActive = active; break;
            case 3: isKranPupukTimerActive = active; break;
            case 4: isKranBuangTimerActive = active; break;
        }
    }

    private CountDownTimer getValveTimer(int index) {
        switch (index) {
            case 1: return timerKranAir;
            case 2: return timerKranInsek;
            case 3: return timerKranPupuk;
            case 4: return timerKranBuang;
            default: return null;
        }
    }

    private void setValveTimer(int index, CountDownTimer timer) {
        switch (index) {
            case 1: timerKranAir = timer; break;
            case 2: timerKranInsek = timer; break;
            case 3: timerKranPupuk = timer; break;
            case 4: timerKranBuang = timer; break;
        }
    }

    private void setValveSwitch(int index, boolean checked) {
        switch (index) {
            case 1: switchKranAir.setChecked(checked); break;
            case 2: switchKranInsek.setChecked(checked); break;
            case 3: switchKranPupuk.setChecked(checked); break;
            case 4: switchKranBuang.setChecked(checked); break;
        }
    }

    private String getValveName(int index) {
        return "Valve " + index;
    }

    private void updateValveCountdown(int index, String text, boolean isActive) {
        TextView tv;
        switch (index) {
            case 1: tv = tvCountdownKranAir; break;
            case 2: tv = tvCountdownKranInsek; break;
            case 3: tv = tvCountdownKranPupuk; break;
            case 4: tv = tvCountdownKranBuang; break;
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
            case 1: layoutKranAirTimerStatus.setVisibility(View.VISIBLE); break;
            case 2: layoutKranInsekTimerStatus.setVisibility(View.VISIBLE); break;
            case 3: layoutKranPupukTimerStatus.setVisibility(View.VISIBLE); break;
            case 4: layoutKranBuangTimerStatus.setVisibility(View.VISIBLE); break;
        }
    }

    private void updateTimerStatusCard(int valveIndex, String timeStr) {
        switch (valveIndex) {
            case 1: tvKranAirTimerStatus.setText(timeStr); break;
            case 2: tvKranInsekTimerStatus.setText(timeStr); break;
            case 3: tvKranPupukTimerStatus.setText(timeStr); break;
            case 4: tvKranBuangTimerStatus.setText(timeStr); break;
        }
    }

    private void hideTimerStatusRow(int valveIndex) {
        switch (valveIndex) {
            case 1: layoutKranAirTimerStatus.setVisibility(View.GONE); break;
            case 2: layoutKranInsekTimerStatus.setVisibility(View.GONE); break;
            case 3: layoutKranPupukTimerStatus.setVisibility(View.GONE); break;
            case 4: layoutKranBuangTimerStatus.setVisibility(View.GONE); break;
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
        if (timerKranAir != null) timerKranAir.cancel();
        if (timerKranInsek != null) timerKranInsek.cancel();
        if (timerKranPupuk != null) timerKranPupuk.cancel();
        if (timerKranBuang != null) timerKranBuang.cancel();
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
