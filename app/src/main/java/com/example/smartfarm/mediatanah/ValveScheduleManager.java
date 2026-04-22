package com.example.smartfarm.mediatanah;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manager class yang mengelola semua jadwal dan timer valve.
 *
 * Tanggung jawab:
 * - CRUD jadwal per valve (4 valve, MULTIPLE jadwal per valve)
 * - Menyimpan/memuat jadwal ke/dari SharedPreferences
 * - Mengelola CountDownTimer per valve
 * - Mengatur logika pompa otomatis (ON saat valve aktif, OFF saat semua selesai)
 * - Communicating state changes ke Activity via ScheduleCallback
 *
 * Prinsip OOP:
 * - Single Responsibility: hanya mengelola scheduling logic
 * - Dependency Inversion: berkomunikasi via interface (ScheduleCallback)
 * - Encapsulation: internal timer state disembunyikan
 */
public class ValveScheduleManager {

    private static final String TAG = "ValveScheduleManager";
    private static final String PREFS_NAME = "valve_schedules";
    private static final String KEY_SCHEDULE_PREFIX = "schedule_valve_";
    public static final int VALVE_COUNT = 4;
    /** Maximum jadwal per valve */
    public static final int MAX_SCHEDULES_PER_VALVE = 10;

    /** Jadwal konfigurasi per valve (index 1-4), setiap valve bisa punya beberapa jadwal */
    private final Map<Integer, List<ScheduleConfig>> schedules;

    /** CountDownTimer per valve yang sedang berjalan */
    private final Map<Integer, CountDownTimer> activeTimers;

    /** Track valve mana yang timer-nya aktif */
    private final Map<Integer, Boolean> timerActiveFlags;

    /** Apakah pompa dinyalakan otomatis oleh valve timer */
    private boolean pumpAutoEnabled = false;

    /** SharedPreferences untuk persistence */
    private final SharedPreferences prefs;

    /** Callback untuk komunikasi ke Activity */
    private final ScheduleCallback callback;

    /** AlarmManager helper untuk scheduling background */
    private final ScheduleAlarmHelper alarmHelper;

    // ==================== CALLBACK INTERFACE ====================

    /**
     * Interface untuk komunikasi dari manager ke Activity/UI layer.
     * Activity harus implement interface ini untuk menerima event dari manager.
     */
    public interface ScheduleCallback {
        /** Dipanggil saat valve harus dinyalakan/dimatikan */
        void onValveSwitched(int valveIndex, boolean turnOn);

        /** Dipanggil setiap detik saat timer berjalan */
        void onCountdownTick(int valveIndex, long millisRemaining, String formattedTime);

        /** Dipanggil saat timer valve selesai */
        void onTimerFinished(int valveIndex);

        /** Dipanggil saat pompa perlu dinyalakan/dimatikan otomatis */
        void onPumpAutoControl(boolean turnOn, String statusText);

        /** Dipanggil saat jadwal berubah (disimpan/dihapus) */
        void onScheduleUpdated(int valveIndex, ScheduleConfig config);

        /** Dipanggil saat semua timer selesai */
        void onAllTimersFinished();
    }

    // ==================== CONSTRUCTOR ====================

    /**
     * Buat manager baru.
     *
     * @param context  Context untuk SharedPreferences
     * @param callback Callback untuk komunikasi ke Activity
     */
    public ValveScheduleManager(Context context, ScheduleCallback callback) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.callback = callback;
        this.schedules = new HashMap<>();
        this.activeTimers = new HashMap<>();
        this.timerActiveFlags = new HashMap<>();
        this.alarmHelper = new ScheduleAlarmHelper(context);

        // Inisialisasi flags dan empty lists
        for (int i = 1; i <= VALVE_COUNT; i++) {
            timerActiveFlags.put(i, false);
            schedules.put(i, new ArrayList<>());
        }

        // Load jadwal tersimpan
        loadSchedules();
    }

    // ==================== SCHEDULE CRUD (MULTI-SCHEDULE) ====================

    /**
     * Tambah jadwal baru untuk valve tertentu.
     * ID akan di-assign secara otomatis.
     *
     * @param valveIndex Index valve (1-4)
     * @param config     Konfigurasi jadwal baru
     */
    public void addSchedule(int valveIndex, ScheduleConfig config) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);

        if (list.size() >= MAX_SCHEDULES_PER_VALVE) {
            Log.w(TAG, "Max schedules reached for valve " + valveIndex);
            return;
        }

        // Auto-assign ID
        int nextId = 0;
        for (ScheduleConfig existing : list) {
            if (existing.getId() >= nextId) {
                nextId = existing.getId() + 1;
            }
        }
        config.setId(nextId);
        list.add(config);

        saveSchedules(valveIndex);

        // Daftarkan alarm di AlarmManager untuk jadwal baru ini
        alarmHelper.registerAlarmsForSchedule(valveIndex, config);

        callback.onScheduleUpdated(valveIndex, config);
        Log.d(TAG, "Schedule added for valve " + valveIndex + " [id=" + nextId + "]: " + config);
    }

    /**
     * Update jadwal yang sudah ada berdasarkan scheduleId.
     *
     * @param valveIndex Index valve (1-4)
     * @param config     Konfigurasi jadwal yang diupdate (harus punya id yang sama)
     */
    public void updateSchedule(int valveIndex, ScheduleConfig config) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == config.getId()) {
                // Cancel alarm lama
                alarmHelper.cancelAlarmsForSchedule(valveIndex, list.get(i));
                // Replace
                list.set(i, config);
                saveSchedules(valveIndex);
                // Daftarkan alarm baru
                alarmHelper.registerAlarmsForSchedule(valveIndex, config);
                callback.onScheduleUpdated(valveIndex, config);
                Log.d(TAG, "Schedule updated for valve " + valveIndex
                        + " [id=" + config.getId() + "]: " + config);
                return;
            }
        }
        Log.w(TAG, "Schedule id " + config.getId() + " not found for valve " + valveIndex);
    }

    /**
     * Hapus jadwal berdasarkan scheduleId.
     *
     * @param valveIndex Index valve (1-4)
     * @param scheduleId ID jadwal yang akan dihapus
     */
    public void removeSchedule(int valveIndex, int scheduleId) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == scheduleId) {
                ScheduleConfig removed = list.remove(i);
                // Cancel alarm di AlarmManager
                alarmHelper.cancelAlarmsForSchedule(valveIndex, removed);
                saveSchedules(valveIndex);
                callback.onScheduleUpdated(valveIndex, null);
                Log.d(TAG, "Schedule removed for valve " + valveIndex
                        + " [id=" + scheduleId + "]");
                return;
            }
        }
    }

    /**
     * Hapus semua jadwal valve tertentu.
     *
     * @param valveIndex Index valve (1-4)
     */
    public void removeAllSchedules(int valveIndex) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);

        // Cancel semua alarm
        for (ScheduleConfig config : list) {
            alarmHelper.cancelAlarmsForSchedule(valveIndex, config);
        }
        list.clear();
        prefs.edit().remove(KEY_SCHEDULE_PREFIX + valveIndex).apply();

        callback.onScheduleUpdated(valveIndex, null);
        Log.d(TAG, "All schedules removed for valve " + valveIndex);
    }

    /**
     * Dapatkan list semua jadwal untuk valve tertentu.
     *
     * @param valveIndex Index valve (1-4)
     * @return List of ScheduleConfig (tidak pernah null)
     */
    public List<ScheduleConfig> getScheduleList(int valveIndex) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = schedules.get(valveIndex);
        if (list == null) {
            list = new ArrayList<>();
            schedules.put(valveIndex, list);
        }
        return list;
    }

    /**
     * Dapatkan jadwal pertama valve (backward compatibility).
     *
     * @param valveIndex Index valve (1-4)
     * @return ScheduleConfig pertama atau null jika belum ada jadwal
     */
    public ScheduleConfig getSchedule(int valveIndex) {
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Dapatkan jadwal berdasarkan ID.
     *
     * @param valveIndex Index valve (1-4)
     * @param scheduleId ID jadwal
     * @return ScheduleConfig atau null jika tidak ditemukan
     */
    public ScheduleConfig getScheduleById(int valveIndex, int scheduleId) {
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        for (ScheduleConfig config : list) {
            if (config.getId() == scheduleId) {
                return config;
            }
        }
        return null;
    }

    /**
     * Cek apakah valve punya jadwal aktif (minimal satu enabled).
     */
    public boolean hasSchedule(int valveIndex) {
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        for (ScheduleConfig config : list) {
            if (config.isEnabled() && config.hasDaysSelected()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dapatkan jumlah jadwal aktif (enabled) untuk valve tertentu.
     */
    public int getActiveScheduleCount(int valveIndex) {
        int count = 0;
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        for (ScheduleConfig config : list) {
            if (config.isEnabled() && config.hasDaysSelected()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Simpan/update jadwal untuk valve tertentu (backward compat - single schedule).
     * Jika sudah ada jadwal, update yang pertama. Jika belum ada, tambahkan baru.
     *
     * @param valveIndex Index valve (1-4)
     * @param config     Konfigurasi jadwal baru
     */
    public void setSchedule(int valveIndex, ScheduleConfig config) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        if (list.isEmpty()) {
            addSchedule(valveIndex, config);
        } else {
            config.setId(list.get(0).getId());
            updateSchedule(valveIndex, config);
        }
    }

    /**
     * Hapus semua jadwal valve tertentu (backward compat).
     *
     * @param valveIndex Index valve (1-4)
     */
    public void removeSchedule(int valveIndex) {
        removeAllSchedules(valveIndex);
    }

    // ==================== TIMER CONTROL ====================

    /**
     * Jalankan valve sekarang berdasarkan sisa waktu jadwal yang sedang aktif.
     * Akan memilih jadwal yang saat ini berada dalam rentang waktu.
     *
     * @param valveIndex Index valve (1-4)
     */
    public void startValveNow(int valveIndex) {
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        for (ScheduleConfig config : list) {
            if (config.isEnabled() && config.hasValidDuration()
                    && config.isTodayScheduled() && config.isWithinTimeRange()) {
                long remainingMs = config.getRemainingDurationMs();
                if (remainingMs > 0) {
                    startValveTimer(valveIndex, remainingMs);
                    return;
                }
            }
        }
        Log.w(TAG, "Cannot start valve " + valveIndex + ": no active schedule in range");
    }

    /**
     * Jalankan valve dengan durasi tertentu (untuk quick-start tanpa jadwal).
     *
     * @param valveIndex Index valve (1-4)
     * @param durationMs Durasi dalam milidetik
     */
    public void startValveWithDuration(int valveIndex, long durationMs) {
        if (durationMs <= 0) {
            Log.w(TAG, "Duration must be > 0");
            return;
        }
        startValveTimer(valveIndex, durationMs);
    }

    /**
     * Cek apakah hari ini ada jadwal yang harus jalan, dan jalankan jika ada.
     * Hanya jalankan jika waktu sekarang berada dalam rentang jadwal.
     * Dipanggil saat app dibuka atau secara periodik.
     */
    public void checkAndRunTodaySchedules() {
        for (int i = 1; i <= VALVE_COUNT; i++) {
            if (isTimerActive(i)) continue; // Sudah ada timer aktif

            List<ScheduleConfig> list = getScheduleList(i);
            for (ScheduleConfig config : list) {
                if (config.isEnabled() && config.isTodayScheduled()
                        && config.isWithinTimeRange()) {
                    long remainingMs = config.getRemainingDurationMs();
                    if (remainingMs > 0) {
                        Log.d(TAG, "Today's schedule active for valve " + i
                                + " [id=" + config.getId() + "], remaining: "
                                + formatTime(remainingMs));
                        startValveTimer(i, remainingMs);
                        break; // Satu valve hanya satu timer pada satu waktu
                    }
                }
            }
        }
    }

    /**
     * Internal: mulai countdown timer untuk valve.
     */
    private void startValveTimer(int valveIndex, long durationMs) {
        // Jika sudah ada timer aktif untuk valve ini, cancel dulu
        stopValve(valveIndex);

        // 1. Nyalakan pompa otomatis
        ensurePumpOn();

        // 2. Nyalakan valve
        callback.onValveSwitched(valveIndex, true);

        // 3. Mulai countdown
        CountDownTimer timer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                String timeStr = formatTime(millisUntilFinished);
                callback.onCountdownTick(valveIndex, millisUntilFinished, timeStr);
            }

            @Override
            public void onFinish() {
                // Matikan valve
                callback.onValveSwitched(valveIndex, false);
                timerActiveFlags.put(valveIndex, false);
                activeTimers.remove(valveIndex);

                // Notify UI
                callback.onTimerFinished(valveIndex);

                // Cek apakah masih ada valve timer lain yang aktif
                if (!hasActiveTimers()) {
                    turnOffPumpAuto();
                    callback.onAllTimersFinished();
                }
            }
        };

        activeTimers.put(valveIndex, timer);
        timerActiveFlags.put(valveIndex, true);
        timer.start();

        Log.d(TAG, "Timer started for valve " + valveIndex + ": " + formatTime(durationMs));
    }

    /**
     * Hentikan timer untuk valve tertentu.
     *
     * @param valveIndex Index valve (1-4)
     */
    public void stopValve(int valveIndex) {
        CountDownTimer timer = activeTimers.get(valveIndex);
        if (timer != null) {
            timer.cancel();
            activeTimers.remove(valveIndex);
        }
        timerActiveFlags.put(valveIndex, false);
        callback.onValveSwitched(valveIndex, false);

        // Cek apakah masih ada valve timer lain yang aktif
        if (!hasActiveTimers()) {
            turnOffPumpAuto();
            callback.onAllTimersFinished();
        }
    }

    /**
     * Hentikan semua timer aktif.
     */
    public void stopAllValves() {
        for (int i = 1; i <= VALVE_COUNT; i++) {
            CountDownTimer timer = activeTimers.get(i);
            if (timer != null) {
                timer.cancel();
            }
            timerActiveFlags.put(i, false);
            callback.onValveSwitched(i, false);
        }
        activeTimers.clear();
        turnOffPumpAuto();
        callback.onAllTimersFinished();
    }

    /**
     * Cek apakah valve tertentu sedang punya timer aktif.
     */
    public boolean isTimerActive(int valveIndex) {
        Boolean flag = timerActiveFlags.get(valveIndex);
        return flag != null && flag;
    }

    /**
     * Cek apakah ada valve timer yang masih aktif.
     */
    public boolean hasActiveTimers() {
        for (int i = 1; i <= VALVE_COUNT; i++) {
            if (isTimerActive(i)) return true;
        }
        return false;
    }

    // ==================== PUMP AUTO CONTROL ====================

    /**
     * Pastikan pompa ON. Jika belum, nyalakan otomatis.
     */
    private void ensurePumpOn() {
        if (!pumpAutoEnabled) {
            pumpAutoEnabled = true;
            callback.onPumpAutoControl(true, "Otomatis (valve aktif)");
        }
    }

    /**
     * Matikan pompa otomatis saat semua valve selesai.
     */
    private void turnOffPumpAuto() {
        if (pumpAutoEnabled) {
            pumpAutoEnabled = false;
            callback.onPumpAutoControl(false, "Manual");
        }
    }

    /**
     * Cek apakah pompa diaktifkan otomatis.
     */
    public boolean isPumpAutoEnabled() {
        return pumpAutoEnabled;
    }

    /**
     * Set flag pompa auto (dipanggil jika pompa sudah ON manual).
     */
    public void setPumpAutoEnabled(boolean auto) {
        this.pumpAutoEnabled = auto;
    }

    // ==================== PERSISTENCE ====================

    /**
     * Simpan semua jadwal satu valve ke SharedPreferences.
     */
    private void saveSchedules(int valveIndex) {
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        String json = ScheduleConfig.listToJson(list);
        prefs.edit().putString(KEY_SCHEDULE_PREFIX + valveIndex, json).apply();
        Log.d(TAG, "Saved " + list.size() + " schedules for valve " + valveIndex);
    }

    /**
     * Simpan semua jadwal ke SharedPreferences.
     */
    public void saveAllSchedules() {
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 1; i <= VALVE_COUNT; i++) {
            List<ScheduleConfig> list = getScheduleList(i);
            if (!list.isEmpty()) {
                editor.putString(KEY_SCHEDULE_PREFIX + i, ScheduleConfig.listToJson(list));
            } else {
                editor.remove(KEY_SCHEDULE_PREFIX + i);
            }
        }
        editor.apply();
        Log.d(TAG, "All schedules saved");
    }

    /**
     * Muat semua jadwal dari SharedPreferences.
     * Backward compatible: mendukung format lama (single JSON object) dan
     * format baru (JSON array).
     */
    private void loadSchedules() {
        for (int i = 1; i <= VALVE_COUNT; i++) {
            String json = prefs.getString(KEY_SCHEDULE_PREFIX + i, null);
            List<ScheduleConfig> list = ScheduleConfig.listFromJson(json);
            if (!list.isEmpty()) {
                schedules.put(i, list);
                Log.d(TAG, "Loaded " + list.size() + " schedules for valve " + i);
            }
        }
    }

    // ==================== UTILITY ====================

    /**
     * Format milidetik ke "HH:mm:ss" atau "mm:ss".
     */
    public static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    /**
     * Dapatkan nama valve berdasarkan index.
     */
    public static String getValveName(int valveIndex) {
        switch (valveIndex) {
            case 1: return "Kran Air";
            case 2: return "Kran Insektisida";
            case 3: return "Kran Pupuk";
            case 4: return "Kran Pembuangan";
            default: return "Valve " + valveIndex;
        }
    }

    /**
     * Validasi index valve (1-4).
     */
    private void validateIndex(int valveIndex) {
        if (valveIndex < 1 || valveIndex > VALVE_COUNT) {
            throw new IllegalArgumentException(
                    "Valve index must be between 1 and " + VALVE_COUNT + ", got: " + valveIndex);
        }
    }

    /**
     * Cancel semua in-app timer. Dipanggil saat Activity onDestroy.
     * CATATAN: Ini TIDAK membatalkan alarm AlarmManager.
     * Jadwal tetap berjalan di background meskipun Activity dihancurkan.
     */
    public void cancelAllTimers() {
        for (CountDownTimer timer : activeTimers.values()) {
            if (timer != null) timer.cancel();
        }
        activeTimers.clear();
        for (int i = 1; i <= VALVE_COUNT; i++) {
            timerActiveFlags.put(i, false);
        }
    }

    /**
     * Pastikan semua alarm terdaftar di AlarmManager.
     * Dipanggil saat app dibuka untuk memastikan alarm tidak hilang.
     */
    public void ensureAlarmsRegistered() {
        alarmHelper.reRegisterAllAlarms();
    }

    /**
     * Dapatkan AlarmHelper untuk akses langsung.
     */
    public ScheduleAlarmHelper getAlarmHelper() {
        return alarmHelper;
    }

    /**
     * Mendapatkan teks ringkasan semua jadwal valve untuk ditampilkan di UI.
     * Contoh: "2 jadwal aktif" atau "📅 Sen, Rab • 08:00-10:30"
     */
    public String getScheduleSummaryText(int valveIndex) {
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        if (list.isEmpty()) {
            return "Tidak dijadwalkan";
        }

        int activeCount = getActiveScheduleCount(valveIndex);
        if (activeCount == 0) {
            return list.size() + " jadwal (nonaktif)";
        }

        if (list.size() == 1) {
            // Jika hanya 1 jadwal, tampilkan detail
            return list.get(0).getSummaryText();
        } else {
            // Jika multiple, tampilkan jumlah
            return "📅 " + activeCount + " jadwal aktif";
        }
    }
}
