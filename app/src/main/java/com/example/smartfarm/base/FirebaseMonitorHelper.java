package com.example.smartfarm.base;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper singleton untuk menyimpan data sensor ke Cloud Firestore.
 *
 * ARSITEKTUR:
 * - Data sensor yang masuk via MQTT di-log ke Firestore untuk monitoring/history.
 * - Setiap collection (sensor_media_tanah, sensor_hidroponik) memiliki:
 *   1. Document "latest" → selalu di-overwrite dengan data terbaru
 *   2. Subcollection "history" → log historis, di-throttle max 1x per menit
 *
 * PENGGUNAAN:
 *   FirebaseMonitorHelper helper = FirebaseMonitorHelper.getInstance();
 *   helper.logSensorData("sensor_media_tanah", "kelembapan", 65);
 *   helper.logSensorData("sensor_media_tanah", "ph", 6.5);
 *   helper.logSensorData("sensor_media_tanah", "suhu", 28.0);
 *
 * Data diakumulasi di memory dan ditulis ke "latest" setiap kali logSensorData() dipanggil.
 * History ditulis max 1x per HISTORY_THROTTLE_MS.
 */
public class FirebaseMonitorHelper {

    private static final String TAG = "FirebaseMonitor";

    /** Throttle history write: max 1x per 60 detik per collection */
    private static final long HISTORY_THROTTLE_MS = 60_000;

    private static FirebaseMonitorHelper instance;

    private final FirebaseFirestore db;

    /** Akumulator data sensor per collection (sebelum ditulis ke Firestore) */
    private final Map<String, Map<String, Object>> sensorAccumulator;

    /** Timestamp terakhir history ditulis per collection */
    private final Map<String, Long> lastHistoryWrite;

    private FirebaseMonitorHelper() {
        db = FirebaseFirestore.getInstance();
        sensorAccumulator = new HashMap<>();
        lastHistoryWrite = new HashMap<>();
    }

    public static synchronized FirebaseMonitorHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseMonitorHelper();
        }
        return instance;
    }

    /**
     * Log satu field sensor ke Firestore.
     * Data diakumulasi dan ditulis ke document "latest".
     * History ditulis max 1x per HISTORY_THROTTLE_MS.
     *
     * @param collection Nama collection Firestore (misal: "sensor_media_tanah")
     * @param key        Nama field sensor (misal: "kelembapan", "ph", "suhu")
     * @param value      Nilai sensor
     */
    public void logSensorData(String collection, String key, Object value) {
        // Akumulasi data
        Map<String, Object> data = sensorAccumulator.get(collection);
        if (data == null) {
            data = new HashMap<>();
            sensorAccumulator.put(collection, data);
        }
        data.put(key, value);

        // Buat salinan data + timestamp untuk ditulis
        Map<String, Object> writeData = new HashMap<>(data);
        writeData.put("timestamp", FieldValue.serverTimestamp());

        // 1. Selalu update "latest"
        updateLatest(collection, writeData);

        // 2. Throttled push ke "history"
        pushHistoryThrottled(collection, writeData);
    }

    /**
     * Log data non-sensor (misal: tanggal_tanam) ke document "latest" saja.
     * Tidak ditulis ke history.
     *
     * @param collection Nama collection Firestore
     * @param key        Nama field
     * @param value      Nilai
     */
    public void logMetadata(String collection, String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        data.put("timestamp", FieldValue.serverTimestamp());

        DocumentReference latestRef = db.collection(collection).document("latest");
        latestRef.update(data)
                .addOnFailureListener(e -> {
                    // Jika document belum ada, buat baru
                    latestRef.set(data)
                            .addOnFailureListener(setErr ->
                                    Log.e(TAG, "Metadata set gagal [" + collection + "]: " + setErr.getMessage()));
                });

        Log.d(TAG, "Metadata logged [" + collection + "." + key + "]: " + value);
    }

    /**
     * Update document "latest" dengan data terbaru (merge).
     */
    private void updateLatest(String collection, Map<String, Object> data) {
        DocumentReference latestRef = db.collection(collection).document("latest");

        // Gunakan set() dengan merge agar field yang tidak di-update tetap ada
        latestRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Latest updated [" + collection + "]"))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Latest update gagal [" + collection + "]: " + e.getMessage()));
    }

    /**
     * Push data ke subcollection "history" dengan throttle.
     * Max 1x per HISTORY_THROTTLE_MS per collection.
     */
    private void pushHistoryThrottled(String collection, Map<String, Object> data) {
        long now = System.currentTimeMillis();
        Long lastWrite = lastHistoryWrite.get(collection);

        if (lastWrite != null && (now - lastWrite) < HISTORY_THROTTLE_MS) {
            // Masih dalam periode throttle, skip
            return;
        }

        // Tulis ke history
        lastHistoryWrite.put(collection, now);

        db.collection(collection)
                .document("latest")
                .collection("history")
                .add(data)
                .addOnSuccessListener(docRef ->
                        Log.d(TAG, "History pushed [" + collection + "]: " + docRef.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "History push gagal [" + collection + "]: " + e.getMessage()));
    }
}
