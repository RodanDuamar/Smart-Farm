package com.example.smartfarm.mediatanah;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Activity untuk monitoring dan kontrol sistem irigasi Media Tanah.
 * Menampilkan sensor kelembapan, pH tanah, valve kontrol, dan penjadwalan.
 * Extends BaseSmartFarmActivity untuk reuse MQTT logic.
 *
 * Fitur:
 * - Monitoring sensor (kelembapan, pH)
 * - Notifikasi peringatan kondisi abnormal
 * - Penjadwalan valve per hari dengan durasi (+ pompa otomatis)
 *
 * Desain OOP:
 * - Scheduling logic didelegasikan ke ValveScheduleManager
 * - Komunikasi via ScheduleCallback interface
 * - Model jadwal disimpan di ScheduleConfig
 */
public class MediaTanahActivity extends BaseSmartFarmActivity
        implements ValveScheduleManager.ScheduleCallback {

    private static final String TAG = "MediaTanah";

    // ==================== VIEWS ====================

    // Sensor views
    private TextView tvKelembapan, tvPH, tvStatusKelembapan, tvStatusPH;
    private ProgressBar progressKelembapan, progressPH;

    // Switch views
    private MaterialSwitch switchPompa, switchKranAir, switchKranInsek, switchKranPupuk, switchKranBuang;
    private MaterialSwitch switchSumberDaya;

    // Timer buttons
    private ImageView btnTimerKranAir, btnTimerKranInsek, btnTimerKranPupuk, btnTimerKranBuang;

    // Countdown/schedule display per valve
    private TextView tvCountdownKranAir, tvCountdownKranInsek, tvCountdownKranPupuk, tvCountdownKranBuang;
    private TextView tvPompaStatus;

    // Jadwal Status Card views
    private MaterialCardView cardJadwalStatus;
    private LinearLayout layoutPompaTimerStatus;
    private LinearLayout layoutKranAirTimerStatus, layoutKranInsekTimerStatus;
    private LinearLayout layoutKranPupukTimerStatus, layoutKranBuangTimerStatus;
    private TextView tvPompaTimerStatus;
    private TextView tvKranAirTimerStatus, tvKranInsekTimerStatus;
    private TextView tvKranPupukTimerStatus, tvKranBuangTimerStatus;
    private TextView btnStopAllTimers;

    // ==================== MANAGER ====================

    /** Manager yang mengelola semua scheduling & timer logic */
    private ValveScheduleManager scheduleManager;

    // ==================== PERMISSION ====================

    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                } else {
                    Log.w(TAG, "Notification permission denied");
                }
            });

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_tanah);

        // Setup notifikasi
        NotificationHelper.createNotificationChannels(this);
        requestNotificationPermission();

        // Inisialisasi manager (akan load jadwal dari SharedPreferences)
        scheduleManager = new ValveScheduleManager(this, this);

        setupMQTT();
        initViews();
        setupSwitchListeners();
        setupTimerButtons();

        // Tampilkan info jadwal yang tersimpan di UI
        refreshAllScheduleDisplays();
    }

    @Override
    protected String getClientId() {
        return "AndroidSmartFarm_MediaTanah";
    }

    @Override
    protected String[] getSubscriptionTopics() {
        return new String[]{
                "smartfarm/sensor/kelembapan",
                "smartfarm/sensor/ph",
                "mediatanah/status"  // Tambahkan ini agar sinkron dengan ESP32
        };
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

    @Override
    protected void onDestroy() {
        // Cancel semua timer saat activity dihancurkan
        if (scheduleManager != null) {
            scheduleManager.cancelAllTimers();
        }
        super.onDestroy();
    }

    // ==================== INITIALIZATION ====================

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

        // Countdown/schedule displays
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
            if (!isChecked && scheduleManager.hasActiveTimers()) {
                switchPompa.setChecked(true);
                Toast.makeText(this,
                        "⚠ Pompa tidak bisa dimatikan saat valve dijadwalkan",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            publishMQTT("smartfarm/kontrol/pompa", isChecked ? "ON" : "OFF");
            if (!isChecked) {
                scheduleManager.setPumpAutoEnabled(false);
                tvPompaStatus.setText("Manual");
                tvPompaStatus.setTextColor(getColor(R.color.text_hint));
            }
        });

        switchKranAir.setOnCheckedChangeListener(
                (buttonView, isChecked) -> publishMQTT("smartfarm/kontrol/kran_air", isChecked ? "ON" : "OFF"));

        switchKranInsek.setOnCheckedChangeListener(
                (buttonView, isChecked) -> publishMQTT("smartfarm/kontrol/kran_insektisida", isChecked ? "ON" : "OFF"));

        switchKranPupuk.setOnCheckedChangeListener(
                (buttonView, isChecked) -> publishMQTT("smartfarm/kontrol/kran_pupuk", isChecked ? "ON" : "OFF"));

        switchKranBuang.setOnCheckedChangeListener(
                (buttonView, isChecked) -> publishMQTT("smartfarm/kontrol/kran_pembuangan", isChecked ? "ON" : "OFF"));

        switchSumberDaya.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String source = isChecked ? "AKI" : "PLN";
            publishMQTT("smartfarm/kontrol/sumber_daya", source);
        });

        // Stop all timers button
        btnStopAllTimers.setOnClickListener(v -> confirmStopAllTimers());
    }

    private void setupTimerButtons() {
        btnTimerKranAir.setOnClickListener(v -> showScheduleDialog("Kran Air", 1));
        btnTimerKranInsek.setOnClickListener(v -> showScheduleDialog("Kran Insektisida", 2));
        btnTimerKranPupuk.setOnClickListener(v -> showScheduleDialog("Kran Pupuk", 3));
        btnTimerKranBuang.setOnClickListener(v -> showScheduleDialog("Kran Pembuangan", 4));
    }

    // ==================== SCHEDULING DIALOG ====================

    /**
     * Menampilkan dialog penjadwalan untuk valve tertentu.
     * Jika valve sudah punya timer aktif, tanyakan apakah ingin dihentikan.
     * Jika sudah punya jadwal, pre-fill dialog dengan jadwal yang ada.
     */
    private void showScheduleDialog(String valveName, int valveIndex) {
        // Jika timer sedang berjalan, tawarkan opsi hentikan
        if (scheduleManager.isTimerActive(valveIndex)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Timer " + valveName + " Aktif")
                    .setMessage("Timer sedang berjalan. Hentikan timer?")
                    .setPositiveButton("Hentikan", (dialog, which) -> {
                        scheduleManager.stopValve(valveIndex);
                        Toast.makeText(this, "Timer " + valveName + " dihentikan",
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }

        // Inflate dialog layout baru
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_schedule, null);

        // Bind views
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        android.widget.TimePicker timePickerStart = dialogView.findViewById(R.id.timePickerStart);
        android.widget.TimePicker timePickerEnd = dialogView.findViewById(R.id.timePickerEnd);

        timePickerStart.setIs24HourView(true);
        timePickerEnd.setIs24HourView(true);

        // Day chips
        Chip chipSenin = dialogView.findViewById(R.id.chipSenin);
        Chip chipSelasa = dialogView.findViewById(R.id.chipSelasa);
        Chip chipRabu = dialogView.findViewById(R.id.chipRabu);
        Chip chipKamis = dialogView.findViewById(R.id.chipKamis);
        Chip chipJumat = dialogView.findViewById(R.id.chipJumat);
        Chip chipSabtu = dialogView.findViewById(R.id.chipSabtu);
        Chip chipMinggu = dialogView.findViewById(R.id.chipMinggu);

        // Map chip to Calendar constant
        final int[][] chipDayMap = {
                { chipSenin.getId(), Calendar.MONDAY },
                { chipSelasa.getId(), Calendar.TUESDAY },
                { chipRabu.getId(), Calendar.WEDNESDAY },
                { chipKamis.getId(), Calendar.THURSDAY },
                { chipJumat.getId(), Calendar.FRIDAY },
                { chipSabtu.getId(), Calendar.SATURDAY },
                { chipMinggu.getId(), Calendar.SUNDAY }
        };

        Chip[] allChips = { chipSenin, chipSelasa, chipRabu, chipKamis, chipJumat, chipSabtu, chipMinggu };

        // Setup title
        tvTitle.setText("Atur Jadwal " + valveName);
        tvSubtitle.setText("Pompa akan otomatis menyala bersama " + valveName);

        // Pre-fill jika sudah ada jadwal tersimpan
        ScheduleConfig existingConfig = scheduleManager.getSchedule(valveIndex);
        if (existingConfig != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                timePickerStart.setHour(existingConfig.getStartHour());
                timePickerStart.setMinute(existingConfig.getStartMinute());
                timePickerEnd.setHour(existingConfig.getEndHour());
                timePickerEnd.setMinute(existingConfig.getEndMinute());
            } else {
                timePickerStart.setCurrentHour(existingConfig.getStartHour());
                timePickerStart.setCurrentMinute(existingConfig.getStartMinute());
                timePickerEnd.setCurrentHour(existingConfig.getEndHour());
                timePickerEnd.setCurrentMinute(existingConfig.getEndMinute());
            }

            // Check chips sesuai hari yang tersimpan
            for (Chip chip : allChips) {
                for (int[] mapping : chipDayMap) {
                    if (mapping[0] == chip.getId()) {
                        chip.setChecked(existingConfig.isDayScheduled(mapping[1]));
                        break;
                    }
                }
            }
        }

        // Build & show dialog dengan 3 tombol
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Simpan Jadwal", (dialog, which) -> {
                    int startHour, startMinute, endHour, endMinute;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startHour = timePickerStart.getHour();
                        startMinute = timePickerStart.getMinute();
                        endHour = timePickerEnd.getHour();
                        endMinute = timePickerEnd.getMinute();
                    } else {
                        startHour = timePickerStart.getCurrentHour();
                        startMinute = timePickerStart.getCurrentMinute();
                        endHour = timePickerEnd.getCurrentHour();
                        endMinute = timePickerEnd.getCurrentMinute();
                    }

                    // Kumpulkan hari yang dipilih
                    Set<Integer> selectedDays = new LinkedHashSet<>();
                    for (Chip chip : allChips) {
                        if (chip.isChecked()) {
                            for (int[] mapping : chipDayMap) {
                                if (mapping[0] == chip.getId()) {
                                    selectedDays.add(mapping[1]);
                                    break;
                                }
                            }
                        }
                    }

                    if (selectedDays.isEmpty()) {
                        Toast.makeText(this, "Pilih minimal 1 hari",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Buat dan simpan jadwal baru
                    ScheduleConfig config = new ScheduleConfig(selectedDays, startHour, startMinute, endHour, endMinute, true);
                    
                    if (!config.hasValidDuration()) {
                        Toast.makeText(this, "Jam mulai as dan jam selesai tidak boleh sama", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    scheduleManager.setSchedule(valveIndex, config);

                    Toast.makeText(this,
                            "✅ Jadwal " + valveName + " disimpan: " + config.getDaysDisplayText()
                                    + " • " + config.getTimeRangeDisplayText(),
                            Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("Jalankan Sekarang", (dialog, which) -> {
                    int startHour, startMinute, endHour, endMinute;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startHour = timePickerStart.getHour();
                        startMinute = timePickerStart.getMinute();
                        endHour = timePickerEnd.getHour();
                        endMinute = timePickerEnd.getMinute();
                    } else {
                        startHour = timePickerStart.getCurrentHour();
                        startMinute = timePickerStart.getCurrentMinute();
                        endHour = timePickerEnd.getCurrentHour();
                        endMinute = timePickerEnd.getCurrentMinute();
                    }

                    // Simpan juga hari yang dipilih (jika ada)
                    Set<Integer> selectedDays = new LinkedHashSet<>();
                    for (Chip chip : allChips) {
                        if (chip.isChecked()) {
                            for (int[] mapping : chipDayMap) {
                                if (mapping[0] == chip.getId()) {
                                    selectedDays.add(mapping[1]);
                                    break;
                                }
                            }
                        }
                    }

                    // Simpan config (enabled hanya jika ada hari dipilih)
                    ScheduleConfig config = new ScheduleConfig(
                            selectedDays, startHour, startMinute, endHour, endMinute, !selectedDays.isEmpty());
                    
                    if (!config.hasValidDuration()) {
                        Toast.makeText(this, "Jam mulai as dan jam selesai tidak boleh sama", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    scheduleManager.setSchedule(valveIndex, config);

                    // Langsung jalankan timer sekarang
                    long totalMs = config.getTotalDurationMs();
                    scheduleManager.startValveWithDuration(valveIndex, totalMs);
                    showTimerStatusCard(valveIndex);

                    Toast.makeText(this,
                            "⏱ " + valveName + " dimulai: " + ValveScheduleManager.formatTime(totalMs),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    /**
     * Konfirmasi sebelum menghentikan semua timer.
     */
    private void confirmStopAllTimers() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Hentikan Semua Timer")
                .setMessage("Yakin ingin menghentikan semua timer aktif? Semua valve dan pompa akan dimatikan.")
                .setPositiveButton("Hentikan Semua", (dialog, which) -> {
                    scheduleManager.stopAllValves();
                    Toast.makeText(this, "Semua timer dihentikan", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ==================== SCHEDULE CALLBACK IMPLEMENTATION ====================

    @Override
    public void onValveSwitched(int valveIndex, boolean turnOn) {
        getValveSwitch(valveIndex).setChecked(turnOn);
    }

    @Override
    public void onCountdownTick(int valveIndex, long millisRemaining, String formattedTime) {
        // Update countdown text di bawah valve
        TextView tv = getCountdownTextView(valveIndex);
        if (tv != null) {
            tv.setText("⏱ Sisa: " + formattedTime);
            tv.setTextColor(getColor(R.color.status_info));
        }

        // Update status card
        updateTimerStatusCard(valveIndex, formattedTime);
    }

    @Override
    public void onTimerFinished(int valveIndex) {
        String valveName = ValveScheduleManager.getValveName(valveIndex);

        // Update countdown text
        TextView tv = getCountdownTextView(valveIndex);
        if (tv != null) {
            tv.setText("✅ Selesai");
            tv.setTextColor(getColor(R.color.status_good));
        }

        // Sembunyikan dari status card
        hideTimerStatusRow(valveIndex);

        Toast.makeText(this, "Timer " + valveName + " selesai", Toast.LENGTH_SHORT).show();

        // Kirim notifikasi
        NotificationHelper.sendWarningNotification(
                this,
                NotificationHelper.CHANNEL_MEDIA_TANAH,
                3000 + valveIndex,
                "✅ Timer " + valveName + " Selesai",
                valveName + " telah dimatikan otomatis setelah durasi selesai.",
                MediaTanahActivity.class);

        // Kembalikan tampilan jadwal setelah delay singkat
        tv.postDelayed(() -> refreshScheduleDisplay(valveIndex), 3000);
    }

    @Override
    public void onPumpAutoControl(boolean turnOn, String statusText) {
        switchPompa.setChecked(turnOn);
        tvPompaStatus.setText(statusText);
        tvPompaStatus.setTextColor(getColor(
                turnOn ? R.color.status_info : R.color.text_hint));
    }

    @Override
    public void onScheduleUpdated(int valveIndex, ScheduleConfig config) {
        refreshScheduleDisplay(valveIndex);
    }

    @Override
    public void onAllTimersFinished() {
        cardJadwalStatus.setVisibility(View.GONE);
    }

    // ==================== UI HELPER METHODS ====================

    /**
     * Refresh tampilan jadwal untuk satu valve.
     */
    private void refreshScheduleDisplay(int valveIndex) {
        if (scheduleManager.isTimerActive(valveIndex)) {
            return; // Jangan overwrite countdown yang sedang jalan
        }

        ScheduleConfig config = scheduleManager.getSchedule(valveIndex);
        TextView tv = getCountdownTextView(valveIndex);
        if (tv == null)
            return;

        if (config != null && config.isEnabled() && config.hasDaysSelected()) {
            tv.setText(config.getSummaryText());
            // Highlight jika hari ini termasuk jadwal
            if (config.isTodayScheduled()) {
                tv.setTextColor(getColor(R.color.status_info));
            } else {
                tv.setTextColor(getColor(R.color.text_secondary));
            }
        } else {
            tv.setText("Tidak dijadwalkan");
            tv.setTextColor(getColor(R.color.text_hint));
        }
    }

    /**
     * Refresh tampilan jadwal untuk semua valve.
     */
    private void refreshAllScheduleDisplays() {
        for (int i = 1; i <= ValveScheduleManager.VALVE_COUNT; i++) {
            refreshScheduleDisplay(i);
        }
    }

    /**
     * Dapatkan MaterialSwitch valve berdasarkan index.
     */
    private MaterialSwitch getValveSwitch(int valveIndex) {
        switch (valveIndex) {
            case 1:
                return switchKranAir;
            case 2:
                return switchKranInsek;
            case 3:
                return switchKranPupuk;
            case 4:
                return switchKranBuang;
            default:
                return switchKranAir;
        }
    }

    /**
     * Dapatkan TextView countdown berdasarkan index valve.
     */
    private TextView getCountdownTextView(int valveIndex) {
        switch (valveIndex) {
            case 1:
                return tvCountdownKranAir;
            case 2:
                return tvCountdownKranInsek;
            case 3:
                return tvCountdownKranPupuk;
            case 4:
                return tvCountdownKranBuang;
            default:
                return null;
        }
    }

    private void showTimerStatusCard(int valveIndex) {
        cardJadwalStatus.setVisibility(View.VISIBLE);
        layoutPompaTimerStatus.setVisibility(View.VISIBLE);
        tvPompaTimerStatus.setText("Aktif (otomatis)");

        switch (valveIndex) {
            case 1:
                layoutKranAirTimerStatus.setVisibility(View.VISIBLE);
                break;
            case 2:
                layoutKranInsekTimerStatus.setVisibility(View.VISIBLE);
                break;
            case 3:
                layoutKranPupukTimerStatus.setVisibility(View.VISIBLE);
                break;
            case 4:
                layoutKranBuangTimerStatus.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateTimerStatusCard(int valveIndex, String timeStr) {
        switch (valveIndex) {
            case 1:
                tvKranAirTimerStatus.setText(timeStr);
                break;
            case 2:
                tvKranInsekTimerStatus.setText(timeStr);
                break;
            case 3:
                tvKranPupukTimerStatus.setText(timeStr);
                break;
            case 4:
                tvKranBuangTimerStatus.setText(timeStr);
                break;
        }
    }

    private void hideTimerStatusRow(int valveIndex) {
        switch (valveIndex) {
            case 1:
                layoutKranAirTimerStatus.setVisibility(View.GONE);
                break;
            case 2:
                layoutKranInsekTimerStatus.setVisibility(View.GONE);
                break;
            case 3:
                layoutKranPupukTimerStatus.setVisibility(View.GONE);
                break;
            case 4:
                layoutKranBuangTimerStatus.setVisibility(View.GONE);
                break;
        }
        if (!scheduleManager.hasActiveTimers()) {
            layoutPompaTimerStatus.setVisibility(View.GONE);
        }
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
                        MediaTanahActivity.class);
            } else {
                tvStatusKelembapan.setText("Terlalu Basah");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_warning));
                NotificationHelper.sendWarningNotification(
                        this, NotificationHelper.CHANNEL_MEDIA_TANAH,
                        NotificationHelper.NOTIF_KELEMBAPAN_TINGGI,
                        "⚠️ Kelembapan Tanah Tinggi!",
                        "Kelembapan tanah saat ini " + kelInt + "% (di atas 80%). "
                                + "Tanah terlalu basah, kurangi penyiraman!",
                        MediaTanahActivity.class);
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
                        MediaTanahActivity.class);
            } else {
                tvStatusPH.setText("Basa");
                tvStatusPH.setTextColor(getColor(R.color.status_warning));
                NotificationHelper.sendWarningNotification(
                        this, NotificationHelper.CHANNEL_MEDIA_TANAH,
                        NotificationHelper.NOTIF_PH_TANAH_BASA,
                        "⚠️ pH Tanah Terlalu Basa!",
                        "pH tanah saat ini " + ph + " (di atas 7.5). "
                                + "Kondisi terlalu basa, pertimbangkan menambahkan pupuk organik!",
                        MediaTanahActivity.class);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid pH value: " + value);
        }
    }
}
