package com.example.smartfarm.mediatanah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Class ini sudah tidak digunakan.
 *
 * Logika penjadwalan sekarang dijalankan sepenuhnya oleh mikrokontroler (MCU).
 * Tidak ada lagi alarm dari Android yang perlu di-handle.
 *
 * Class ini dipertahankan agar AndroidManifest.xml tidak error.
 * Anda bisa menghapus file ini beserta entry-nya di AndroidManifest.xml.
 *
 * @deprecated Scheduling sekarang di MCU, bukan di Android.
 */
@Deprecated
public class ScheduleAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ScheduleAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm received but ignored - scheduling now handled by MCU");
        // No-op: semua logika penjadwalan dijalankan oleh mikrokontroler
    }
}
