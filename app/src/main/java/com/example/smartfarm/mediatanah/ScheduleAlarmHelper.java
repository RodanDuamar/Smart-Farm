package com.example.smartfarm.mediatanah;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * Helper untuk mengelola AlarmManager scheduling.
 *
 * Setiap valve bisa punya MULTIPLE jadwal (max 10 per valve).
 * Setiap jadwal bisa punya beberapa hari dalam seminggu.
 * Setiap jadwal menghasilkan 2 alarm per hari:
 *   - Alarm START: menyalakan valve + pompa
 *   - Alarm END:   mematikan valve (+ pompa jika tidak ada valve lain aktif)
 *
 * Request code formula (mendukung multiple schedules):
 *   START alarm: (valveIndex * 1000) + (scheduleId * 100) + (dayOfWeek * 10) + 1
 *   END alarm:   (valveIndex * 1000) + (scheduleId * 100) + (dayOfWeek * 10) + 2
 *
 * Ini memastikan setiap alarm punya request code unik.
 */
public class ScheduleAlarmHelper {

    private static final String TAG = "ScheduleAlarmHelper";

    /** Intent action constants */
    public static final String ACTION_VALVE_START = "com.example.smartfarm.VALVE_START";
    public static final String ACTION_VALVE_STOP = "com.example.smartfarm.VALVE_STOP";

    /** Intent extra keys */
    public static final String EXTRA_VALVE_INDEX = "valve_index";
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_DAY_OF_WEEK = "day_of_week";

    private final Context context;
    private final AlarmManager alarmManager;

    public ScheduleAlarmHelper(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    // ==================== PUBLIC API ====================

    /**
     * Daftarkan semua alarm untuk SATU jadwal (ScheduleConfig) dari satu valve.
     *
     * @param valveIndex Index valve (1-4)
     * @param config     Konfigurasi jadwal
     */
    public void registerAlarmsForSchedule(int valveIndex, ScheduleConfig config) {
        if (config == null || !config.isEnabled() || !config.hasDaysSelected()
                || !config.hasValidDuration()) {
            Log.d(TAG, "Skipping alarm registration for valve " + valveIndex
                    + " schedule " + (config != null ? config.getId() : "null")
                    + ": config invalid or disabled");
            return;
        }

        int scheduleId = config.getId();
        Set<Integer> days = config.getSelectedDays();
        for (int day : days) {
            scheduleStartAlarm(valveIndex, scheduleId, day,
                    config.getStartHour(), config.getStartMinute());
            scheduleEndAlarm(valveIndex, scheduleId, day,
                    config.getEndHour(), config.getEndMinute());
        }

        Log.d(TAG, "Registered alarms for valve " + valveIndex
                + " schedule " + scheduleId
                + ": " + days.size() + " days, time " + config.getTimeRangeDisplayText());
    }

    /**
     * Cancel semua alarm untuk SATU jadwal (ScheduleConfig).
     *
     * @param valveIndex Index valve (1-4)
     * @param config     Konfigurasi jadwal yang akan di-cancel
     */
    public void cancelAlarmsForSchedule(int valveIndex, ScheduleConfig config) {
        if (config == null) return;
        int scheduleId = config.getId();
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            cancelAlarm(getStartRequestCode(valveIndex, scheduleId, day));
            cancelAlarm(getEndRequestCode(valveIndex, scheduleId, day));
        }
        Log.d(TAG, "Cancelled alarms for valve " + valveIndex + " schedule " + scheduleId);
    }

    /**
     * Daftarkan semua alarm untuk satu valve (semua jadwalnya).
     * Backward compatibility method.
     *
     * @param valveIndex Index valve (1-4)
     * @param config     Konfigurasi jadwal (single)
     */
    public void registerAlarmsForValve(int valveIndex, ScheduleConfig config) {
        registerAlarmsForSchedule(valveIndex, config);
    }

    /**
     * Cancel semua alarm untuk satu valve (semua schedules, semua hari).
     */
    public void cancelAlarmsForValve(int valveIndex) {
        // Cancel all possible schedule ids (0-9) dan semua hari
        for (int scheduleId = 0; scheduleId < ValveScheduleManager.MAX_SCHEDULES_PER_VALVE; scheduleId++) {
            for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
                cancelAlarm(getStartRequestCode(valveIndex, scheduleId, day));
                cancelAlarm(getEndRequestCode(valveIndex, scheduleId, day));
            }
        }
        Log.d(TAG, "Cancelled all alarms for valve " + valveIndex);
    }

    /**
     * Cancel semua alarm untuk semua valve.
     */
    public void cancelAllAlarms() {
        for (int valve = 1; valve <= ValveScheduleManager.VALVE_COUNT; valve++) {
            cancelAlarmsForValve(valve);
        }
    }

    /**
     * Re-register semua alarm dari jadwal yang tersimpan di SharedPreferences.
     * Dipanggil saat:
     * - Boot completed (BootReceiver)
     * - App dibuka (untuk memastikan alarm terdaftar)
     */
    public void reRegisterAllAlarms() {
        SharedPreferences prefs = context.getSharedPreferences("valve_schedules",
                Context.MODE_PRIVATE);

        for (int i = 1; i <= ValveScheduleManager.VALVE_COUNT; i++) {
            String json = prefs.getString("schedule_valve_" + i, null);
            List<ScheduleConfig> schedules = ScheduleConfig.listFromJson(json);
            for (ScheduleConfig config : schedules) {
                if (config.isEnabled()) {
                    registerAlarmsForSchedule(i, config);
                }
            }
        }
        Log.d(TAG, "Re-registered all alarms from saved schedules");
    }

    // ==================== INTERNAL ====================

    /**
     * Schedule alarm START untuk valve + jadwal pada hari tertentu.
     */
    private void scheduleStartAlarm(int valveIndex, int scheduleId,
                                     int dayOfWeek, int hour, int minute) {
        int requestCode = getStartRequestCode(valveIndex, scheduleId, dayOfWeek);
        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        intent.setAction(ACTION_VALVE_START);
        intent.putExtra(EXTRA_VALVE_INDEX, valveIndex);
        intent.putExtra(EXTRA_SCHEDULE_ID, scheduleId);
        intent.putExtra(EXTRA_DAY_OF_WEEK, dayOfWeek);

        long triggerTimeMs = getNextTriggerTime(dayOfWeek, hour, minute);
        setWeeklyAlarm(requestCode, intent, triggerTimeMs);

        Log.d(TAG, "Scheduled START alarm: valve=" + valveIndex
                + " schedule=" + scheduleId
                + " day=" + dayOfWeek + " time=" + hour + ":" + minute
                + " requestCode=" + requestCode);
    }

    /**
     * Schedule alarm END untuk valve + jadwal pada hari tertentu.
     */
    private void scheduleEndAlarm(int valveIndex, int scheduleId,
                                   int dayOfWeek, int hour, int minute) {
        int requestCode = getEndRequestCode(valveIndex, scheduleId, dayOfWeek);
        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        intent.setAction(ACTION_VALVE_STOP);
        intent.putExtra(EXTRA_VALVE_INDEX, valveIndex);
        intent.putExtra(EXTRA_SCHEDULE_ID, scheduleId);
        intent.putExtra(EXTRA_DAY_OF_WEEK, dayOfWeek);

        long triggerTimeMs = getNextTriggerTime(dayOfWeek, hour, minute);
        setWeeklyAlarm(requestCode, intent, triggerTimeMs);

        Log.d(TAG, "Scheduled END alarm: valve=" + valveIndex
                + " schedule=" + scheduleId
                + " day=" + dayOfWeek + " time=" + hour + ":" + minute
                + " requestCode=" + requestCode);
    }

    /**
     * Set alarm exact yang berulang mingguan.
     */
    private void setWeeklyAlarm(int requestCode, Intent intent, long triggerTimeMs) {
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: cek izin exact alarm
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTimeMs, pi);
                } else {
                    // Fallback ke inexact (masih bisa jalan, tapi mungkin delay)
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerTimeMs, pi);
                    Log.w(TAG, "Exact alarm permission not granted, using inexact alarm");
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTimeMs, pi);
            }
        } catch (SecurityException e) {
            // Fallback ke inexact alarm
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTimeMs, pi);
            Log.w(TAG, "SecurityException setting exact alarm, using inexact", e);
        }
    }

    /**
     * Cancel alarm berdasarkan request code.
     */
    private void cancelAlarm(int requestCode) {
        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            alarmManager.cancel(pi);
            pi.cancel();
        }
    }

    /**
     * Hitung waktu trigger berikutnya untuk hari dan jam tertentu.
     * Jika waktunya sudah lewat minggu ini, pindah ke minggu depan.
     */
    private long getNextTriggerTime(int dayOfWeek, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        // Jika target sudah lewat, jadwalkan untuk minggu depan
        if (target.before(now) || target.equals(now)) {
            target.add(Calendar.WEEK_OF_YEAR, 1);
        }

        return target.getTimeInMillis();
    }

    // ==================== REQUEST CODE GENERATION ====================

    /**
     * Generate request code unik untuk alarm START.
     * Formula: (valveIndex * 1000) + (scheduleId * 100) + (dayOfWeek * 10) + 1
     */
    private int getStartRequestCode(int valveIndex, int scheduleId, int dayOfWeek) {
        return (valveIndex * 1000) + (scheduleId * 100) + (dayOfWeek * 10) + 1;
    }

    /**
     * Generate request code unik untuk alarm END.
     * Formula: (valveIndex * 1000) + (scheduleId * 100) + (dayOfWeek * 10) + 2
     */
    private int getEndRequestCode(int valveIndex, int scheduleId, int dayOfWeek) {
        return (valveIndex * 1000) + (scheduleId * 100) + (dayOfWeek * 10) + 2;
    }
}
