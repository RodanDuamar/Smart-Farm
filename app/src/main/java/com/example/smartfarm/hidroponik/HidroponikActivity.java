package com.example.smartfarm.hidroponik;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.smartfarm.R;
import com.example.smartfarm.base.BaseSmartFarmActivity;

import org.json.JSONObject;

public class HidroponikActivity extends BaseSmartFarmActivity {

    private TextView tvTdsRealtime, tvPhRealtime, tvModeStatus;
    private TextView btnPompaA, btnPompaB, btnPompaAir, btnUpdateParameter;
    private EditText etPpmTarget, etPpmTargetMax;
    private LinearLayout layoutManualControl, layoutParameter;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchAuto;

    private final Handler handler = new Handler();
    private Runnable safetyTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidroponik);

        initViews();
        setupMQTT();
        setupModeControl();
        setupPumpActions();

        // Initial state sync
        updateUIState(switchAuto.isChecked());
    }

    private void initViews() {
        tvTdsRealtime = findViewById(R.id.tvTdsRealtime);
        tvPhRealtime = findViewById(R.id.tvPhRealtime);
        tvModeStatus = findViewById(R.id.tvModeStatus);

        btnPompaA = findViewById(R.id.btnPompaA);
        btnPompaB = findViewById(R.id.btnPompaB);
        btnPompaAir = findViewById(R.id.btnPompaAir);
        btnUpdateParameter = findViewById(R.id.btnUpdateParameter);

        etPpmTarget = findViewById(R.id.etPpmTarget);
        etPpmTargetMax = findViewById(R.id.etPpmTargetMax);

        layoutManualControl = findViewById(R.id.layoutManualControl);
        layoutParameter = findViewById(R.id.layoutParameter);
        switchAuto = findViewById(R.id.switchAuto);
    }

    private void setupModeControl() {
        switchAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvModeStatus.setText(isChecked ? "OTOMATIS" : "MANUAL");
            updateUIState(isChecked);

            try {
                JSONObject json = new JSONObject();
                json.put("auto", isChecked);
                publishMQTT("nutrisi/control", json.toString());

                if (isChecked) sendPumpCommand(false, false, false);
            } catch (Exception e) { e.printStackTrace(); }
        });

        btnUpdateParameter.setOnClickListener(v -> {
            String min = etPpmTarget.getText().toString();
            String max = etPpmTargetMax.getText().toString();
            if (min.isEmpty() || max.isEmpty()) return;

            try {
                JSONObject json = new JSONObject();
                json.put("min", Integer.parseInt(min));
                json.put("max", Integer.parseInt(max));
                publishMQTT("nutrisi/set/ppm", json.toString());
                Toast.makeText(this, "Target PPM Disimpan", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void updateUIState(boolean isAuto) {
        boolean manualEnabled = !isAuto;
        float alpha = manualEnabled ? 1.0f : 0.4f;

        // Kunci Kontrol Manual
        layoutManualControl.setAlpha(alpha);
        btnPompaA.setEnabled(manualEnabled);
        btnPompaB.setEnabled(manualEnabled);
        btnPompaAir.setEnabled(manualEnabled);

        // Kunci Input Parameter
        layoutParameter.setAlpha(alpha);
        etPpmTarget.setEnabled(manualEnabled);
        etPpmTargetMax.setEnabled(manualEnabled);
        btnUpdateParameter.setEnabled(manualEnabled);

        if (isAuto) {
            Toast.makeText(this, "Mode Otomatis Aktif: Manual Dikunci", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPumpActions() {
        btnPompaA.setOnTouchListener((v, event) -> handleTouch(event, v, true, false, false));
        btnPompaB.setOnTouchListener((v, event) -> handleTouch(event, v, false, true, false));
        btnPompaAir.setOnTouchListener((v, event) -> handleTouch(event, v, false, false, true));
    }

    private boolean handleTouch(MotionEvent event, android.view.View v, boolean pA, boolean pB, boolean pAir) {
        if (!v.isEnabled()) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            v.setPressed(true);
            sendPumpCommand(pA, pB, pAir);
            safetyTask = () -> sendPumpCommand(false, false, false);
            handler.postDelayed(safetyTask, 30000);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            v.setPressed(false);
            sendPumpCommand(false, false, false);
            handler.removeCallbacks(safetyTask);
            return true;
        }
        return false;
    }

    private void sendPumpCommand(boolean a, boolean b, boolean air) {
        try {
            JSONObject json = new JSONObject();
            json.put("dosing1", a);
            json.put("dosing2", b);
            json.put("water_pump", air);
            publishMQTT("nutrisi/control", json.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onMqttMessageReceived(String topic, String payload) {
        runOnUiThread(() -> {
            try {
                JSONObject json = new JSONObject(payload);
                tvTdsRealtime.setText(String.valueOf(json.optInt("ppm", 0)));
                tvPhRealtime.setText(String.format("%.2f", json.optDouble("ph", 0.0)));
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @Override protected String getSubscriptionTopic() { return "nutrisi/sensor"; }
    @Override protected String getClientId() { return "SmartFarm_" + System.currentTimeMillis(); }
}