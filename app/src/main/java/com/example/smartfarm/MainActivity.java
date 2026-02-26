package com.example.smartfarm;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private LineChart lineChart;
    private FirebaseFirestore db;
    private ArrayList<Entry> suhuEntries = new ArrayList<>();
    private MqttClient mqttClient;
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";
    private static final String CLIENT_ID = "AndroidAppSmartFarm";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Inisialisasi Chart
        lineChart = findViewById(R.id.lineChart);

        db = FirebaseFirestore.getInstance();

        bacaDataFirestoreUntukGrafik();

        setupMQTT();

        // Contoh implementasi tombol
        Button btnPlot1 = findViewById(R.id.btnPlot1);
        btnPlot1.setOnClickListener(v -> publishMQTT("smartfarm/kontrol/plot1", "ON"));
    }

    private void bacaDataFirestoreUntukGrafik() {
        // Mengambil data dari collection "sensor_history"
        // Diurutkan berdasarkan field "timestamp" dari yang terlama ke terbaru
        db.collection("sensor_history")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value,
                                        @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w("Firestore", "Listen failed.", e);
                            return;
                        }

                        suhuEntries.clear();
                        int xIndex = 0; // Sumbu X untuk grafik

                        for (QueryDocumentSnapshot doc : value) {
                            // Ambil nilai suhu (pastikan tipe data di Firestore adalah Number)
                            if (doc.get("suhu") != null) {
                                float suhu = doc.getDouble("suhu").floatValue();
                                suhuEntries.add(new Entry(xIndex, suhu));
                                xIndex++;
                            }
                        }

                        updateGrafik();
                    }
                });
    }

    private void updateGrafik() {
        // Konfigurasi garis grafik
        LineDataSet dataSetSuhu = new LineDataSet(suhuEntries, "Suhu Udara");
        dataSetSuhu.setColor(android.graphics.Color.parseColor("#FF8A65"));
        dataSetSuhu.setDrawCircles(false); // Sesuai gambar, tanpa bulatan titik
        dataSetSuhu.setLineWidth(2f);

        LineData lineData = new LineData(dataSetSuhu);
        lineChart.setData(lineData);
        lineChart.invalidate(); // Refresh chart
    }

    private void setupMQTT() {
        TextView tvSuhu = findViewById(R.id.tvSuhu);
        try {
            mqttClient = new MqttClient(BROKER_URL, CLIENT_ID, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.connect(options);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {}

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    runOnUiThread(() -> {
                        if(topic.equals("smartfarm/sensor/suhu")){
                            // Update TextView Suhu
                            tvSuhu.setText(payload);
                        }
                    });
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            mqttClient.subscribe("smartfarm/sensor/#");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishMQTT(String topic, String msg) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(msg.getBytes());
                message.setQos(0);
                mqttClient.publish(topic, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


