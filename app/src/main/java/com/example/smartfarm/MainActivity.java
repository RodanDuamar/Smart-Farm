package com.example.smartfarm;

import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;

public class MainActivity extends AppCompatActivity {

    private MqttClient mqttClient;
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";
    private static final String CLIENT_ID = "AndroidAppSmartFarm";

    // Sensor views
    private TextView tvKelembapan, tvPH, tvStatusKelembapan, tvStatusPH;
    private ProgressBar progressKelembapan, progressPH;

    // Switch views
    private MaterialSwitch switchKranAir, switchKranInsektisida, switchKranPupuk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize sensor views
        tvKelembapan = findViewById(R.id.tvKelembapan);
        tvPH = findViewById(R.id.tvPH);
        tvStatusKelembapan = findViewById(R.id.tvStatusKelembapan);
        tvStatusPH = findViewById(R.id.tvStatusPH);
        progressKelembapan = findViewById(R.id.progressKelembapan);
        progressPH = findViewById(R.id.progressPH);

        // Initialize switches
        switchKranAir = findViewById(R.id.switchKranAir);
        switchKranInsektisida = findViewById(R.id.switchKranInsektisida);
        switchKranPupuk = findViewById(R.id.switchKranPupuk);

        // Setup switch listeners
        switchKranAir.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_air", isChecked ? "ON" : "OFF");
        });

        switchKranInsektisida.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_insektisida", isChecked ? "ON" : "OFF");
        });

        switchKranPupuk.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_pupuk", isChecked ? "ON" : "OFF");
        });

        // Setup MQTT
        setupMQTT();
    }

    private void setupMQTT() {
        try {
            mqttClient = new MqttClient(BROKER_URL, CLIENT_ID, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.connect(options);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    runOnUiThread(() -> {
                        switch (topic) {
                            case "smartfarm/sensor/kelembapan":
                                updateKelembapan(payload);
                                break;
                            case "smartfarm/sensor/ph":
                                updatePH(payload);
                                break;
                        }
                    });
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            mqttClient.subscribe("smartfarm/sensor/#");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateKelembapan(String value) {
        try {
            float kelembapan = Float.parseFloat(value);
            int kelInt = Math.round(kelembapan);
            tvKelembapan.setText(String.valueOf(kelInt));
            progressKelembapan.setProgress(kelInt);

            // Update status
            if (kelembapan >= 40 && kelembapan <= 80) {
                tvStatusKelembapan.setText("Optimal");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_good));
            } else if (kelembapan < 40) {
                tvStatusKelembapan.setText("Kering");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusKelembapan.setText("Terlalu Basah");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e("MainActivity", "Invalid kelembapan value: " + value);
        }
    }

    private void updatePH(String value) {
        try {
            float ph = Float.parseFloat(value);
            tvPH.setText(String.valueOf(ph));
            // pH scale 0-14, progress max is 140 (for decimal precision)
            progressPH.setProgress(Math.round(ph * 10));

            // Update status
            if (ph >= 5.5 && ph <= 7.5) {
                tvStatusPH.setText("Optimal");
                tvStatusPH.setTextColor(getColor(R.color.status_good));
            } else if (ph < 5.5) {
                tvStatusPH.setText("Asam");
                tvStatusPH.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusPH.setText("Basa");
                tvStatusPH.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e("MainActivity", "Invalid pH value: " + value);
        }
    }

    private void publishMQTT(String topic, String msg) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(msg.getBytes());
                message.setQos(0);
                mqttClient.publish(topic, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
