package com.example.smartfarm.base;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Abstract base class untuk semua Activity SmartFarm.
 * Menyediakan shared MQTT connection logic yang bisa di-reuse oleh sub-class.
 */
public abstract class BaseSmartFarmActivity extends AppCompatActivity {

    private static final String TAG = "BaseSmartFarm";
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";

    protected MqttClient mqttClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Setiap sub-class harus menentukan Client ID MQTT unik.
     */
    protected abstract String getClientId();

    /**
     * Setiap sub-class menentukan topic yang akan di-subscribe.
     */
    protected abstract String getSubscriptionTopic();

    /**
     * Callback saat pesan MQTT diterima. Sub-class harus meng-handle message.
     */
    protected abstract void onMqttMessageReceived(String topic, String payload);

    /**
     * Setup koneksi MQTT dengan broker, subscribe ke topic, dan set callback.
     */
    protected void setupMQTT() {
        try {
            mqttClient = new MqttClient(BROKER_URL, getClientId(), null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.connect(options);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "MQTT connection lost", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    runOnUiThread(() -> onMqttMessageReceived(topic, payload));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            mqttClient.subscribe(getSubscriptionTopic());
            Log.d(TAG, "MQTT connected, subscribed to: " + getSubscriptionTopic());

        } catch (Exception e) {
            Log.e(TAG, "MQTT setup failed", e);
        }
    }

    /**
     * Publish pesan ke topic MQTT tertentu.
     */
    protected void publishMQTT(String topic, String msg) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(msg.getBytes());
                message.setQos(0);
                mqttClient.publish(topic, message);
            }
        } catch (Exception e) {
            Log.e(TAG, "MQTT publish failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "MQTT disconnect failed", e);
        }
    }
}
