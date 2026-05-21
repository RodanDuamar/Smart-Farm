package com.example.smartfarm.schedule;

import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;

/**
 * Receiver untuk mengeksekusi penjadwalan irigasi via MQTT.
 * Dipicu oleh AlarmManager untuk publish payload "ON" lalu "OFF".
 */
public class SmartFarmScheduleReceiver extends BroadcastReceiver {

    public static final String EXTRA_TOPICS = "extra_topics";
    public static final String EXTRA_PAYLOAD = "extra_payload";
    private static final String TAG = "SmartFarmScheduleRcvr";
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";

    @Override
    public void onReceive(Context context, Intent intent) {
        ArrayList<String> topics = intent.getStringArrayListExtra(EXTRA_TOPICS);
        String payload = intent.getStringExtra(EXTRA_PAYLOAD);

        if (topics == null || topics.isEmpty() || payload == null) {
            return;
        }

        // Copy supaya aman kalau list berubah.
        ArrayList<String> topicsCopy = new ArrayList<>(topics);
        String payloadCopy = payload;

        PendingResult pendingResult = goAsync();
        new Thread(() -> {
            MqttClient client = null;
            try {
                String clientId = "AndroidSmartFarm_Scheduler_" + System.currentTimeMillis();
                client = new MqttClient(BROKER_URL, clientId, null);

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                client.connect(options);

                for (String topic : topicsCopy) {
                    if (topic == null || topic.trim().isEmpty()) continue;

                    MqttMessage message = new MqttMessage(payloadCopy.getBytes());
                    message.setQos(0);
                    client.publish(topic, message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Schedule publish failed", e);
            } finally {
                try {
                    if (client != null && client.isConnected()) {
                        client.disconnect();
                    }
                } catch (Exception ignored) {
                }

                pendingResult.finish();
            }
        }).start();
    }
}

