package com.example.smartfarm.hidroponik;

import android.os.Bundle;
import android.util.Log;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;

/**
 * Activity placeholder untuk sistem monitoring Hidroponik.
 * Extends BaseSmartFarmActivity untuk reuse MQTT logic.
 * Fitur lengkap akan dikembangkan di versi selanjutnya.
 */
public class HidroponikActivity extends BaseSmartFarmActivity {

    private static final String TAG = "Hidroponik";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        // MQTT belum di-setup karena fitur masih placeholder
        // Uncomment baris di bawah saat fitur sudah siap:
        // setupMQTT();
    }

    @Override
    protected String getClientId() {
        return "AndroidSmartFarm_Hidroponik";
    }

    @Override
    protected String getSubscriptionTopic() {
        return "smartfarm/hidroponik/#";
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        // TODO: Implement saat fitur hidroponik sudah dikembangkan
        Log.d(TAG, "Message received - topic: " + topic + ", payload: " + payload);
    }
}
