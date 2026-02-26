package com.example.smartfarm;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText etMoisture;
    private Switch switchWatering;
    private Button btnSave;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inisialisasi View
        etMoisture = findViewById(R.id.etMoisture);
        switchWatering = findViewById(R.id.switchWatering);
        btnSave = findViewById(R.id.btnSave);

        // Inisialisasi Firestore
        db = FirebaseFirestore.getInstance();

        btnSave.setOnClickListener(v -> simpanDataKeFirestore());
    }

    private void simpanDataKeFirestore() {
        String moistureStr = etMoisture.getText().toString();

        if (moistureStr.isEmpty()) {
            Toast.makeText(this, "Masukkan nilai Soil Moisture", Toast.LENGTH_SHORT).show();
            return;
        }

        int soilMoisture = Integer.parseInt(moistureStr);
        boolean isPumpOn = switchWatering.isChecked();

        // Siapkan data yang akan dikirim
        Map<String, Object> farmData = new HashMap<>();
        farmData.put("soilMoisture", soilMoisture);
        farmData.put("pompaMenyala", isPumpOn);
        farmData.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());

        // Simpan ke collection "kontrol_penyiraman" dengan document ID "status_terkini"
        // Menggunakan set() agar data selalu di-update di satu dokumen yang sama
        db.collection("kontrol_penyiraman").document("status_terkini")
                .set(farmData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MainActivity.this, "Data berhasil dikirim ke Firestore!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Gagal mengirim data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}

