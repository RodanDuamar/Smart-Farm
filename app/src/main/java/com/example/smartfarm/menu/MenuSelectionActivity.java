package com.example.smartfarm.menu;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfarm.R;
import com.example.smartfarm.device.DeviceConfig;
import com.example.smartfarm.device.DeviceListActivity;
import com.google.android.material.card.MaterialCardView;

/**
 * Activity launcher yang menampilkan pilihan menu utama.
 * User memilih kategori (Hidroponik / Media Tanah) → masuk ke daftar device.
 */
public class MenuSelectionActivity extends AppCompatActivity {

    private MaterialCardView cardHidroponik;
    private MaterialCardView cardMediaTanah;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_selection);

        initViews();
        setupListeners();
    }

    /**
     * Navigasi ke DeviceListActivity dengan tipe device tertentu.
     *
     * @param deviceType tipe device (DeviceConfig.TYPE_HIDROPONIK / TYPE_MEDIA_TANAH)
     */
    private void navigateToDeviceList(String deviceType) {
        Intent intent = new Intent(this, DeviceListActivity.class);
        intent.putExtra(DeviceListActivity.EXTRA_DEVICE_TYPE, deviceType);
        startActivity(intent);
    }

    private void initViews() {
        cardHidroponik = findViewById(R.id.cardHidroponik);
        cardMediaTanah = findViewById(R.id.cardMediaTanah);
    }

    private void setupListeners() {
        cardHidroponik.setOnClickListener(v -> {
            navigateToDeviceList(DeviceConfig.TYPE_HIDROPONIK);
        });

        cardMediaTanah.setOnClickListener(v -> {
            navigateToDeviceList(DeviceConfig.TYPE_MEDIA_TANAH);
        });
    }

}
