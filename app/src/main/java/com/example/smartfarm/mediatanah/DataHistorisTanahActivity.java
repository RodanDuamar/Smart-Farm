package com.example.smartfarm.mediatanah;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfarm.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity untuk menampilkan data historis sensor Media Tanah.
 *
 * Mengambil 50 data terakhir dari Firestore collection:
 *   sensor_media_tanah → latest → history
 *
 * Menampilkan 3 line chart:
 *   1. Kelembapan Tanah (%)   — warna hijau
 *   2. Suhu Udara (°C)        — warna oranye
 *   3. pH Tanah               — warna ungu
 */
public class DataHistorisTanahActivity extends AppCompatActivity {

    private static final String TAG = "DataHistoris";
    private static final String FIREBASE_COLLECTION = "sensor_media_tanah";
    private static final int MAX_DATA_POINTS = 50;

    // Views
    private LinearLayout layoutLoading, layoutEmpty, layoutCharts;
    private TextView tvSubtitle;
    private LineChart chartKelembapan, chartSuhu, chartPH;

    // Data lists
    private final List<Entry> entriesKelembapan = new ArrayList<>();
    private final List<Entry> entriesSuhu = new ArrayList<>();
    private final List<Entry> entriesPH = new ArrayList<>();
    private final List<String> timeLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_historis_tanah);

        initViews();
        setupCharts();
        loadHistoryData();
    }

    private void initViews() {
        // Toolbar back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvSubtitle = findViewById(R.id.tvSubtitle);
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutCharts = findViewById(R.id.layoutCharts);

        // Charts
        chartKelembapan = findViewById(R.id.chartKelembapan);
        chartSuhu = findViewById(R.id.chartSuhu);
        chartPH = findViewById(R.id.chartPH);
    }

    // ==================== CHART SETUP ====================

    private void setupCharts() {
        setupLineChart(chartKelembapan);
        setupLineChart(chartSuhu);
        setupLineChart(chartPH);
    }

    /**
     * Konfigurasi umum untuk semua LineChart.
     * Menghasilkan tampilan bersih tanpa grid yang berlebihan.
     */
    private void setupLineChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setExtraBottomOffset(8f);

        // Legend
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextSize(11f);
        chart.getLegend().setTextColor(Color.parseColor("#757575"));

        // X Axis — tampilkan label waktu di bawah
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(9f);
        xAxis.setTextColor(Color.parseColor("#9E9E9E"));
        xAxis.setLabelRotationAngle(-45);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < timeLabels.size()) {
                    return timeLabels.get(index);
                }
                return "";
            }
        });

        // Y Axis (left)
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#F0F0F0"));
        leftAxis.setTextSize(10f);
        leftAxis.setTextColor(Color.parseColor("#757575"));

        // Y Axis (right) — hide
        chart.getAxisRight().setEnabled(false);
    }

    // ==================== DATA LOADING ====================

    /**
     * Ambil data historis dari Firestore.
     * Path: sensor_media_tanah/latest/history
     * Diurutkan berdasarkan timestamp ascending (terlama dulu → terbaru)
     * Limit: 50 data terakhir
     */
    private void loadHistoryData() {
        showLoading();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(FIREBASE_COLLECTION)
                .document("latest")
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(MAX_DATA_POINTS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        showEmpty();
                        return;
                    }

                    // Reverse agar data ditampilkan dari lama ke baru
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        docs.add(doc);
                    }
                    java.util.Collections.reverse(docs);

                    parseAndDisplayData(docs);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Gagal memuat data historis: " + e.getMessage(), e);
                    showEmpty();
                });
    }

    /**
     * Parse data Firestore dan populate chart entries.
     */
    private void parseAndDisplayData(List<QueryDocumentSnapshot> docs) {
        entriesKelembapan.clear();
        entriesSuhu.clear();
        entriesPH.clear();
        timeLabels.clear();

        SimpleDateFormat sdf = new SimpleDateFormat("d/MM HH:mm", Locale.getDefault());

        int index = 0;
        for (QueryDocumentSnapshot doc : docs) {
            // Timestamp
            Timestamp timestamp = doc.getTimestamp("timestamp");
            if (timestamp != null) {
                Date date = timestamp.toDate();
                timeLabels.add(sdf.format(date));
            } else {
                timeLabels.add("");
            }

            // Kelembapan
            if (doc.contains("kelembapan")) {
                double kelembapan = doc.getDouble("kelembapan") != null
                        ? doc.getDouble("kelembapan") : 0;
                entriesKelembapan.add(new Entry(index, (float) kelembapan));
            }

            // Suhu
            if (doc.contains("suhu")) {
                double suhu = doc.getDouble("suhu") != null
                        ? doc.getDouble("suhu") : 0;
                entriesSuhu.add(new Entry(index, (float) suhu));
            }

            // pH
            if (doc.contains("ph")) {
                double ph = doc.getDouble("ph") != null
                        ? doc.getDouble("ph") : 0;
                entriesPH.add(new Entry(index, (float) ph));
            }

            index++;
        }

        // Update subtitle
        tvSubtitle.setText("Menampilkan " + docs.size() + " data terakhir");

        // Populate charts
        if (!entriesKelembapan.isEmpty()) {
            setChartData(chartKelembapan, entriesKelembapan,
                    "Kelembapan Tanah (%)",
                    Color.parseColor("#4CAF50"),   // line color (green)
                    Color.parseColor("#1A4CAF50")); // fill color (green transparent)
        }

        if (!entriesSuhu.isEmpty()) {
            setChartData(chartSuhu, entriesSuhu,
                    "Suhu Udara (°C)",
                    Color.parseColor("#FF9800"),    // line color (orange)
                    Color.parseColor("#1AFF9800")); // fill color (orange transparent)
        }

        if (!entriesPH.isEmpty()) {
            setChartData(chartPH, entriesPH,
                    "pH Tanah",
                    Color.parseColor("#7B1FA2"),    // line color (purple)
                    Color.parseColor("#1A7B1FA2")); // fill color (purple transparent)
        }

        showCharts();
    }

    /**
     * Set data ke LineChart dengan styling sesuai gambar referensi.
     * - Line dengan fill area di bawahnya
     * - Dot markers pada data points
     * - Smooth curve
     */
    private void setChartData(LineChart chart, List<Entry> entries,
                               String label, int lineColor, int fillColor) {
        LineDataSet dataSet = new LineDataSet(entries, label);

        // Line styling
        dataSet.setColor(lineColor);
        dataSet.setLineWidth(2f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // smooth curve

        // Fill area under line
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(lineColor);
        dataSet.setFillAlpha(25);

        // Circle markers
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(lineColor);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setCircleHoleRadius(1.5f);

        // Value labels — hide to keep chart clean
        dataSet.setDrawValues(false);

        // Highlight
        dataSet.setHighLightColor(lineColor);
        dataSet.setHighlightLineWidth(1f);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.animateX(800);
        chart.invalidate();
    }

    // ==================== STATE MANAGEMENT ====================

    private void showLoading() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        layoutCharts.setVisibility(View.GONE);
    }

    private void showEmpty() {
        layoutLoading.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        layoutCharts.setVisibility(View.GONE);
    }

    private void showCharts() {
        layoutLoading.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        layoutCharts.setVisibility(View.VISIBLE);
    }
}
