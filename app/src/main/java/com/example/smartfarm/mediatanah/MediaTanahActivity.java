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
 *
 * ARSITEKTUR:
 * - Logika penjadwalan dijalankan sepenuhnya oleh MIKROKONTROLER (MCU).
 * - App hanya mengirim konfigurasi jadwal ke MCU via MQTT.
 * - MCU menyimpan jadwal, mengeksekusi ON/OFF valve secara mandiri.
 * - App menerima status terkini dari MCU untuk sinkronisasi tampilan.
 *
 * MQTT Subscribe:
 * - smartfarm/sensor/#          → data sensor (kelembapan, pH)
 * - smartfarm/jadwal/state      → daftar jadwal dari MCU
 * - smartfarm/status/valves     → status ON/OFF valve & pompa dari MCU
 *
 * MQTT Publish:
 * - smartfarm/jadwal/set        → kirim jadwal ke MCU
 * - smartfarm/jadwal/delete     → hapus jadwal dari MCU
 * - smartfarm/jadwal/sync       → request sinkronisasi
 * - smartfarm/kontrol/*         → kontrol manual valve/pompa
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

    // Timer/schedule buttons
    private ImageView btnTimerKranAir, btnTimerKranInsek, btnTimerKranPupuk, btnTimerKranBuang;

    // Schedule display per valve
    private TextView tvCountdownKranAir, tvCountdownKranInsek, tvCountdownKranPupuk, tvCountdownKranBuang;
    private TextView tvPompaStatus;

    // Jadwal Status Card views (menampilkan valve yang sedang aktif dari MCU)
    private MaterialCardView cardJadwalStatus;
    private LinearLayout layoutPompaTimerStatus;
    private LinearLayout layoutKranAirTimerStatus, layoutKranInsekTimerStatus;
    private LinearLayout layoutKranPupukTimerStatus, layoutKranBuangTimerStatus;
    private TextView tvPompaTimerStatus;
    private TextView tvKranAirTimerStatus, tvKranInsekTimerStatus;
    private TextView tvKranPupukTimerStatus, tvKranBuangTimerStatus;
    private TextView btnStopAllTimers;

    // ==================== MANAGER ====================

    private ValveScheduleManager scheduleManager;

    // ==================== DIALOG STATE ====================

    private AlertDialog activeScheduleListDialog;

    // ==================== FLAG: suppress switch listener saat sync dari MCU ====================
    private boolean suppressSwitchListener = false;

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

        // Inisialisasi manager (load cache lokal)
        scheduleManager = new ValveScheduleManager(this, this);

        setupMQTT();
        initViews();
        setupSwitchListeners();
        setupTimerButtons();

        // Tampilkan jadwal dari cache lokal
        refreshAllScheduleDisplays();

        // Request sinkronisasi jadwal terkini dari MCU
        scheduleManager.requestSyncFromMcu();
    }

    @Override
    protected String getClientId() {
        return "AndroidSmartFarm_MediaTanah";
    }

    /**
     * Subscribe ke sensor data + jadwal state + valve status dari MCU.
     */
    @Override
    protected String getSubscriptionTopic() {
        return "smartfarm/#";
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        // Handle sensor data
        switch (topic) {
            case "smartfarm/sensor/kelembapan":
                updateKelembapan(payload);
                return;
            case "smartfarm/sensor/ph":
                updatePH(payload);
                return;
        }

        // Handle jadwal & status dari MCU via manager
        if (topic.equals(ValveScheduleManager.TOPIC_SCHEDULE_STATE)
                || topic.equals(ValveScheduleManager.TOPIC_STATUS_VALVES)) {
            scheduleManager.handleMqttMessage(topic, payload);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scheduleManager != null) {
            refreshAllScheduleDisplays();
            // Request sinkronisasi saat app kembali ke foreground
            scheduleManager.requestSyncFromMcu();
        }
    }

    @Override
    protected void onDestroy() {
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

        // Timer/schedule buttons
        btnTimerKranAir = findViewById(R.id.btnTimerValve1);
        btnTimerKranInsek = findViewById(R.id.btnTimerValve2);
        btnTimerKranPupuk = findViewById(R.id.btnTimerKranPupuk);
        btnTimerKranBuang = findViewById(R.id.btnTimerKranBuang);

        // Schedule displays
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
        // Kontrol manual pompa
        switchPompa.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchListener) return;
            publishMQTT("smartfarm/kontrol/pompa", isChecked ? "ON" : "OFF");
        });

        // Kontrol manual valve
        switchKranAir.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchListener) return;
            publishMQTT("smartfarm/kontrol/kran_air", isChecked ? "ON" : "OFF");
        });

        switchKranInsek.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchListener) return;
            publishMQTT("smartfarm/kontrol/kran_insektisida", isChecked ? "ON" : "OFF");
        });

        switchKranPupuk.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchListener) return;
            publishMQTT("smartfarm/kontrol/kran_pupuk", isChecked ? "ON" : "OFF");
        });

        switchKranBuang.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchListener) return;
            publishMQTT("smartfarm/kontrol/kran_pembuangan", isChecked ? "ON" : "OFF");
        });

        switchSumberDaya.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchListener) return;
            String source = isChecked ? "AKI" : "PLN";
            publishMQTT("smartfarm/kontrol/sumber_daya", source);
        });

        // Tombol sync jadwal dari MCU
        btnStopAllTimers.setOnClickListener(v -> {
            scheduleManager.requestSyncFromMcu();
            Toast.makeText(this, "🔄 Sinkronisasi jadwal dari MCU...",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void setupTimerButtons() {
        btnTimerKranAir.setOnClickListener(v -> showScheduleListDialog("Kran Air", 1));
        btnTimerKranInsek.setOnClickListener(v -> showScheduleListDialog("Kran Insektisida", 2));
        btnTimerKranPupuk.setOnClickListener(v -> showScheduleListDialog("Kran Pupuk", 3));
        btnTimerKranBuang.setOnClickListener(v -> showScheduleListDialog("Kran Pembuangan", 4));
    }

    // ==================== SCHEDULE LIST DIALOG ====================

    /**
     * Menampilkan dialog daftar jadwal untuk valve tertentu.
     */
    private void showScheduleListDialog(String valveName, int valveIndex) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_schedule_list, null);

        // Bind title
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogListTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogListSubtitle);
        tvTitle.setText("Jadwal " + valveName);
        tvSubtitle.setText("Jadwal disimpan & dijalankan oleh mikrokontroler");

        LinearLayout layoutScheduleList = dialogView.findViewById(R.id.layoutScheduleList);
        LinearLayout layoutEmptyState = dialogView.findViewById(R.id.layoutEmptyState);
        MaterialButton btnAddSchedule = dialogView.findViewById(R.id.btnAddSchedule);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setNeutralButton("🔄 Sync MCU", (d, w) -> {
                    scheduleManager.requestSyncFromMcu();
                    Toast.makeText(this, "🔄 Sinkronisasi dari MCU...",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Tutup", null)
                .create();

        activeScheduleListDialog = dialog;

        // Populate list
        populateScheduleList(layoutScheduleList, layoutEmptyState,
                valveName, valveIndex, dialog);

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
                populateScheduleList(layoutScheduleList, layoutEmptyState,
                        valveName, valveIndex, dialog);
            });
        });

        dialog.setOnDismissListener(d -> activeScheduleListDialog = null);
        dialog.show();
    }

    /**
     * Populate daftar jadwal di dialog.
     */
    private void populateScheduleList(LinearLayout container, LinearLayout emptyState,
                                       String valveName, int valveIndex,
                                       AlertDialog parentDialog) {
        container.removeAllViews();
        List<ScheduleConfig> schedules = scheduleManager.getScheduleList(valveIndex);

        if (schedules.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);

        for (ScheduleConfig config : schedules) {
            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_schedule, container, false);

            TextView tvTime = itemView.findViewById(R.id.tvScheduleTime);
            TextView tvDays = itemView.findViewById(R.id.tvScheduleDays);
            MaterialSwitch switchEnabled = itemView.findViewById(R.id.switchScheduleEnabled);
            ImageView btnDelete = itemView.findViewById(R.id.btnDeleteSchedule);

            tvTime.setText(config.getTimeRangeDisplayText());
            tvDays.setText(config.getDaysDisplayText());
            switchEnabled.setChecked(config.isEnabled());

            float alpha = config.isEnabled() ? 1.0f : 0.5f;
            tvTime.setAlpha(alpha);
            tvDays.setAlpha(alpha);

            final int scheduleId = config.getId();

            // Klik item → edit jadwal
            itemView.setOnClickListener(v -> {
                ScheduleConfig editConfig = scheduleManager.getScheduleById(
                        valveIndex, scheduleId);
                if (editConfig != null) {
                    showAddEditScheduleDialog(valveName, valveIndex, editConfig, () -> {
                        populateScheduleList(container, emptyState,
                                valveName, valveIndex, parentDialog);
                    });
                }
            });

            // Toggle enable/disable → kirim update ke MCU
            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ScheduleConfig toggleConfig = scheduleManager.getScheduleById(
                        valveIndex, scheduleId);
                if (toggleConfig != null) {
                    toggleConfig.setEnabled(isChecked);
                    scheduleManager.updateSchedule(valveIndex, toggleConfig);
                    float newAlpha = isChecked ? 1.0f : 0.5f;
                    tvTime.setAlpha(newAlpha);
                    tvDays.setAlpha(newAlpha);
                }
            });

            // Delete → kirim hapus ke MCU
            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Hapus Jadwal")
                        .setMessage("Hapus jadwal " + config.getTimeRangeDisplayText()
                                + " dari mikrokontroler?")
                        .setPositiveButton("Hapus", (dialog, which) -> {
                            scheduleManager.removeSchedule(valveIndex, scheduleId);
                            populateScheduleList(container, emptyState,
                                    valveName, valveIndex, parentDialog);
                            Toast.makeText(this, "Jadwal dihapus dari MCU",
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
     * Dialog tambah/edit jadwal. Setelah disimpan, jadwal dikirim ke MCU via MQTT.
     */
    private void showAddEditScheduleDialog(String valveName, int valveIndex,
                                            ScheduleConfig existingConfig,
                                            Runnable onSaved) {
        boolean isEdit = (existingConfig != null);

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

        // Title
        tvTitle.setText(isEdit ? "Edit Jadwal " + valveName
                : "Tambah Jadwal " + valveName);
        tvSubtitle.setText("Jadwal akan dikirim ke mikrokontroler");

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

            for (Chip chip : allChips) {
                for (int[] mapping : chipDayMap) {
                    if (mapping[0] == chip.getId()) {
                        chip.setChecked(existingConfig.isDayScheduled(mapping[1]));
                        break;
                    }
                }
            }
        }

        // Build dialog
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Kirim ke MCU", (dialog, which) -> {
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

                    // Kumpulkan hari
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

                    ScheduleConfig config = new ScheduleConfig(
                            selectedDays, startHour, startMinute,
                            endHour, endMinute, true);

                    if (!config.hasValidDuration()) {
                        Toast.makeText(this,
                                "Jam mulai dan jam selesai tidak boleh sama",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isEdit) {
                        config.setId(existingConfig.getId());
                        scheduleManager.updateSchedule(valveIndex, config);
                        Toast.makeText(this,
                                "✅ Jadwal diperbarui & dikirim ke MCU",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        scheduleManager.addSchedule(valveIndex, config);
                        Toast.makeText(this,
                                "✅ Jadwal ditambah & dikirim ke MCU",
                                Toast.LENGTH_SHORT).show();
                    }

                    if (onSaved != null) onSaved.run();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ==================== SCHEDULE CALLBACK IMPLEMENTATION ====================

    @Override
    public void onMqttPublishRequested(String topic, String payload) {
        publishMQTT(topic, payload);
    }

    @Override
    public void onValveStateChanged(int valveIndex, boolean isOn) {
        // Update switch UI tanpa trigger listener
        suppressSwitchListener = true;
        getValveSwitch(valveIndex).setChecked(isOn);
        suppressSwitchListener = false;

        // Update status card untuk menunjukkan valve aktif dari MCU
        updateActiveStatusCard();

        // Update schedule display
        TextView tv = getCountdownTextView(valveIndex);
        if (tv != null && isOn) {
            tv.setText("🟢 Aktif (oleh MCU)");
            tv.setTextColor(getColor(R.color.status_info));
        } else if (tv != null && !isOn) {
            refreshScheduleDisplay(valveIndex);
        }
    }

    @Override
    public void onPumpStateChanged(boolean isOn, String statusText) {
        suppressSwitchListener = true;
        switchPompa.setChecked(isOn);
        suppressSwitchListener = false;

        tvPompaStatus.setText(statusText);
        tvPompaStatus.setTextColor(getColor(
                isOn ? R.color.status_info : R.color.text_hint));
    }

    @Override
    public void onScheduleListChanged(int valveIndex) {
        refreshScheduleDisplay(valveIndex);
    }

    // ==================== UI HELPER METHODS ====================

    /**
     * Refresh tampilan jadwal untuk satu valve.
     */
    private void refreshScheduleDisplay(int valveIndex) {
        // Jika valve sedang ON dari MCU, jangan overwrite
        if (scheduleManager.isValveOn(valveIndex)) {
            return;
        }

        TextView tv = getCountdownTextView(valveIndex);
        if (tv == null) return;

        String summary = scheduleManager.getScheduleSummaryText(valveIndex);
        tv.setText(summary);

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

    private void refreshAllScheduleDisplays() {
        for (int i = 1; i <= ValveScheduleManager.VALVE_COUNT; i++) {
            refreshScheduleDisplay(i);
        }
    }

    /**
     * Update status card berdasarkan valve yang sedang aktif dari MCU.
     */
    private void updateActiveStatusCard() {
        boolean anyActive = scheduleManager.hasActiveValves();

        if (anyActive) {
            cardJadwalStatus.setVisibility(View.VISIBLE);

            // Pompa
            if (scheduleManager.isPumpOn()) {
                layoutPompaTimerStatus.setVisibility(View.VISIBLE);
                tvPompaTimerStatus.setText("Aktif (MCU)");
            } else {
                layoutPompaTimerStatus.setVisibility(View.GONE);
            }

            // Valve 1-4
            updateValveStatusRow(1, layoutKranAirTimerStatus, tvKranAirTimerStatus);
            updateValveStatusRow(2, layoutKranInsekTimerStatus, tvKranInsekTimerStatus);
            updateValveStatusRow(3, layoutKranPupukTimerStatus, tvKranPupukTimerStatus);
            updateValveStatusRow(4, layoutKranBuangTimerStatus, tvKranBuangTimerStatus);
        } else {
            cardJadwalStatus.setVisibility(View.GONE);
        }
    }

    private void updateValveStatusRow(int valveIndex, LinearLayout layout, TextView tvStatus) {
        if (scheduleManager.isValveOn(valveIndex)) {
            layout.setVisibility(View.VISIBLE);
            tvStatus.setText("🟢 ON");
        } else {
            layout.setVisibility(View.GONE);
        }
    }

    private MaterialSwitch getValveSwitch(int valveIndex) {
        switch (valveIndex) {
            case 1: return switchKranAir;
            case 2: return switchKranInsek;
            case 3: return switchKranPupuk;
            case 4: return switchKranBuang;
            default: return switchKranAir;
        }
    }

    private TextView getCountdownTextView(int valveIndex) {
        switch (valveIndex) {
            case 1: return tvCountdownKranAir;
            case 2: return tvCountdownKranInsek;
            case 3: return tvCountdownKranPupuk;
            case 4: return tvCountdownKranBuang;
            default: return null;
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
