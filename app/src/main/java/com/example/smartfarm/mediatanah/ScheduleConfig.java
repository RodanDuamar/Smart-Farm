package com.example.smartfarm.mediatanah;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Model class yang merepresentasikan konfigurasi jadwal satu valve.
 *
 * Menyimpan:
 * - Hari-hari yang dijadwalkan (Set of Calendar day constants)
 * - Jam mulai dan jam selesai (rentang waktu menyala)
 * - Status aktif/nonaktif
 *
 * Mendukung serialisasi JSON untuk penyimpanan di SharedPreferences.
 */
public class ScheduleConfig {

    private static final String TAG = "ScheduleConfig";

    // JSON keys
    private static final String KEY_DAYS = "days";
    private static final String KEY_START_HOUR = "start_hour";
    private static final String KEY_START_MINUTE = "start_minute";
    private static final String KEY_END_HOUR = "end_hour";
    private static final String KEY_END_MINUTE = "end_minute";
    private static final String KEY_ENABLED = "enabled";

    /** Hari-hari yang dipilih (Calendar.SUNDAY=1 .. Calendar.SATURDAY=7) */
    private final Set<Integer> selectedDays;

    /** Jam mulai valve menyala */
    private int startHour;
    private int startMinute;

    /** Jam selesai valve mati */
    private int endHour;
    private int endMinute;

    /** Apakah jadwal ini aktif */
    private boolean enabled;

    // ==================== NAMA HARI (Bahasa Indonesia) ====================

    private static final String[] DAY_NAMES_SHORT = {
            "", "Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"
    };

    private static final String[] DAY_NAMES_FULL = {
            "", "Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"
    };

    // ==================== CONSTRUCTORS ====================

    /**
     * Buat jadwal baru dengan default (kosong, 08:00-08:30, disabled).
     */
    public ScheduleConfig() {
        this.selectedDays = new LinkedHashSet<>();
        this.startHour = 8;
        this.startMinute = 0;
        this.endHour = 8;
        this.endMinute = 30;
        this.enabled = false;
    }

    /**
     * Buat jadwal baru dengan parameter lengkap.
     *
     * @param selectedDays Set hari yang dijadwalkan (Calendar constants)
     * @param startHour    Jam mulai (0-23)
     * @param startMinute  Menit mulai (0-59)
     * @param endHour      Jam selesai (0-23)
     * @param endMinute    Menit selesai (0-59)
     * @param enabled      Apakah jadwal aktif
     */
    public ScheduleConfig(Set<Integer> selectedDays, int startHour, int startMinute,
            int endHour, int endMinute, boolean enabled) {
        this.selectedDays = new LinkedHashSet<>(selectedDays);
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
        this.enabled = enabled;
    }

    // ==================== GETTERS & SETTERS ====================

    public Set<Integer> getSelectedDays() {
        return selectedDays;
    }

    public void addDay(int dayOfWeek) {
        selectedDays.add(dayOfWeek);
    }

    public void removeDay(int dayOfWeek) {
        selectedDays.remove(dayOfWeek);
    }

    public void toggleDay(int dayOfWeek) {
        if (selectedDays.contains(dayOfWeek)) {
            selectedDays.remove(dayOfWeek);
        } else {
            selectedDays.add(dayOfWeek);
        }
    }

    public int getStartHour() {
        return startHour;
    }

    public void setStartHour(int startHour) {
        this.startHour = startHour;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public void setStartMinute(int startMinute) {
        this.startMinute = startMinute;
    }

    public int getEndHour() {
        return endHour;
    }

    public void setEndHour(int endHour) {
        this.endHour = endHour;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public void setEndMinute(int endMinute) {
        this.endMinute = endMinute;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Hitung total durasi dalam milidetik dari rentang waktu.
     * Jika end < start, diasumsikan melewati tengah malam (cross-midnight).
     */
    public long getTotalDurationMs() {
        int startTotalMinutes = startHour * 60 + startMinute;
        int endTotalMinutes = endHour * 60 + endMinute;

        int diffMinutes;
        if (endTotalMinutes > startTotalMinutes) {
            diffMinutes = endTotalMinutes - startTotalMinutes;
        } else if (endTotalMinutes < startTotalMinutes) {
            // Cross midnight: misal 23:00 - 01:00 = 2 jam
            diffMinutes = (24 * 60 - startTotalMinutes) + endTotalMinutes;
        } else {
            // Sama persis = 0
            return 0;
        }

        return diffMinutes * 60L * 1000L;
    }

    /**
     * Hitung sisa durasi dari sekarang sampai jam selesai (dalam ms).
     * Digunakan saat valve baru dimulai di tengah jadwal.
     */
    public long getRemainingDurationMs() {
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int endMinutes = endHour * 60 + endMinute;

        int diffMinutes;
        if (endMinutes > nowMinutes) {
            diffMinutes = endMinutes - nowMinutes;
        } else if (endMinutes < nowMinutes) {
            // Cross midnight
            diffMinutes = (24 * 60 - nowMinutes) + endMinutes;
        } else {
            return 0;
        }

        return diffMinutes * 60L * 1000L;
    }

    /**
     * Cek apakah rentang waktu valid (start != end).
     */
    public boolean hasValidDuration() {
        return getTotalDurationMs() > 0;
    }

    /**
     * Cek apakah waktu sekarang berada dalam rentang jadwal.
     */
    public boolean isWithinTimeRange() {
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = startHour * 60 + startMinute;
        int endMinutes = endHour * 60 + endMinute;

        if (endMinutes > startMinutes) {
            // Normal range (misal 08:00 - 10:00)
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        } else if (endMinutes < startMinutes) {
            // Cross-midnight (misal 23:00 - 01:00)
            return nowMinutes >= startMinutes || nowMinutes < endMinutes;
        }
        return false;
    }

    /**
     * Cek apakah hari tertentu dijadwalkan.
     *
     * @param dayOfWeek Calendar.SUNDAY .. Calendar.SATURDAY
     */
    public boolean isDayScheduled(int dayOfWeek) {
        return selectedDays.contains(dayOfWeek);
    }

    /**
     * Cek apakah hari ini dijadwalkan.
     */
    public boolean isTodayScheduled() {
        int today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return isDayScheduled(today);
    }

    /**
     * Cek apakah ada hari yang dipilih.
     */
    public boolean hasDaysSelected() {
        return !selectedDays.isEmpty();
    }

    /**
     * Mendapatkan teks singkat untuk menampilkan hari-hari yang dijadwalkan.
     * Contoh: "Sen, Rab, Jum" atau "Setiap Hari"
     */
    public String getDaysDisplayText() {
        if (selectedDays.isEmpty()) {
            return "Belum dipilih";
        }

        // Cek apakah semua hari dipilih
        if (selectedDays.size() == 7) {
            return "Setiap Hari";
        }

        // Urutkan hari dimulai dari Senin
        int[] dayOrder = {
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        };

        StringBuilder sb = new StringBuilder();
        for (int day : dayOrder) {
            if (selectedDays.contains(day)) {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(DAY_NAMES_SHORT[day]);
            }
        }
        return sb.toString();
    }

    /**
     * Mendapatkan teks rentang waktu yang diformat.
     * Contoh: "08:00 - 10:30"
     */
    public String getTimeRangeDisplayText() {
        return String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d",
                startHour, startMinute, endHour, endMinute);
    }

    /**
     * Mendapatkan ringkasan jadwal untuk ditampilkan di bawah nama valve.
     * Contoh: "📅 Sen, Rab, Jum • 08:00-10:30"
     */
    public String getSummaryText() {
        if (!enabled || selectedDays.isEmpty()) {
            return "Tidak dijadwalkan";
        }
        return "📅 " + getDaysDisplayText() + " • " + getTimeRangeDisplayText();
    }

    /**
     * Dapatkan nama lengkap hari berdasarkan Calendar constant.
     */
    public static String getDayFullName(int dayOfWeek) {
        if (dayOfWeek >= Calendar.SUNDAY && dayOfWeek <= Calendar.SATURDAY) {
            return DAY_NAMES_FULL[dayOfWeek];
        }
        return "";
    }

    /**
     * Dapatkan nama singkat hari berdasarkan Calendar constant.
     */
    public static String getDayShortName(int dayOfWeek) {
        if (dayOfWeek >= Calendar.SUNDAY && dayOfWeek <= Calendar.SATURDAY) {
            return DAY_NAMES_SHORT[dayOfWeek];
        }
        return "";
    }

    // ==================== SERIALISASI JSON ====================

    /**
     * Konversi ke JSON untuk penyimpanan.
     */
    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            JSONArray daysArray = new JSONArray();
            for (int day : selectedDays) {
                daysArray.put(day);
            }
            json.put(KEY_DAYS, daysArray);
            json.put(KEY_START_HOUR, startHour);
            json.put(KEY_START_MINUTE, startMinute);
            json.put(KEY_END_HOUR, endHour);
            json.put(KEY_END_MINUTE, endMinute);
            json.put(KEY_ENABLED, enabled);
            return json;
        } catch (JSONException e) {
            Log.e(TAG, "Error converting to JSON", e);
            return new JSONObject();
        }
    }

    /**
     * Buat ScheduleConfig dari JSON string.
     *
     * @param jsonString String JSON yang disimpan di SharedPreferences
     * @return ScheduleConfig atau null jika gagal parse
     */
    public static ScheduleConfig fromJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(jsonString);
            ScheduleConfig config = new ScheduleConfig();

            JSONArray daysArray = json.optJSONArray(KEY_DAYS);
            if (daysArray != null) {
                for (int i = 0; i < daysArray.length(); i++) {
                    config.addDay(daysArray.getInt(i));
                }
            }

            config.setStartHour(json.optInt(KEY_START_HOUR, 8));
            config.setStartMinute(json.optInt(KEY_START_MINUTE, 0));
            config.setEndHour(json.optInt(KEY_END_HOUR, 8));
            config.setEndMinute(json.optInt(KEY_END_MINUTE, 30));
            config.setEnabled(json.optBoolean(KEY_ENABLED, false));

            return config;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + jsonString, e);
            return null;
        }
    }

    @Override
    public String toString() {
        return "ScheduleConfig{" +
                "days=" + getDaysDisplayText() +
                ", time=" + getTimeRangeDisplayText() +
                ", enabled=" + enabled +
                '}';
    }
}
