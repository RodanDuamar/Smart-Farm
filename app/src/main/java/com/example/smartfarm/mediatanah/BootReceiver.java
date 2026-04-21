package com.example.smartfarm.mediatanah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver yang dipanggil setelah device reboot.
 *
 * AlarmManager alarms hilang saat device restart, jadi kita perlu
 * mendaftarkan ulang semua alarm dari jadwal yang tersimpan
 * di SharedPreferences.
 *
 * Juga menangani TIME_SET dan TIMEZONE_CHANGED agar alarm
 * tetap akurat jika pengguna mengubah waktu perangkat.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case "android.intent.action.TIME_SET":
            case "android.intent.action.TIMEZONE_CHANGED":
                // Re-register semua alarm dari jadwal tersimpan
                reRegisterAlarms(context);
                break;
        }
    }

    /**
     * Daftarkan ulang semua alarm dari SharedPreferences.
     */
    private void reRegisterAlarms(Context context) {
        try {
            ScheduleAlarmHelper helper = new ScheduleAlarmHelper(context);
            helper.reRegisterAllAlarms();
            Log.d(TAG, "Successfully re-registered all schedule alarms");
        } catch (Exception e) {
            Log.e(TAG, "Failed to re-register alarms", e);
        }
    }
}
