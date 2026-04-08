package com.example.smartfarm.mediatanah;

import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Activity untuk monitoring dan kontrol sistem irigasi Media Tanah.
 * Menampilkan sensor kelembapan, pH tanah, dan valve kontrol.
 * Extends BaseSmartFarmActivity untuk reuse MQTT logic.
 */
public class MediaTanahActivity extends BaseSmartFarmActivity {

    private static final String TAG = "MediaTanah";

    // Sensor views
    private TextView tvKelembapan, tvPH, tvStatusKelembapan, tvStatusPH;
    private ProgressBar progressKelembapan, progressPH;

    // Switch views
    private MaterialSwitch switchKranAir, switchKranInsektisida, switchKranPupuk, switchKranPembuangan, switchSumberDaya;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_tanah);

        setupMQTT();
        initViews();
        setupSwitchListeners();
    }

    @Override
    protected String getClientId() {
        return "AndroidSmartFarm_MediaTanah";
    }

    @Override
    protected String getSubscriptionTopic() {
        return "smartfarm/sensor/#";
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        switch (topic) {
            case "smartfarm/sensor/kelembapan":
                updateKelembapan(payload);
                break;
            case "smartfarm/sensor/ph":
                updatePH(payload);
                break;
        }
    }

    private void initViews() {
        // Sensor views
        tvKelembapan = findViewById(R.id.tvKelembapan);
        tvPH = findViewById(R.id.tvPH);
        tvStatusKelembapan = findViewById(R.id.tvStatusKelembapan);
        tvStatusPH = findViewById(R.id.tvStatusPH);
        progressKelembapan = findViewById(R.id.progressKelembapan);
        progressPH = findViewById(R.id.progressPH);

        // Switch views
        switchKranAir = findViewById(R.id.switchPompa);
        switchKranInsektisida = findViewById(R.id.switchValve1);
        switchKranPupuk = findViewById(R.id.switchValve2);
        switchKranPembuangan = findViewById(R.id.switchKranPembuangan);
        switchSumberDaya = findViewById(R.id.switchSumberDaya);
    }

    private void setupSwitchListeners() {
        switchKranAir.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_air", isChecked ? "ON" : "OFF");
        });

        switchKranInsektisida.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_insektisida", isChecked ? "ON" : "OFF");
        });

        switchKranPupuk.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_pupuk", isChecked ? "ON" : "OFF");
        });

        switchKranPembuangan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/kran_pembuangan", isChecked ? "ON" : "OFF");
        });

        // Switch Sumber Daya: OFF (Left) = Listrik Rumah, ON (Right) = Panel Surya
        switchSumberDaya.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String source = isChecked ? "SOLAR" : "PLN";
            publishMQTT("smartfarm/kontrol/sumber_daya", source);
        });
    }

    private void updateKelembapan(String value) {
        try {
            float kelembapan = Float.parseFloat(value);
            int kelInt = Math.round(kelembapan);
            tvKelembapan.setText(String.valueOf(kelInt));
            progressKelembapan.setProgress(kelInt);

            if (kelembapan >= 40 && kelembapan <= 80) {
                tvStatusKelembapan.setText("Ideal");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_good));
            } else if (kelembapan < 40) {
                tvStatusKelembapan.setText("Kering");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusKelembapan.setText("Terlalu Basah");
                tvStatusKelembapan.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid kelembapan value: " + value);
        }
    }

    private void updatePH(String value) {
        try {
            float ph = Float.parseFloat(value);
            tvPH.setText(String.valueOf(ph));
            progressPH.setProgress(Math.round(ph * 10));

            if (ph >= 5.5 && ph <= 7.5) {
                tvStatusPH.setText("Ideal");
                tvStatusPH.setTextColor(getColor(R.color.status_good));
            } else if (ph < 5.5) {
                tvStatusPH.setText("Asam");
                tvStatusPH.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusPH.setText("Basa");
                tvStatusPH.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid pH value: " + value);
        }
    }
}
