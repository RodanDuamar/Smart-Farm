package com.example.smartfarm.mediatanah;

/**
 * Class ini sudah tidak digunakan.
 *
 * Logika penjadwalan sekarang dijalankan sepenuhnya oleh mikrokontroler (MCU).
 * Aplikasi Android hanya mengirim konfigurasi jadwal via MQTT ke MCU.
 * MCU yang menyimpan dan mengeksekusi jadwal secara mandiri.
 *
 * Class ini dipertahankan agar tidak terjadi error kompilasi pada file
 * yang masih mereferensikannya. Anda bisa menghapus file ini beserta
 * entry-nya di AndroidManifest.xml jika sudah tidak diperlukan.
 *
 * @deprecated Gunakan ValveScheduleManager yang berkomunikasi via MQTT ke MCU.
 */
@Deprecated
public class ScheduleAlarmHelper {

    public ScheduleAlarmHelper(android.content.Context context) {
        // No-op: scheduling sekarang di MCU
    }

    public void registerAlarmsForSchedule(int valveIndex, ScheduleConfig config) {
        // No-op
    }

    public void registerAlarmsForValve(int valveIndex, ScheduleConfig config) {
        // No-op
    }

    public void cancelAlarmsForSchedule(int valveIndex, ScheduleConfig config) {
        // No-op
    }

    public void cancelAlarmsForValve(int valveIndex) {
        // No-op
    }

    public void cancelAllAlarms() {
        // No-op
    }

    public void reRegisterAllAlarms() {
        // No-op
    }
}
