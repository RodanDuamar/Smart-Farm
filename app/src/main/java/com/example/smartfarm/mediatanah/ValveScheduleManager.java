package com.example.smartfarm.mediatanah;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manager class yang mengelola semua jadwal dan timer valve.
 *
 * Tanggung jawab:
 * - CRUD jadwal per valve (4 valve)
 * - Menyimpan/memuat jadwal ke/dari SharedPreferences
 * - Mengelola CountDownTimer per valve
 * - Mengatur logika pompa otomatis (ON saat valve aktif, OFF saat semua selesai)
 * - Comunicating state changes ke Activity via ScheduleCallback
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

    /** Jadwal konfigurasi per valve (index 1-4) */
    private final Map<Integer, ScheduleConfig> schedules;

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

        // Inisialisasi flags
        for (int i = 1; i <= VALVE_COUNT; i++) {
            timerActiveFlags.put(i, false);
        }

        // Load jadwal tersimpan
        loadSchedules();
    }

    // ==================== SCHEDULE CRUD ====================

    /**
     * Simpan/update jadwal untuk valve tertentu.
     *
     * @param valveIndex Index valve (1-4)
     * @param config     Konfigurasi jadwal baru
     */
    public void setSchedule(int valveIndex, ScheduleConfig config) {
        validateIndex(valveIndex);
        schedules.put(valveIndex, config);
        saveSchedule(valveIndex);
        callback.onScheduleUpdated(valveIndex, config);
        Log.d(TAG, "Schedule set for valve " + valveIndex + ": " + config);
    }

    /**
     * Hapus jadwal valve tertentu.
     *
     * @param valveIndex Index valve (1-4)
     */
    public void removeSchedule(int valveIndex) {
        validateIndex(valveIndex);
        schedules.remove(valveIndex);
        prefs.edit().remove(KEY_SCHEDULE_PREFIX + valveIndex).apply();
        callback.onScheduleUpdated(valveIndex, null);
        Log.d(TAG, "Schedule removed for valve " + valveIndex);
    }

    /**
     * Dapatkan jadwal valve tertentu.
     *
     * @param valveIndex Index valve (1-4)
     * @return ScheduleConfig atau null jika belum ada jadwal
     */
    public ScheduleConfig getSchedule(int valveIndex) {
        validateIndex(valveIndex);
        return schedules.get(valveIndex);
    }

    /**
     * Cek apakah valve punya jadwal aktif.
     */
    public boolean hasSchedule(int valveIndex) {
        ScheduleConfig config = schedules.get(valveIndex);
        return config != null && config.isEnabled() && config.hasDaysSelected();
    }

    // ==================== TIMER CONTROL ====================

    /**
     * Jalankan valve sekarang berdasarkan sisa waktu sampai jam selesai.
     * Pompa akan otomatis menyala.
     *
     * @param valveIndex Index valve (1-4)
     */
    public void startValveNow(int valveIndex) {
        ScheduleConfig config = schedules.get(valveIndex);
        if (config == null || !config.hasValidDuration()) {
            Log.w(TAG, "Cannot start valve " + valveIndex + ": no valid config");
            return;
        }
        // Hitung sisa durasi dari sekarang sampai jam selesai
        long remainingMs = config.getRemainingDurationMs();
        if (remainingMs <= 0) {
            Log.w(TAG, "No remaining time for valve " + valveIndex);
            return;
        }
        startValveTimer(valveIndex, remainingMs);
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
            ScheduleConfig config = schedules.get(i);
            if (config != null && config.isEnabled() && config.isTodayScheduled()) {
                if (!isTimerActive(i) && config.isWithinTimeRange()) {
                    long remainingMs = config.getRemainingDurationMs();
                    if (remainingMs > 0) {
                        Log.d(TAG, "Today's schedule active for valve " + i
                                + ", remaining: " + formatTime(remainingMs));
                        startValveTimer(i, remainingMs);
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
     * Simpan jadwal satu valve ke SharedPreferences.
     */
    private void saveSchedule(int valveIndex) {
        ScheduleConfig config = schedules.get(valveIndex);
        if (config != null) {
            String json = config.toJson().toString();
            prefs.edit().putString(KEY_SCHEDULE_PREFIX + valveIndex, json).apply();
            Log.d(TAG, "Saved schedule for valve " + valveIndex);
        }
    }

    /**
     * Simpan semua jadwal ke SharedPreferences.
     */
    public void saveAllSchedules() {
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 1; i <= VALVE_COUNT; i++) {
            ScheduleConfig config = schedules.get(i);
            if (config != null) {
                editor.putString(KEY_SCHEDULE_PREFIX + i, config.toJson().toString());
            } else {
                editor.remove(KEY_SCHEDULE_PREFIX + i);
            }
        }
        editor.apply();
        Log.d(TAG, "All schedules saved");
    }

    /**
     * Muat semua jadwal dari SharedPreferences.
     */
    private void loadSchedules() {
        for (int i = 1; i <= VALVE_COUNT; i++) {
            String json = prefs.getString(KEY_SCHEDULE_PREFIX + i, null);
            ScheduleConfig config = ScheduleConfig.fromJson(json);
            if (config != null) {
                schedules.put(i, config);
                Log.d(TAG, "Loaded schedule for valve " + i + ": " + config);
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
     * Cancel semua timer. Dipanggil saat Activity onDestroy.
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
}
