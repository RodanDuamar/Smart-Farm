package com.example.smartfarm.mediatanah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.smartfarm.base.NotificationHelper;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Calendar;

/**
 * BroadcastReceiver yang menerima alarm dari AlarmManager.
 *
 * Ketika alarm berbunyi (baik START maupun STOP), receiver ini:
 * 1. Membuat koneksi MQTT baru (karena tidak ada Activity yang hidup)
 * 2. Mengirim perintah ON/OFF ke topic MQTT yang sesuai
 * 3. Mengirim notifikasi ke pengguna
 * 4. Menjadwalkan ulang alarm untuk minggu depan (karena AlarmManager
 *    setExact hanya sekali jalan)
 *
 * Receiver ini bisa berjalan TANPA Activity karena menggunakan context sendiri.
 */
public class ScheduleAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ScheduleAlarmReceiver";
    private static final String BROKER_URL = "tcp://broker.emqx.io:1883";

    /** SharedPreferences key untuk tracking valve yang sedang ON */
    private static final String PREFS_ACTIVE_VALVES = "active_valves_state";
    private static final String KEY_VALVE_ACTIVE_PREFIX = "valve_active_";
    private static final String KEY_PUMP_AUTO = "pump_auto_active";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        int valveIndex = intent.getIntExtra(ScheduleAlarmHelper.EXTRA_VALVE_INDEX, -1);
        int dayOfWeek = intent.getIntExtra(ScheduleAlarmHelper.EXTRA_DAY_OF_WEEK, -1);

        if (valveIndex < 1 || valveIndex > ValveScheduleManager.VALVE_COUNT) {
            Log.e(TAG, "Invalid valve index: " + valveIndex);
            return;
        }

        Log.d(TAG, "Alarm received: action=" + action + " valve=" + valveIndex + " day=" + dayOfWeek);

        // Pastikan notification channels sudah dibuat
        NotificationHelper.createNotificationChannels(context);

        switch (action) {
            case ScheduleAlarmHelper.ACTION_VALVE_START:
                handleValveStart(context, valveIndex, dayOfWeek);
                break;
            case ScheduleAlarmHelper.ACTION_VALVE_STOP:
                handleValveStop(context, valveIndex, dayOfWeek);
                break;
        }

        // Re-schedule alarm ini untuk minggu depan
        rescheduleForNextWeek(context, valveIndex, dayOfWeek, action);
    }

    /**
     * Handle START alarm: nyalakan pompa + valve via MQTT.
     */
    private void handleValveStart(Context context, int valveIndex, int dayOfWeek) {
        String valveName = ValveScheduleManager.getValveName(valveIndex);
        Log.d(TAG, "Starting valve " + valveIndex + " (" + valveName + ")");

        // Track state: valve ON
        setValveActiveState(context, valveIndex, true);

        // Kirim perintah MQTT di background thread
        new Thread(() -> {
            MqttClient client = null;
            try {
                client = createMqttClient();
                if (client != null && client.isConnected()) {
                    // Nyalakan pompa dulu
                    publishMessage(client, "smartfarm/kontrol/pompa", "ON");
                    setPumpAutoState(context, true);
                    Thread.sleep(500); // Tunggu pompa stabil

                    // Nyalakan valve
                    String topic = getValveMqttTopic(valveIndex);
                    publishMessage(client, topic, "ON");

                    Log.d(TAG, "MQTT: Pump ON + " + valveName + " ON");
                }
            } catch (Exception e) {
                Log.e(TAG, "MQTT error during valve start", e);
            } finally {
                disconnectMqtt(client);
            }
        }).start();

        // Kirim notifikasi
        NotificationHelper.sendWarningNotification(
                context,
                NotificationHelper.CHANNEL_MEDIA_TANAH,
                4000 + valveIndex,
                "⏱ Jadwal " + valveName + " Dimulai",
                valveName + " telah dinyalakan otomatis sesuai jadwal. "
                        + "Pompa air juga dinyalakan.",
                MediaTanahActivity.class);
    }

    /**
     * Handle STOP alarm: matikan valve, cek apakah pompa perlu dimatikan.
     */
    private void handleValveStop(Context context, int valveIndex, int dayOfWeek) {
        String valveName = ValveScheduleManager.getValveName(valveIndex);
        Log.d(TAG, "Stopping valve " + valveIndex + " (" + valveName + ")");

        // Track state: valve OFF
        setValveActiveState(context, valveIndex, false);

        new Thread(() -> {
            MqttClient client = null;
            try {
                client = createMqttClient();
                if (client != null && client.isConnected()) {
                    // Matikan valve
                    String topic = getValveMqttTopic(valveIndex);
                    publishMessage(client, topic, "OFF");

                    // Cek apakah masih ada valve lain yang aktif
                    if (!hasAnyActiveValve(context)) {
                        Thread.sleep(500);
                        publishMessage(client, "smartfarm/kontrol/pompa", "OFF");
                        setPumpAutoState(context, false);
                        Log.d(TAG, "All valves off, pump turned OFF");
                    }

                    Log.d(TAG, "MQTT: " + valveName + " OFF");
                }
            } catch (Exception e) {
                Log.e(TAG, "MQTT error during valve stop", e);
            } finally {
                disconnectMqtt(client);
            }
        }).start();

        // Kirim notifikasi
        NotificationHelper.sendWarningNotification(
                context,
                NotificationHelper.CHANNEL_MEDIA_TANAH,
                4000 + valveIndex,
                "✅ Jadwal " + valveName + " Selesai",
                valveName + " telah dimatikan otomatis setelah jadwal selesai.",
                MediaTanahActivity.class);
    }

    /**
     * Re-schedule alarm untuk minggu depan (karena setExact hanya 1x trigger).
     */
    private void rescheduleForNextWeek(Context context, int valveIndex, int dayOfWeek, String action) {
        // Cek apakah jadwal masih enabled
        SharedPreferences prefs = context.getSharedPreferences("valve_schedules",
                Context.MODE_PRIVATE);
        String json = prefs.getString("schedule_valve_" + valveIndex, null);
        ScheduleConfig config = ScheduleConfig.fromJson(json);

        if (config == null || !config.isEnabled() || !config.isDayScheduled(dayOfWeek)) {
            Log.d(TAG, "Schedule no longer valid, not rescheduling valve "
                    + valveIndex + " day " + dayOfWeek);
            return;
        }

        // Daftarkan kembali alarm ini untuk minggu depan
        ScheduleAlarmHelper helper = new ScheduleAlarmHelper(context);
        helper.registerAlarmsForValve(valveIndex, config);

        Log.d(TAG, "Rescheduled alarm for valve " + valveIndex
                + " day " + dayOfWeek + " for next week");
    }

    // ==================== MQTT HELPERS ====================

    /**
     * Buat koneksi MQTT baru. Digunakan di background thread.
     */
    private MqttClient createMqttClient() {
        try {
            String clientId = "SmartFarm_Alarm_" + System.currentTimeMillis();
            MqttClient client = new MqttClient(BROKER_URL, clientId, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("ardana_garden");
            options.setPassword("rahasia1234".toCharArray());
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            client.connect(options);
            return client;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MQTT client", e);
            return null;
        }
    }

    private void publishMessage(MqttClient client, String topic, String payload) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1); // QoS 1 untuk reliability
            message.setRetained(false);
            client.publish(topic, message);
        } catch (Exception e) {
            Log.e(TAG, "MQTT publish failed: " + topic + " = " + payload, e);
        }
    }

    private void disconnectMqtt(MqttClient client) {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "MQTT disconnect failed", e);
        }
    }

    /**
     * Dapatkan MQTT topic untuk valve tertentu.
     */
    private String getValveMqttTopic(int valveIndex) {
        switch (valveIndex) {
            case 1: return "smartfarm/kontrol/kran_air";
            case 2: return "smartfarm/kontrol/kran_insektisida";
            case 3: return "smartfarm/kontrol/kran_pupuk";
            case 4: return "smartfarm/kontrol/kran_pembuangan";
            default: return "smartfarm/kontrol/valve_" + valveIndex;
        }
    }

    // ==================== STATE TRACKING ====================

    /**
     * Simpan state valve aktif/tidak di SharedPreferences.
     * Digunakan untuk menentukan apakah pompa perlu dimatikan.
     */
    private void setValveActiveState(Context context, int valveIndex, boolean active) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ACTIVE_VALVES,
                Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_VALVE_ACTIVE_PREFIX + valveIndex, active).apply();
    }

    private void setPumpAutoState(Context context, boolean active) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ACTIVE_VALVES,
                Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PUMP_AUTO, active).apply();
    }

    /**
     * Cek apakah ada valve yang sedang aktif.
     */
    private boolean hasAnyActiveValve(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ACTIVE_VALVES,
                Context.MODE_PRIVATE);
        for (int i = 1; i <= ValveScheduleManager.VALVE_COUNT; i++) {
            if (prefs.getBoolean(KEY_VALVE_ACTIVE_PREFIX + i, false)) {
                return true;
            }
        }
        return false;
    }
}
