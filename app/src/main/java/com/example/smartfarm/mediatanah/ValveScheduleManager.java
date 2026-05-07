package com.example.smartfarm.mediatanah;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manager class yang mengelola jadwal valve.
 *
 * ARSITEKTUR:
 * - Logika penjadwalan (timer, pengecekan waktu, eksekusi ON/OFF)
 *   sepenuhnya dijalankan oleh MIKROKONTROLER.
 * - Android app hanya bertugas:
 *   1. Mengirim perintah jadwal (add/update/delete) ke MCU via MQTT
 *   2. Menyimpan salinan jadwal secara lokal (cache untuk tampilan UI)
 *   3. Menerima state update dari MCU (valve ON/OFF, pompa ON/OFF)
 *   4. Menampilkan status terkini ke pengguna
 *
 * MQTT TOPICS:
 * App → MCU (publish):
 *   smartfarm/jadwal/set    → JSON jadwal untuk disimpan di MCU
 *   smartfarm/jadwal/delete → JSON {valve, id} untuk dihapus dari MCU
 *   smartfarm/jadwal/sync   → Request MCU kirim semua jadwal terkini
 *
 * MCU → App (subscribe):
 *   smartfarm/jadwal/state  → JSON semua jadwal yang tersimpan di MCU
 *   smartfarm/status/valves → JSON status ON/OFF semua valve & pompa
 */
public class ValveScheduleManager {

    private static final String TAG = "ValveScheduleManager";
    private static final String PREFS_NAME = "valve_schedules";
    private static final String KEY_SCHEDULE_PREFIX = "schedule_valve_";
    private static final String KEY_VALVE_STATES = "valve_states";
    public static final int VALVE_COUNT = 4;
    /** Maximum jadwal per valve */
    public static final int MAX_SCHEDULES_PER_VALVE = 10;

    // ==================== MQTT TOPICS ====================

    /** App → MCU: kirim jadwal untuk disimpan di MCU */
    public static final String TOPIC_SCHEDULE_SET = "smartfarm/jadwal/set";
    /** App → MCU: hapus jadwal dari MCU */
    public static final String TOPIC_SCHEDULE_DELETE = "smartfarm/jadwal/delete";
    /** App → MCU: request sinkronisasi semua jadwal */
    public static final String TOPIC_SCHEDULE_SYNC = "smartfarm/jadwal/sync";
    /** MCU → App: semua jadwal yang tersimpan di MCU */
    public static final String TOPIC_SCHEDULE_STATE = "smartfarm/jadwal/state";
    /** MCU → App: status ON/OFF semua valve dan pompa */
    public static final String TOPIC_STATUS_VALVES = "smartfarm/status/valves";

    // ==================== STATE ====================

    /** Jadwal per valve (cache lokal, sumber kebenaran ada di MCU) */
    private final Map<Integer, List<ScheduleConfig>> schedules;

    /** Status valve terkini dari MCU (true = ON, false = OFF) */
    private final Map<Integer, Boolean> valveStates;

    /** Status pompa terkini dari MCU */
    private boolean pumpState = false;

    /** Waktu terakhir jadwal diubah secara lokal (untuk mencegah race condition MQTT) */
    private long lastLocalUpdateTime = 0;

    /** SharedPreferences untuk cache lokal */
    private final SharedPreferences prefs;

    /** Callback untuk komunikasi ke Activity */
    private final ScheduleCallback callback;

    // ==================== CALLBACK INTERFACE ====================

    /**
     * Interface untuk komunikasi dari manager ke Activity/UI layer.
     */
    public interface ScheduleCallback {
        /** Dipanggil saat perlu mengirim pesan MQTT */
        void onMqttPublishRequested(String topic, String payload);

        /** Dipanggil saat status valve berubah (dari MCU) */
        void onValveStateChanged(int valveIndex, boolean isOn);

        /** Dipanggil saat status pompa berubah (dari MCU) */
        void onPumpStateChanged(boolean isOn, String statusText);

        /** Dipanggil saat daftar jadwal berubah (dari MCU atau lokal) */
        void onScheduleListChanged(int valveIndex);

        /** Dipanggil saat mode MCU berubah (AUTO/MANUAL) */
        default void onModeChanged(String mode) {}

        /** Dipanggil saat sumber daya berubah (PLN/AKI) */
        default void onPowerSourceChanged(String source) {}
    }

    // ==================== CONSTRUCTOR ====================

    public ValveScheduleManager(Context context, ScheduleCallback callback) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.callback = callback;
        this.schedules = new HashMap<>();
        this.valveStates = new HashMap<>();

        // Inisialisasi
        for (int i = 1; i <= VALVE_COUNT; i++) {
            schedules.put(i, new ArrayList<>());
            valveStates.put(i, false);
        }

        // Load cache lokal
        loadLocalCache();
    }

    // ==================== MQTT COMMAND: App → MCU ====================

    /**
     * Kirim jadwal baru ke MCU via MQTT dan simpan di cache lokal.
     * MCU akan menyimpan jadwal dan menjalankannya secara mandiri.
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

        // Tandai waktu update lokal
        lastLocalUpdateTime = System.currentTimeMillis();

        // Simpan ke cache lokal
        list.add(config);
        saveLocalCache(valveIndex);

        // Kirim ke MCU via MQTT
        publishScheduleToMcu(valveIndex, config);

        callback.onScheduleListChanged(valveIndex);
        Log.d(TAG, "Schedule added & sent to MCU: valve " + valveIndex
                + " [id=" + nextId + "]");
    }

    /**
     * Update jadwal di MCU via MQTT.
     */
    public void updateSchedule(int valveIndex, ScheduleConfig config) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == config.getId()) {
                list.set(i, config);
                lastLocalUpdateTime = System.currentTimeMillis();
                saveLocalCache(valveIndex);

                // Kirim update ke MCU
                publishScheduleToMcu(valveIndex, config);

                callback.onScheduleListChanged(valveIndex);
                Log.d(TAG, "Schedule updated & sent to MCU: valve " + valveIndex
                        + " [id=" + config.getId() + "]");
                return;
            }
        }
        Log.w(TAG, "Schedule id " + config.getId()
                + " not found for valve " + valveIndex);
    }

    /**
     * Hapus jadwal dari MCU via MQTT.
     */
    public void removeSchedule(int valveIndex, int scheduleId) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == scheduleId) {
                list.remove(i);
                lastLocalUpdateTime = System.currentTimeMillis();
                saveLocalCache(valveIndex);

                // Kirim perintah hapus ke MCU
                publishDeleteToMcu(valveIndex, scheduleId);

                callback.onScheduleListChanged(valveIndex);
                Log.d(TAG, "Schedule deleted & sent to MCU: valve " + valveIndex
                        + " [id=" + scheduleId + "]");
                return;
            }
        }
    }

    /**
     * Hapus semua jadwal valve dari MCU.
     */
    public void removeAllSchedules(int valveIndex) {
        validateIndex(valveIndex);
        List<ScheduleConfig> list = getScheduleList(valveIndex);

        // Kirim perintah hapus semua ke MCU
        for (ScheduleConfig config : list) {
            publishDeleteToMcu(valveIndex, config.getId());
        }
        list.clear();
        lastLocalUpdateTime = System.currentTimeMillis();
        saveLocalCache(valveIndex);

        callback.onScheduleListChanged(valveIndex);
        Log.d(TAG, "All schedules removed for valve " + valveIndex);
    }

    /**
     * Request sinkronisasi jadwal dari MCU.
     * MCU akan membalas dengan publish ke TOPIC_SCHEDULE_STATE.
     */
    public void requestSyncFromMcu() {
        callback.onMqttPublishRequested(TOPIC_SCHEDULE_SYNC, "ALL");
        Log.d(TAG, "Sync request sent to MCU");
    }

    // ==================== MQTT RECEIVE: MCU → App ====================

    /**
     * Handle pesan MQTT dari MCU. Dipanggil oleh Activity saat pesan masuk.
     *
     * @param topic   MQTT topic
     * @param payload Pesan payload
     */
    public void handleMqttMessage(String topic, String payload) {
        switch (topic) {
            case TOPIC_SCHEDULE_STATE:
                handleScheduleState(payload);
                break;
            case TOPIC_STATUS_VALVES:
                handleValveStatus(payload);
                break;
        }
    }

    /**
     * Handle state jadwal dari MCU.
     * Format payload:
     * {
     *   "1": [{"id":0,"days":[2,4],"start_hour":8,...}, ...],
     *   "2": [...],
     *   "3": [...],
     *   "4": [...]
     * }
     */
    private void handleScheduleState(String payload) {
        // Jika kita baru saja memodifikasi jadwal lokal (dalam 3 detik terakhir),
        // abaikan state dari MCU karena MCU mungkin mengirim state lama yang belum terupdate.
        if (System.currentTimeMillis() - lastLocalUpdateTime < 3000) {
            Log.d(TAG, "Abaikan state dari MCU karena jadwal baru saja diupdate lokal");
            return;
        }

        try {
            JSONObject json = new JSONObject(payload);
            for (int i = 1; i <= VALVE_COUNT; i++) {
                JSONArray arr = json.optJSONArray(String.valueOf(i));
                List<ScheduleConfig> list = new ArrayList<>();
                if (arr != null) {
                    for (int j = 0; j < arr.length(); j++) {
                        ScheduleConfig config = ScheduleConfig.fromJsonObject(
                                arr.getJSONObject(j));
                        if (config != null) {
                            list.add(config);
                        }
                    }
                }
                schedules.put(i, list);
                saveLocalCache(i);
                callback.onScheduleListChanged(i);
            }
            Log.d(TAG, "Schedule state synced from MCU");
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing schedule state from MCU: " + payload, e);
        }
    }

    /**
     * Handle status valve/pompa dari MCU.
     * Format payload:
     * {
     *   "pompa": "ON",
     *   "valve_1": "ON",
     *   "valve_2": "OFF",
     *   "valve_3": "OFF",
     *   "valve_4": "OFF",
     *   "mode": "AUTO",
     *   "power": "PLN"
     * }
     */
    private void handleValveStatus(String payload) {
        try {
            JSONObject json = new JSONObject(payload);

            // Update pompa
            boolean newPumpState = "ON".equalsIgnoreCase(
                    json.optString("pompa", "OFF"));
            if (newPumpState != pumpState) {
                pumpState = newPumpState;
                String statusText = pumpState ? "Otomatis (MCU)" : "Manual";
                callback.onPumpStateChanged(pumpState, statusText);
            }

            // Update valves
            for (int i = 1; i <= VALVE_COUNT; i++) {
                boolean newState = "ON".equalsIgnoreCase(
                        json.optString("valve_" + i, "OFF"));
                Boolean oldState = valveStates.get(i);
                if (oldState == null || newState != oldState) {
                    valveStates.put(i, newState);
                    callback.onValveStateChanged(i, newState);
                }
            }

            // Update mode (AUTO/MANUAL)
            String mode = json.optString("mode", "");
            if (!mode.isEmpty()) {
                callback.onModeChanged(mode);
            }

            // Update sumber daya (PLN/AKI)
            String power = json.optString("power", "");
            if (!power.isEmpty()) {
                callback.onPowerSourceChanged(power);
            }

            Log.d(TAG, "Valve status updated from MCU: " + payload);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing valve status from MCU: " + payload, e);
        }
    }

    // ==================== MQTT PUBLISH HELPERS ====================

    /**
     * Publish jadwal ke MCU.
     * Format:
     * {
     *   "valve": 1,
     *   "schedule": {id, days, start_hour, start_minute, end_hour, end_minute, enabled}
     * }
     */
    private void publishScheduleToMcu(int valveIndex, ScheduleConfig config) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("valve", valveIndex);
            payload.put("schedule", config.toJson());
            callback.onMqttPublishRequested(TOPIC_SCHEDULE_SET, payload.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating schedule JSON", e);
        }
    }

    /**
     * Publish perintah hapus jadwal ke MCU.
     * Format: {"valve": 1, "id": 0}
     */
    private void publishDeleteToMcu(int valveIndex, int scheduleId) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("valve", valveIndex);
            payload.put("id", scheduleId);
            callback.onMqttPublishRequested(TOPIC_SCHEDULE_DELETE, payload.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating delete JSON", e);
        }
    }

    // ==================== SCHEDULE QUERY ====================

    /**
     * Dapatkan list semua jadwal untuk valve tertentu (dari cache lokal).
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
     * Dapatkan jadwal berdasarkan ID.
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
     * Cek apakah valve sedang ON (berdasarkan status dari MCU).
     */
    public boolean isValveOn(int valveIndex) {
        Boolean state = valveStates.get(valveIndex);
        return state != null && state;
    }

    /**
     * Cek apakah ada valve yang sedang ON.
     */
    public boolean hasActiveValves() {
        for (int i = 1; i <= VALVE_COUNT; i++) {
            if (isValveOn(i)) return true;
        }
        return false;
    }

    /**
     * Cek apakah pompa sedang ON.
     */
    public boolean isPumpOn() {
        return pumpState;
    }

    // ==================== LOCAL CACHE ====================

    /**
     * Simpan cache lokal satu valve.
     * Cache ini hanya untuk tampilan UI saat app dibuka.
     * Sumber kebenaran tetap ada di MCU.
     */
    private void saveLocalCache(int valveIndex) {
        List<ScheduleConfig> list = getScheduleList(valveIndex);
        String json = ScheduleConfig.listToJson(list);
        prefs.edit().putString(KEY_SCHEDULE_PREFIX + valveIndex, json).apply();
    }

    /**
     * Muat cache lokal. Dipanggil saat startup untuk menampilkan
     * jadwal terakhir yang diketahui sambil menunggu sync dari MCU.
     */
    private void loadLocalCache() {
        for (int i = 1; i <= VALVE_COUNT; i++) {
            String json = prefs.getString(KEY_SCHEDULE_PREFIX + i, null);
            List<ScheduleConfig> list = ScheduleConfig.listFromJson(json);
            if (!list.isEmpty()) {
                schedules.put(i, list);
                Log.d(TAG, "Loaded " + list.size()
                        + " cached schedules for valve " + i);
            }
        }
    }

    // ==================== UTILITY ====================

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
                    "Valve index must be between 1 and " + VALVE_COUNT
                    + ", got: " + valveIndex);
        }
    }

    /**
     * Mendapatkan teks ringkasan semua jadwal valve untuk ditampilkan di UI.
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
            return list.get(0).getSummaryText();
        } else {
            return "📅 " + activeCount + " jadwal aktif";
        }
    }

    /**
     * Format milidetik ke "HH:mm:ss" atau "mm:ss".
     */
    public static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d",
                    hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
