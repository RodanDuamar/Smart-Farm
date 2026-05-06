package com.example.smartfarm.base;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Base Activity untuk semua Activity yang membutuhkan koneksi MQTT.
 *
 * Mendukung konfigurasi broker, credential, dan subscription per Activity:
 * - getBrokerUrl()          → URL broker MQTT (wajib override)
 * - getMqttUsername()       → username (opsional, default null = tanpa auth)
 * - getMqttPassword()       → password (opsional, default null = tanpa auth)
 * - getSubscriptionTopics() → topik MQTT yang di-subscribe (wajib override)
 *
 * Ini memungkinkan Hidroponik dan Media Tanah menggunakan broker/credential berbeda.
 */
public abstract class BaseSmartFarmActivity extends AppCompatActivity {

    private static final String TAG = "BaseSmartFarm";

    // Ganti MqttClient -> MqttAsyncClient agar tidak blokir UI thread
    protected MqttAsyncClient mqttClient;

    protected abstract String   getClientId();
    protected abstract String[] getSubscriptionTopics();
    protected abstract String   getBrokerUrl();
    protected abstract void     onMqttMessageReceived(String topic, String payload);

    /**
     * Override untuk menambahkan username MQTT. Default: null (tanpa auth).
     */
    protected String getMqttUsername() { return null; }

    /**
     * Override untuk menambahkan password MQTT. Default: null (tanpa auth).
     */
    protected String getMqttPassword() { return null; }

    protected void onMqttConnected() {}

    protected void setupMQTT() {
        try {
            // MemoryPersistence agar tidak perlu storage permission
            mqttClient = new MqttAsyncClient(getBrokerUrl(), getClientId(), new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);

            // Set credential jika tersedia
            String username = getMqttUsername();
            String password = getMqttPassword();
            if (username != null && !username.isEmpty()) {
                options.setUserName(username);
                if (password != null) {
                    options.setPassword(password.toCharArray());
                }
            }

            // Gunakan MqttCallbackExtended agar connectComplete dipanggil
            // BAIK saat connect pertama MAUPUN saat auto-reconnect
            mqttClient.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Log.d(TAG, (reconnect ? "Reconnected" : "Connected") + " to " + serverURI);
                    // Re-subscribe setiap kali connect/reconnect
                    subscribeToTopics();
                    runOnUiThread(() -> onMqttConnected());
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "Koneksi terputus: " +
                            (cause != null ? cause.getMessage() : "unknown"));
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "Pesan masuk [" + topic + "]: " + payload);
                    // Pastikan update UI di main thread
                    runOnUiThread(() -> onMqttMessageReceived(topic, payload));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            // Connect secara async — tidak blokir UI thread
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // connectComplete callback akan handle subscribe & onMqttConnected
                    Log.d(TAG, "MQTT connect initiated successfully");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "MQTT Connect gagal: " + exception.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "MQTT setup failed: " + e.getMessage(), e);
        }
    }

    // Subscribe dipanggil setelah connect berhasil (bukan sebelum)
    private void subscribeToTopics() {
        try {
            for (String topic : getSubscriptionTopics()) {
                mqttClient.subscribe(topic, 1, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "Subscribe OK: " + topic);
                    }
                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Subscribe gagal: " + topic + " - " + exception.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Subscribe error: " + e.getMessage(), e);
        }
    }

    protected void publishMQTT(String topic, String msg) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(msg.getBytes());
                message.setQos(1);
                mqttClient.publish(topic, message);
                Log.d(TAG, "Publish [" + topic + "]: " + msg);
            } else {
                Log.w(TAG, "Publish gagal: MQTT belum terkoneksi");
            }
        } catch (Exception e) {
            Log.e(TAG, "Publish failed: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                Log.d(TAG, "MQTT Disconnected");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}