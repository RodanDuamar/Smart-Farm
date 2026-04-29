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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import com.example.smartfarm.base.NotificationHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
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
 * - MULTIPLE jadwal per valve (tambah, edit, hapus)
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

    // ==================== ACTIVE DIALOG REFERENCES ====================

    /** Reference ke dialog daftar jadwal yang sedang terbuka (untuk refresh) */
    private AlertDialog activeScheduleListDialog;
    private int activeScheduleListValveIndex = -1;

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

        // Pastikan semua alarm terdaftar di AlarmManager
        // (penting jika alarm hilang karena force-stop atau update app)
        scheduleManager.ensureAlarmsRegistered();

        // Cek apakah ada jadwal yang seharusnya sedang berjalan sekarang
        // (misal: app dibuka saat jadwal sedang aktif)
        scheduleManager.checkAndRunTodaySchedules();
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
    protected void onResume() {
        super.onResume();
        // Refresh tampilan jadwal dan cek jadwal yang mungkin berjalan
        if (scheduleManager != null) {
            refreshAllScheduleDisplays();
            scheduleManager.checkAndRunTodaySchedules();
        }
    }

    @Override
    protected void onDestroy() {
        // Cancel hanya in-app CountDownTimer, BUKAN alarm AlarmManager.
        // Jadwal AlarmManager tetap berjalan di background.
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
        btnTimerKranAir.setOnClickListener(v -> showScheduleListDialog("Kran Air", 1));
        btnTimerKranInsek.setOnClickListener(v -> showScheduleListDialog("Kran Insektisida", 2));
        btnTimerKranPupuk.setOnClickListener(v -> showScheduleListDialog("Kran Pupuk", 3));
        btnTimerKranBuang.setOnClickListener(v -> showScheduleListDialog("Kran Pembuangan", 4));
    }

    // ==================== SCHEDULE LIST DIALOG (MULTI-SCHEDULE) ====================

    /**
     * Menampilkan dialog daftar jadwal untuk valve tertentu.
     * Dari sini user bisa: melihat semua jadwal, menambah, mengedit, menghapus, enable/disable.
     */
    private void showScheduleListDialog(String valveName, int valveIndex) {
        // Jika timer sedang berjalan, tawarkan opsi hentikan
        if (scheduleManager.isTimerActive(valveIndex)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Timer " + valveName + " Aktif")
                    .setMessage("Timer sedang berjalan. Hentikan timer?")
                    .setPositiveButton("Hentikan", (dialog, which) -> {
                        scheduleManager.stopValve(valveIndex);
                        Toast.makeText(this, "Timer " + valveName + " dihentikan",
                                Toast.LENGTH_SHORT).show();
                        // Tampilkan dialog jadwal setelah timer dihentikan
                        showScheduleListDialogInternal(valveName, valveIndex);
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }

        showScheduleListDialogInternal(valveName, valveIndex);
    }

    /**
     * Internal: build & show dialog daftar jadwal.
     */
    private void showScheduleListDialogInternal(String valveName, int valveIndex) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_schedule_list, null);

        // Bind title
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogListTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogListSubtitle);
        tvTitle.setText("Jadwal " + valveName);
        tvSubtitle.setText("Kelola jadwal penyiraman " + valveName.toLowerCase());

        LinearLayout layoutScheduleList = dialogView.findViewById(R.id.layoutScheduleList);
        LinearLayout layoutEmptyState = dialogView.findViewById(R.id.layoutEmptyState);
        MaterialButton btnAddSchedule = dialogView.findViewById(R.id.btnAddSchedule);

        // Build dialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setNegativeButton("Tutup", null)
                .create();

        // Simpan reference untuk refresh
        activeScheduleListDialog = dialog;
        activeScheduleListValveIndex = valveIndex;

        // Populate list
        populateScheduleList(layoutScheduleList, layoutEmptyState, valveName, valveIndex, dialog);

        // Add schedule button
        btnAddSchedule.setOnClickListener(v -> {
            List<ScheduleConfig> list = scheduleManager.getScheduleList(valveIndex);
            if (list.size() >= ValveScheduleManager.MAX_SCHEDULES_PER_VALVE) {
                Toast.makeText(this, "Maksimal "
                        + ValveScheduleManager.MAX_SCHEDULES_PER_VALVE
                        + " jadwal per valve", Toast.LENGTH_SHORT).show();
                return;
            }
            showAddEditScheduleDialog(valveName, valveIndex, null, () -> {
                // Refresh list setelah jadwal ditambah
                populateScheduleList(layoutScheduleList, layoutEmptyState,
                        valveName, valveIndex, dialog);
            });
        });

        dialog.setOnDismissListener(d -> {
            activeScheduleListDialog = null;
            activeScheduleListValveIndex = -1;
        });

        dialog.show();
    }

    /**
     * Populate (atau refresh) daftar jadwal di dialog list.
     */
    private void populateScheduleList(LinearLayout container, LinearLayout emptyState,
                                       String valveName, int valveIndex, AlertDialog parentDialog) {
        container.removeAllViews();
        List<ScheduleConfig> schedules = scheduleManager.getScheduleList(valveIndex);

        if (schedules.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);

        for (int i = 0; i < schedules.size(); i++) {
            ScheduleConfig config = schedules.get(i);
            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_schedule, container, false);

            TextView tvTime = itemView.findViewById(R.id.tvScheduleTime);
            TextView tvDays = itemView.findViewById(R.id.tvScheduleDays);
            MaterialSwitch switchEnabled = itemView.findViewById(R.id.switchScheduleEnabled);
            ImageView btnDelete = itemView.findViewById(R.id.btnDeleteSchedule);

            // Set data
            tvTime.setText(config.getTimeRangeDisplayText());
            tvDays.setText(config.getDaysDisplayText());
            switchEnabled.setChecked(config.isEnabled());

            // Dimmed styling jika disabled
            float alpha = config.isEnabled() ? 1.0f : 0.5f;
            tvTime.setAlpha(alpha);
            tvDays.setAlpha(alpha);

            // Klik item -> edit jadwal
            final int scheduleId = config.getId();
            itemView.setOnClickListener(v -> {
                ScheduleConfig editConfig = scheduleManager.getScheduleById(valveIndex, scheduleId);
                if (editConfig != null) {
                    showAddEditScheduleDialog(valveName, valveIndex, editConfig, () -> {
                        populateScheduleList(container, emptyState,
                                valveName, valveIndex, parentDialog);
                    });
                }
            });

            // Toggle enable/disable
            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ScheduleConfig toggleConfig = scheduleManager.getScheduleById(valveIndex, scheduleId);
                if (toggleConfig != null) {
                    toggleConfig.setEnabled(isChecked);
                    scheduleManager.updateSchedule(valveIndex, toggleConfig);
                    // Update styling
                    float newAlpha = isChecked ? 1.0f : 0.5f;
                    tvTime.setAlpha(newAlpha);
                    tvDays.setAlpha(newAlpha);
                }
            });

            // Delete button
            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Hapus Jadwal")
                        .setMessage("Hapus jadwal " + config.getTimeRangeDisplayText() + "?")
                        .setPositiveButton("Hapus", (dialog, which) -> {
                            scheduleManager.removeSchedule(valveIndex, scheduleId);
                            populateScheduleList(container, emptyState,
                                    valveName, valveIndex, parentDialog);
                            Toast.makeText(this, "Jadwal dihapus",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });

            container.addView(itemView);
        }
    }

    // ==================== ADD/EDIT SCHEDULE DIALOG ====================

    /**
     * Menampilkan dialog untuk menambah atau mengedit satu jadwal.
     *
     * @param valveName    Nama valve untuk judul
     * @param valveIndex   Index valve (1-4)
     * @param existingConfig Jadwal yang diedit, atau null untuk jadwal baru
     * @param onSaved      Callback yang dipanggil setelah jadwal disimpan
     */
    private void showAddEditScheduleDialog(String valveName, int valveIndex,
                                            ScheduleConfig existingConfig,
                                            Runnable onSaved) {
        boolean isEdit = (existingConfig != null);

        // Inflate dialog layout
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_set_schedule, null);

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

        Chip[] allChips = { chipSenin, chipSelasa, chipRabu, chipKamis,
                chipJumat, chipSabtu, chipMinggu };

        // Setup title
        tvTitle.setText(isEdit ? "Edit Jadwal " + valveName : "Tambah Jadwal " + valveName);
        tvSubtitle.setText("Pompa akan otomatis menyala bersama " + valveName);

        // Pre-fill jika edit
        if (isEdit) {
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

        // Build & show dialog
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Simpan", (dialog, which) -> {
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

                    // Buat config
                    ScheduleConfig config = new ScheduleConfig(
                            selectedDays, startHour, startMinute, endHour, endMinute, true);

                    if (!config.hasValidDuration()) {
                        Toast.makeText(this, "Jam mulai dan jam selesai tidak boleh sama",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isEdit) {
                        // Update jadwal yang ada
                        config.setId(existingConfig.getId());
                        scheduleManager.updateSchedule(valveIndex, config);
                        Toast.makeText(this,
                                "✅ Jadwal diperbarui: " + config.getTimeRangeDisplayText(),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // Tambah jadwal baru
                        scheduleManager.addSchedule(valveIndex, config);
                        Toast.makeText(this,
                                "✅ Jadwal ditambahkan: " + config.getDaysDisplayText()
                                        + " • " + config.getTimeRangeDisplayText(),
                                Toast.LENGTH_SHORT).show();
                    }

                    // Callback refresh
                    if (onSaved != null) {
                        onSaved.run();
                    }
                })
                .setNegativeButton("Batal", null);

        // Tambahkan tombol "Jalankan Sekarang" hanya untuk jadwal baru
        if (!isEdit) {
            builder.setNeutralButton("Jalankan Sekarang", (dialog, which) -> {
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

                ScheduleConfig config = new ScheduleConfig(
                        selectedDays, startHour, startMinute, endHour, endMinute,
                        !selectedDays.isEmpty());

                if (!config.hasValidDuration()) {
                    Toast.makeText(this, "Jam mulai dan jam selesai tidak boleh sama",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Simpan jadwal (jika ada hari dipilih)
                if (!selectedDays.isEmpty()) {
                    scheduleManager.addSchedule(valveIndex, config);
                }

                // Langsung jalankan timer sekarang
                long totalMs = config.getTotalDurationMs();
                scheduleManager.startValveWithDuration(valveIndex, totalMs);
                showTimerStatusCard(valveIndex);

                // Tutup dialog list juga
                if (activeScheduleListDialog != null) {
                    activeScheduleListDialog.dismiss();
                }

                Toast.makeText(this,
                        "⏱ " + valveName + " dimulai: "
                                + ValveScheduleManager.formatTime(totalMs),
                        Toast.LENGTH_SHORT).show();
            });
        }

        builder.show();
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
     * Menampilkan ringkasan dari semua jadwal valve.
     */
    private void refreshScheduleDisplay(int valveIndex) {
        if (scheduleManager.isTimerActive(valveIndex)) {
            return; // Jangan overwrite countdown yang sedang jalan
        }

        TextView tv = getCountdownTextView(valveIndex);
        if (tv == null)
            return;

        String summary = scheduleManager.getScheduleSummaryText(valveIndex);
        tv.setText(summary);

        // Determine color
        List<ScheduleConfig> list = scheduleManager.getScheduleList(valveIndex);
        boolean hasActiveToday = false;
        for (ScheduleConfig config : list) {
            if (config.isEnabled() && config.isTodayScheduled()) {
                hasActiveToday = true;
                break;
            }
        }

        if (hasActiveToday) {
            tv.setTextColor(getColor(R.color.status_info));
        } else if (!list.isEmpty() && scheduleManager.hasSchedule(valveIndex)) {
            tv.setTextColor(getColor(R.color.text_secondary));
        } else {
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
