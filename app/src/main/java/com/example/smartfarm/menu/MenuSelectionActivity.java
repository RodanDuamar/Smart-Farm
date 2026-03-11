package com.example.smartfarm.menu;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfarm.R;
import com.example.smartfarm.hidroponik.HidroponikActivity;
import com.example.smartfarm.mediatanah.MediaTanahActivity;
import com.google.android.material.card.MaterialCardView;

/**
 * Activity launcher yang menampilkan pilihan menu utama.
 * User dapat memilih antara mode Hidroponik atau Media Tanah.
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

    private void initViews() {
        cardHidroponik = findViewById(R.id.cardHidroponik);
        cardMediaTanah = findViewById(R.id.cardMediaTanah);
    }

    private void setupListeners() {
        cardHidroponik.setOnClickListener(v -> {
            navigateTo(HidroponikActivity.class);
        });

        cardMediaTanah.setOnClickListener(v -> {
            navigateTo(MediaTanahActivity.class);
        });
    }

    /**
     * Navigasi ke Activity target.
     * 
     * @param targetActivity class Activity tujuan
     */
    private void navigateTo(Class<?> targetActivity) {
        Intent intent = new Intent(this, targetActivity);
        startActivity(intent);
    }
}
