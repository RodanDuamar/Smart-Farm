package com.example.smartfarm.base;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.smartfarm.R;

/**
 * Helper class untuk mengelola notifikasi monitoring SmartFarm.
 * Membuat notification channel dan mengirim notifikasi peringatan
 * ketika kondisi sensor di luar batas ideal.
 *
 * Menggunakan cooldown mechanism agar notifikasi tidak spam.
 */
public class NotificationHelper {

    // Notification Channel IDs
    public static final String CHANNEL_MEDIA_TANAH = "channel_media_tanah";
    public static final String CHANNEL_HIDROPONIK = "channel_hidroponik";

    // Notification IDs
    public static final int NOTIF_KELEMBAPAN_RENDAH = 1001;
    public static final int NOTIF_KELEMBAPAN_TINGGI = 1002;
    public static final int NOTIF_PH_TANAH_ASAM = 1003;
    public static final int NOTIF_PH_TANAH_BASA = 1004;
    public static final int NOTIF_SUHU_PANAS = 1005;
    public static final int NOTIF_SUHU_DINGIN = 1006;
    public static final int NOTIF_PH_AIR_ASAM = 2001;
    public static final int NOTIF_PH_AIR_BASA = 2002;
    public static final int NOTIF_NUTRISI_KURANG = 2003;
    public static final int NOTIF_NUTRISI_BERLEBIH = 2004;

    // Cooldown: minimal 60 detik antar notifikasi sejenis
    private static final long COOLDOWN_MS = 60_000;

    // Timestamps terakhir notifikasi dikirim (per ID)
    private static final java.util.Map<Integer, Long> lastNotifTime = new java.util.HashMap<>();

    /**
     * Inisialisasi Notification Channels. Panggil di onCreate() Activity.
     */
    public static void createNotificationChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        // Channel Media Tanah
        NotificationChannel channelTanah = new NotificationChannel(
                CHANNEL_MEDIA_TANAH,
                "Peringatan Media Tanah",
                NotificationManager.IMPORTANCE_HIGH
        );
        channelTanah.setDescription("Notifikasi ketika sensor media tanah di luar batas ideal");
        channelTanah.enableVibration(true);
        channelTanah.setVibrationPattern(new long[]{0, 300, 200, 300});
        manager.createNotificationChannel(channelTanah);

        // Channel Hidroponik
        NotificationChannel channelHidro = new NotificationChannel(
                CHANNEL_HIDROPONIK,
                "Peringatan Hidroponik",
                NotificationManager.IMPORTANCE_HIGH
        );
        channelHidro.setDescription("Notifikasi ketika sensor hidroponik di luar batas ideal");
        channelHidro.enableVibration(true);
        channelHidro.setVibrationPattern(new long[]{0, 300, 200, 300});
        manager.createNotificationChannel(channelHidro);
    }

    /**
     * Mengirim notifikasi peringatan dengan mekanisme cooldown.
     *
     * @param context       Context aplikasi
     * @param channelId     Channel ID (CHANNEL_MEDIA_TANAH atau CHANNEL_HIDROPONIK)
     * @param notifId       Notification ID unik
     * @param title         Judul notifikasi
     * @param message       Isi pesan notifikasi
     * @param targetActivity Activity yang dibuka saat notifikasi di-tap
     */
    public static void sendWarningNotification(
            Context context,
            String channelId,
            int notifId,
            String title,
            String message,
            Class<?> targetActivity
    ) {
        // Cek cooldown
        long now = System.currentTimeMillis();
        Long lastTime = lastNotifTime.get(notifId);
        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            return; // Masih dalam cooldown, skip notifikasi
        }
        lastNotifTime.put(notifId, now);

        // Intent untuk membuka Activity ketika notifikasi di-tap
        Intent intent = new Intent(context, targetActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Pilih ikon berdasarkan channel
        int iconRes;
        if (CHANNEL_MEDIA_TANAH.equals(channelId)) {
            iconRes = R.drawable.ic_app_logo;
        } else {
            iconRes = R.drawable.ic_hydroponics;
        }

        // Build notifikasi
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE);

        // Kirim notifikasi
        try {
            NotificationManagerCompat notifManager = NotificationManagerCompat.from(context);
            notifManager.notify(notifId, builder.build());
        } catch (SecurityException e) {
            // Permission POST_NOTIFICATIONS belum diberikan
            android.util.Log.w("NotificationHelper", "Notification permission not granted", e);
        }
    }

    /**
     * Membatalkan notifikasi berdasarkan ID (misalnya ketika kondisi sudah kembali normal).
     */
    public static void cancelNotification(Context context, int notifId) {
        NotificationManagerCompat notifManager = NotificationManagerCompat.from(context);
        notifManager.cancel(notifId);
        lastNotifTime.remove(notifId);
    }
}
