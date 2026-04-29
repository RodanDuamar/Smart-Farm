package com.example.smartfarm.hidroponik;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;
import org.json.JSONObject;

import java.util.Locale;

public class HidroponikActivity extends BaseSmartFarmActivity {

    private TextView tvTdsRealtime, tvPhRealtime, tvModeStatus;
    private TextView btnPompaA, btnPompaB, btnPompaAir, btnUpdateParameter;
    private EditText etPpmTarget, etPpmTargetMax;
    private LinearLayout layoutManualControl, layoutParameter;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchAuto;

    // State pompa lokal (untuk kontrol manual)
    private boolean isPompaAOn   = false;
    private boolean isPompaBOn   = false;
    private boolean isPompaAirOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        initViews();
        setupMQTT();
        setupControlListeners();
    }

    private void initViews() {
        tvTdsRealtime      = findViewById(R.id.tvTdsRealtime);
        tvPhRealtime       = findViewById(R.id.tvPhRealtime);
        tvModeStatus       = findViewById(R.id.tvModeStatus);
        btnPompaA          = findViewById(R.id.btnPompaA);
        btnPompaB          = findViewById(R.id.btnPompaB);
        btnPompaAir        = findViewById(R.id.btnPompaAir);
        btnUpdateParameter = findViewById(R.id.btnUpdateParameter);
        etPpmTarget        = findViewById(R.id.etPpmTarget);
        etPpmTargetMax     = findViewById(R.id.etPpmTargetMax);
        layoutManualControl = findViewById(R.id.layoutManualControl);
        layoutParameter    = findViewById(R.id.layoutParameter);
        switchAuto         = findViewById(R.id.switchAuto);
    }

    private void setupControlListeners() {

        // ── Switch Auto/Manual ─────────────────────────────────────
        // ESP32 membaca key "manual":
        //   {"manual": false} -> mode OTOMATIS
        //   {"manual": true}  -> mode MANUAL
        // Switch ON (isChecked=true) = OTOMATIS -> kirim {"manual": false}
        // Switch OFF (isChecked=false) = MANUAL  -> kirim {"manual": true}
        switchAuto.setOnCheckedChangeListener((v, isChecked) -> {
            updateUIState(isChecked);
            try {
                JSONObject json = new JSONObject();
                json.put("manual", !isChecked); // isChecked=true(auto) -> manual=false
                publishMQTT("nutrisi/control", json.toString());

                if (isChecked) {
                    // Pindah ke auto -> reset visual tombol pompa
                    resetPumpStates();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // ── Update Parameter PPM ───────────────────────────────────
        btnUpdateParameter.setOnClickListener(v -> {
            String minStr = etPpmTarget.getText().toString();
            String maxStr = etPpmTargetMax.getText().toString();

            if (minStr.isEmpty() || maxStr.isEmpty()) {
                Toast.makeText(this, "Input tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int min = Integer.parseInt(minStr);
                int max = Integer.parseInt(maxStr);

                if (min >= max) {
                    Toast.makeText(this, "PPM min harus lebih kecil dari max", Toast.LENGTH_SHORT).show();
                    return;
                }

                JSONObject json = new JSONObject();
                json.put("min", min);
                json.put("max", max);
                publishMQTT("nutrisi/set/ppm", json.toString());
                Toast.makeText(this, "Parameter Terkirim!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Gagal mengirim: Input harus angka", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Tombol Pompa (Toggle) ──────────────────────────────────
        btnPompaA.setOnClickListener(v -> {
            isPompaAOn = !isPompaAOn;
            updateButtonStyle(btnPompaA, isPompaAOn);
            sendManualCmd("dosing1", isPompaAOn);
        });

        btnPompaB.setOnClickListener(v -> {
            isPompaBOn = !isPompaBOn;
            updateButtonStyle(btnPompaB, isPompaBOn);
            sendManualCmd("dosing2", isPompaBOn);
        });

        btnPompaAir.setOnClickListener(v -> {
            isPompaAirOn = !isPompaAirOn;
            updateButtonStyle(btnPompaAir, isPompaAirOn);
            sendManualCmd("water_pump", isPompaAirOn);
        });
    }

    // ── Kirim perintah manual pompa ────────────────────────────────
    // ESP32 akan otomatis set manualMode=true saat menerima perintah pompa
    private void sendManualCmd(String key, boolean state) {
        try {
            JSONObject json = new JSONObject();
            json.put(key, state);
            publishMQTT("nutrisi/control", json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Update tampilan UI sesuai mode ─────────────────────────────
    private void updateUIState(boolean isAuto) {
        tvModeStatus.setText(isAuto ? "OTOMATIS" : "MANUAL");
        layoutManualControl.setAlpha(isAuto ? 0.4f : 1.0f);
        btnPompaA.setEnabled(!isAuto);
        btnPompaB.setEnabled(!isAuto);
        btnPompaAir.setEnabled(!isAuto);
    }

    private void updateButtonStyle(TextView view, boolean isOn) {
        view.setBackgroundResource(isOn
                ? R.drawable.bg_neumorph_card_pressed
                : R.drawable.bg_neumorph_card);
        view.setAlpha(isOn ? 0.7f : 1.0f);
    }

    private void resetPumpStates() {
        isPompaAOn = false; isPompaBOn = false; isPompaAirOn = false;
        updateButtonStyle(btnPompaA,   false);
        updateButtonStyle(btnPompaB,   false);
        updateButtonStyle(btnPompaAir, false);
    }

    // ── Terima data dari MQTT ──────────────────────────────────────
    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        try {
            JSONObject json = new JSONObject(payload);

            // 1. Data sensor real-time dari ESP32
            if (topic.equals("nutrisi/sensor")) {
                double phValue  = json.optDouble("ph", 0.0);
                int    ppmValue = json.optInt("ppm", 0);

                runOnUiThread(() -> {
                    tvPhRealtime.setText(String.format(Locale.getDefault(), "%.2f", phValue));
                    tvTdsRealtime.setText(String.valueOf(ppmValue));

                    // Sinkronisasi visual tombol pompa dengan status aktual ESP32
                    if (json.has("dosing1")) {
                        isPompaAOn = json.optBoolean("dosing1");
                        updateButtonStyle(btnPompaA, isPompaAOn);
                    }
                    if (json.has("dosing2")) {
                        isPompaBOn = json.optBoolean("dosing2");
                        updateButtonStyle(btnPompaB, isPompaBOn);
                    }
                    if (json.has("water_pump")) {
                        isPompaAirOn = json.optBoolean("water_pump");
                        updateButtonStyle(btnPompaAir, isPompaAirOn);
                    }

                    // Sinkronisasi setpoint yang tersimpan di ESP32
                    if (json.has("ppm_min"))
                        etPpmTarget.setText(String.valueOf(json.optInt("ppm_min")));
                    if (json.has("ppm_max"))
                        etPpmTargetMax.setText(String.valueOf(json.optInt("ppm_max")));
                });
            }

            // 2. Status pompa dari ESP32 (topic nutrisi/status)
            else if (topic.equals("nutrisi/status")) {
                runOnUiThread(() -> {
                    if (json.has("dosing1")) {
                        isPompaAOn = json.optBoolean("dosing1");
                        updateButtonStyle(btnPompaA, isPompaAOn);
                    }
                    if (json.has("dosing2")) {
                        isPompaBOn = json.optBoolean("dosing2");
                        updateButtonStyle(btnPompaB, isPompaBOn);
                    }
                    if (json.has("water_pump")) {
                        isPompaAirOn = json.optBoolean("water_pump");
                        updateButtonStyle(btnPompaAir, isPompaAirOn);
                    }
                });
            }

        } catch (Exception e) {
            Log.e("MQTT_ERROR", "Gagal parse JSON: " + e.getMessage());
        }
    }

    @Override
    protected String[] getSubscriptionTopics() {
        return new String[]{"nutrisi/sensor", "nutrisi/status"};
    }

    @Override
    protected String getClientId() {
        return "Android_Hidroponik_" + System.currentTimeMillis();
    }
}