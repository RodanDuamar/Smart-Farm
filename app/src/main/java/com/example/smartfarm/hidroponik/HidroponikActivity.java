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

    private TextView tvNutrisiAir, tvPhAir, tvStatusPhAir, tvStatusNutrisiAir;
    private ProgressBar progressPhAir, progressNutrisiAir, progressVitaminA, progressVitaminB;
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
                updatePhAir(payload);
                break;
            default:
                Log.w(TAG, "Unknown topic: " + topic);
                break;
        }
    }

    private void initViews() {
        tvPhAir = findViewById(R.id.tvPhAir);
        tvNutrisiAir = findViewById(R.id.tvNutrisiAir);
        progressPhAir = findViewById(R.id.progressPhAir);
        progressNutrisiAir = findViewById(R.id.progressNutrisiAir);
        tvStatusPhAir = findViewById(R.id.tvStatusPhAir);
        tvStatusNutrisiAir = findViewById(R.id.tvStatusNutrisiAir);

//        progressVitaminA = findViewById(R.id.progressVitaminA);
//        progressVitaminB = findViewById(R.id.progressVitaminB);

    }

    private void setupSwitchListeners() {
        switchNutrisiAir.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/nutrisi_air", isChecked ? "ON" : "OFF");
        });

        switchLarutan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            publishMQTT("smartfarm/kontrol/larutan", isChecked ? "ON" : "OFF");
        });

        }


    private void updatePhAir(String payload) {
        try {
            float pH = Float.parseFloat(payload);
            tvPhAir.setText(String.valueOf(pH));
            progressVitaminA.setProgress(Math.round(pH * 10));
            if (pH >= 5.5 && pH <= 6.5) {
                tvStatusPhAir.setText("Ideal");
                tvStatusPhAir.setTextColor(getColor(R.color.status_good));
            } else if (pH < 5.5) {
                tvStatusPhAir.setText("Asam");
                tvStatusPhAir.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusPhAir.setText("Basa");
                tvStatusPhAir.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid pH Air value: " + payload);

        }
    }

    private void updateNutrisiAir(String payload) {
        try {
            float pH = Float.parseFloat(payload);
            tvNutrisiAir.setText(String.valueOf(pH));
            progressNutrisiAir.setProgress(Math.round(pH * 10));

            if (pH >= 800 && pH <= 1500) {
                tvStatusNutrisiAir.setText("Ideal");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_good));
            } else if (pH < 800) {
                tvStatusNutrisiAir.setText("Kurang Nutrisi");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_danger));
            } else {
                tvStatusNutrisiAir.setText("Berlebihan Nutrisi");
                tvStatusNutrisiAir.setTextColor(getColor(R.color.status_warning));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid Nutrisi value: " + payload);
        }

    }
    }





