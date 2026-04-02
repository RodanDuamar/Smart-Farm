package com.example.smartfarm.hidroponik;

import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Activity placeholder untuk sistem monitoring Hidroponik.
 * Extends BaseSmartFarmActivity untuk reuse MQTT logic.
 * Fitur lengkap akan dikembangkan di versi selanjutnya.
 */
public class HidroponikActivity extends BaseSmartFarmActivity {

    private static final String TAG = "Hidroponik";

    private TextView tvNutrisiAir, tvLarutan , tvStatusNutrisiAir, tvStatusLarutan;
    private ProgressBar progressNutrisiAir, progressLarutan;
    private MaterialSwitch switchNutrisiAir, switchLarutan;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        setupMQTT();
        initViews();
        setupSwitchListeners();
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
        switch (topic) {
            case "smartfarm/hidroponik/nutrisi_air":
                updateNutrisiAir(payload);
                break;
            case "smartfarm/hidroponik/larutan":
                updateLarutan(payload);
                break;
            default:
                Log.w(TAG, "Unknown topic: " + topic);
                break;

        }
    }

    private void initViews() {
        tvNutrisiAir = findViewById(R.id.tvNutrisiAir);
        tvLarutan = findViewById(R.id.tvLarutan);
        tvStatusNutrisiAir = findViewById(R.id.tvStatusNutrisiAir);
        tvStatusLarutan = findViewById(R.id.tvStatusLarutan);
        progressNutrisiAir = findViewById(R.id.progressNutrisiAir);
        progressLarutan = findViewById(R.id.progressLarutan);
        switchNutrisiAir = findViewById(R.id.switchPompaNutrisi);
        switchLarutan = findViewById(R.id.switchPompaAir);
    }

    private void setupSwitchListeners() {
        switchNutrisiAir.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/nutrisi_air", isChecked ? "ON" : "OFF");
        });

        switchLarutan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/larutan", isChecked ? "ON" : "OFF");
        });

        }







    private void updateLarutan(String payload) {
        try {
            float pH = Float.parseFloat(payload);
            tvLarutan.setText(String.valueOf(pH));
            progressLarutan.setProgress(Math.round(pH * 10));
            if (pH >= 5.5 && pH <= 7.5) {
                tvStatusLarutan.setText("Optimal");
                tvStatusLarutan.setTextColor(getColor(R.color.status_good));
            } else if (pH < 5.5) {
                tvStatusLarutan.setText("Asam");
                tvStatusLarutan.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusLarutan.setText("Basa");
                tvStatusLarutan.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid pH value: " + payload);

        }
    }

    private void updateNutrisiAir(String payload) {
        try {
            float pH = Float.parseFloat(payload);
            tvNutrisiAir.setText(String.valueOf(pH));
            progressNutrisiAir.setProgress(Math.round(pH * 10));

            if (pH >= 5.5 && pH <= 7.5) {
                tvStatusNutrisiAir.setText("Optimal");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_good));
            } else if (pH < 5.5) {
                tvStatusNutrisiAir.setText("Asam");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusNutrisiAir.setText("Basa");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid pH value: " + payload);
        }

    }
    }





