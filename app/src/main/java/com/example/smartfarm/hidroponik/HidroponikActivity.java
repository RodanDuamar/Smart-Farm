package com.example.smartfarm.hidroponik;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;

import org.json.JSONObject;

import java.util.Locale;

public class HidroponikActivity extends BaseSmartFarmActivity {

    // --- DEKLARASI VARIABEL ---
    private TextView tvTdsRealtime, tvPhRealtime, tvModeStatus, tvStatusTds, tvStatusPh;
    private TextView tvTdsMin, tvTdsMax;
    private ProgressBar progressTds, progressPh;
    private SwitchCompat switchPompaA, switchPompaB, switchPompaAir;
    private EditText etPpmTarget, etPpmTargetMax;
    private View btnUpdateParameter, cardKontrolPompa;

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchAuto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        initViews();
        setupMQTT();
        setupControlListeners();
    }

    // --- SINKRONISASI SAAT APLIKASI DIBUKA ---
    @Override
    protected void onResume() {
        super.onResume();
        // Memicu sinkronisasi ulang saat aplikasi dibuka kembali
        requestStatusUpdate();
    }

    private void requestStatusUpdate() {
        try {
            // Mengirim perintah khusus agar alat mengirimkan SEMUA status termasuk min/max
            publishMQTT("nutrisi/request", "get_all_status");
            Log.d("MQTT_SYNC", "Meminta data parameter dan status ke hardware...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initViews() {
        tvTdsRealtime      = findViewById(R.id.tvTdsRealtime);
        tvPhRealtime       = findViewById(R.id.tvPhRealtime);
        tvModeStatus       = findViewById(R.id.tvModeStatus);
        tvStatusTds        = findViewById(R.id.tvStatusTds);
        tvStatusPh         = findViewById(R.id.tvStatusPh);
        tvTdsMin           = findViewById(R.id.tvTdsMin);
        tvTdsMax           = findViewById(R.id.tvTdsMax);
        progressTds        = findViewById(R.id.progressTds);
        progressPh         = findViewById(R.id.progressPh);
        cardKontrolPompa   = findViewById(R.id.cardKontrolPompa);
        switchPompaA       = findViewById(R.id.switchPompaA);
        switchPompaB       = findViewById(R.id.switchPompaB);
        switchPompaAir     = findViewById(R.id.switchPompaAir);
        etPpmTarget        = findViewById(R.id.etPpmTarget);
        etPpmTargetMax     = findViewById(R.id.etPpmTargetMax);
        btnUpdateParameter = findViewById(R.id.btnUpdateParameter);
        switchAuto         = findViewById(R.id.switchAuto);
    }

    private void setupControlListeners() {
        switchAuto.setOnCheckedChangeListener((v, isChecked) -> {
            if (v.isPressed()) {
                updateUIState(isChecked);
                publishCommand("manual", !isChecked);
            }
        });

        switchPompaA.setOnClickListener(v -> publishCommand("dosing1", switchPompaA.isChecked()));
        switchPompaB.setOnClickListener(v -> publishCommand("dosing2", switchPompaB.isChecked()));
        switchPompaAir.setOnClickListener(v -> publishCommand("water_pump", switchPompaAir.isChecked()));

        btnUpdateParameter.setOnClickListener(v -> {
            String min = etPpmTarget.getText().toString();
            String max = etPpmTargetMax.getText().toString();

            if (min.isEmpty() || max.isEmpty()) {
                Toast.makeText(this, "Isi semua parameter!", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int valMin = Integer.parseInt(min);
                int valMax = Integer.parseInt(max);

                JSONObject json = new JSONObject();
                json.put("min", valMin);
                json.put("max", valMax);

                // Publish ke topik set agar alat menyimpan nilai baru
                publishMQTT("nutrisi/set/ppm", json.toString());

                tvTdsMin.setText(min);
                tvTdsMax.setText(max);

                Toast.makeText(this, "Parameter Nutrisi Diperbarui!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("UI_ERROR", "Error update parameter: " + e.getMessage());
            }
        });
    }

    private void publishCommand(String key, boolean state) {
        try {
            JSONObject json = new JSONObject();
            json.put(key, state);
            publishMQTT("nutrisi/control", json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUIState(boolean isAuto) {
        runOnUiThread(() -> {
            tvModeStatus.setText(isAuto ? "OTOMATIS" : "MANUAL");
            if (cardKontrolPompa != null) {
                cardKontrolPompa.setAlpha(isAuto ? 0.5f : 1.0f);
            }
            switchPompaA.setEnabled(!isAuto);
            switchPompaB.setEnabled(!isAuto);
            switchPompaAir.setEnabled(!isAuto);
        });
    }

    @Override
    protected String getBrokerUrl() {
        return "tcp://broker.hivemq.com:1883";
    }

    @Override
    protected String getClientId() {
        return "Android_Hidroponik_Sleman_" + System.currentTimeMillis();
    }

    @Override
    protected String[] getSubscriptionTopics() {
        return new String[]{"nutrisi/sensor", "nutrisi/status"};
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        runOnUiThread(() -> {
            try {
                JSONObject json = new JSONObject(payload);

                // 1. SINKRONISASI NILAI MIN/MAX (Parameter Alat)
                if (json.has("min")) {
                    String minVal = String.valueOf(json.optInt("min"));
                    tvTdsMin.setText(minVal);
                    // Update input jika user sedang tidak fokus mengetik
                    if (!etPpmTarget.isFocused()) {
                        etPpmTarget.setText(minVal);
                    }
                }
                if (json.has("max")) {
                    String maxVal = String.valueOf(json.optInt("max"));
                    tvTdsMax.setText(maxVal);
                    if (!etPpmTargetMax.isFocused()) {
                        etPpmTargetMax.setText(maxVal);
                    }
                }

                // 2. Handling Data Sensor
                if (json.has("ppm")) {
                    int ppm = json.optInt("ppm");
                    tvTdsRealtime.setText(String.valueOf(ppm));
                    progressTds.setProgress(ppm);
                    tvStatusTds.setText(ppm < 800 ? "RENDAH" : (ppm <= 1200 ? "NORMAL" : "TINGGI"));
                }

                if (json.has("ph")) {
                    double ph = json.optDouble("ph");
                    tvPhRealtime.setText(String.format(Locale.getDefault(), "%.1f", ph));
                    progressPh.setProgress((int) (ph * 10));
                    tvStatusPh.setText((ph >= 5.5 && ph <= 6.5) ? "IDEAL" : "TIDAK STABIL");
                }

                // 3. Sinkronisasi Status Saklar & Mode
                if (json.has("manual")) {
                    boolean isManual = json.optBoolean("manual");
                    switchAuto.setChecked(!isManual);
                    updateUIState(!isManual);
                }

                if (json.has("dosing1")) switchPompaA.setChecked(json.optBoolean("dosing1"));
                if (json.has("dosing2")) switchPompaB.setChecked(json.optBoolean("dosing2"));
                if (json.has("water_pump")) switchPompaAir.setChecked(json.optBoolean("water_pump"));

            } catch (Exception e) {
                Log.e("MQTT_PARSE", "Gagal sinkronisasi: " + e.getMessage());
            }
        });
    }
}