package com.example.smartfarm.device;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartfarm.R;
import com.example.smartfarm.hidroponik.HidroponikActivity;
import com.example.smartfarm.mediatanah.MediaTanahActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * Activity untuk menampilkan daftar device berdasarkan tipe (Hidroponik / Media Tanah).
 *
 * Fitur:
 * - Menampilkan semua device yang tersimpan untuk tipe tertentu
 * - Tambah device baru via dialog konfigurasi MQTT
 * - Edit konfigurasi device yang sudah ada
 * - Hapus device dengan konfirmasi
 * - Klik device → buka Activity kontrol dengan konfigurasi MQTT dari device tersebut
 *
 * Intent Extras:
 * - EXTRA_DEVICE_TYPE: "hidroponik" atau "mediatanah"
 */
public class DeviceListActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_TYPE = "device_type";

    /**
     * Key-key Intent extra yang digunakan untuk mengirim konfigurasi device
     * ke HidroponikActivity / MediaTanahActivity.
     */
    public static final String EXTRA_DEVICE_ID = "device_id";
    public static final String EXTRA_DEVICE_NAME = "device_name";
    public static final String EXTRA_BROKER_URL = "broker_url";
    public static final String EXTRA_MQTT_USERNAME = "mqtt_username";
    public static final String EXTRA_MQTT_PASSWORD = "mqtt_password";
    public static final String EXTRA_TOPIC_PREFIX = "topic_prefix";

    private String deviceType;
    private DeviceManager deviceManager;

    private LinearLayout layoutDeviceList;
    private LinearLayout layoutEmptyState;
    private TextView tvHeaderTitle, tvHeaderSubtitle, tvSectionTitle;
    private ImageView ivHeaderIcon, btnBack;
    private MaterialCardView cardAddDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        deviceType = getIntent().getStringExtra(EXTRA_DEVICE_TYPE);
        if (deviceType == null) {
            deviceType = DeviceConfig.TYPE_HIDROPONIK;
        }

        deviceManager = new DeviceManager(this);

        initViews();
        setupHeader();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDeviceList();
    }

    // ==================== INITIALIZATION ====================

    private void initViews() {
        layoutDeviceList = findViewById(R.id.layoutDeviceList);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        tvSectionTitle = findViewById(R.id.tvSectionTitle);
        ivHeaderIcon = findViewById(R.id.ivHeaderIcon);
        btnBack = findViewById(R.id.btnBack);
        cardAddDevice = findViewById(R.id.cardAddDevice);
    }

    private void setupHeader() {
        boolean isHidroponik = DeviceConfig.TYPE_HIDROPONIK.equals(deviceType);

        tvHeaderTitle.setText(isHidroponik ? "Device Hidroponik" : "Device Media Tanah");
        tvHeaderSubtitle.setText("Kelola perangkat " +
                (isHidroponik ? "hidroponik" : "media tanah") + " Anda");
        tvSectionTitle.setText("PERANGKAT " +
                (isHidroponik ? "HIDROPONIK" : "MEDIA TANAH"));

        if (isHidroponik) {
            ivHeaderIcon.setImageResource(R.drawable.ic_hydroponics);
        } else {
            ivHeaderIcon.setImageResource(R.drawable.ic_soil);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        cardAddDevice.setOnClickListener(v -> showAddEditDeviceDialog(null));
    }

    // ==================== DEVICE LIST ====================

    /**
     * Refresh tampilan daftar device dari SharedPreferences.
     */
    private void refreshDeviceList() {
        layoutDeviceList.removeAllViews();
        List<DeviceConfig> devices = deviceManager.getDevices(deviceType);

        if (devices.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            layoutDeviceList.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            layoutDeviceList.setVisibility(View.VISIBLE);

            for (DeviceConfig device : devices) {
                View itemView = createDeviceCardView(device);
                layoutDeviceList.addView(itemView);
            }
        }
    }

    /**
     * Buat satu card view untuk device.
     */
    private View createDeviceCardView(DeviceConfig device) {
        View itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_device_card, layoutDeviceList, false);

        TextView tvName = itemView.findViewById(R.id.tvDeviceName);
        TextView tvBroker = itemView.findViewById(R.id.tvDeviceBroker);
        TextView tvTopic = itemView.findViewById(R.id.tvDeviceTopic);
        ImageView btnEdit = itemView.findViewById(R.id.btnEditDevice);
        ImageView btnDelete = itemView.findViewById(R.id.btnDeleteDevice);

        tvName.setText(device.getName());
        tvBroker.setText(device.getBrokerUrl());

        String topicDisplay = device.getTopicPrefix();
        if (topicDisplay == null || topicDisplay.isEmpty()) {
            topicDisplay = "(default)";
        } else {
            topicDisplay = topicDisplay + "/#";
        }
        tvTopic.setText(topicDisplay);

        // Klik card → buka Activity kontrol
        itemView.setOnClickListener(v -> openDeviceActivity(device));

        // Edit device
        btnEdit.setOnClickListener(v -> showAddEditDeviceDialog(device));

        // Hapus device
        btnDelete.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Hapus Device")
                    .setMessage("Hapus \"" + device.getName() + "\" dari daftar?")
                    .setPositiveButton("Hapus", (dialog, which) -> {
                        deviceManager.removeDevice(deviceType, device.getId());
                        refreshDeviceList();
                        Toast.makeText(this, "Device dihapus", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        });

        return itemView;
    }

    // ==================== ADD/EDIT DEVICE DIALOG ====================

    /**
     * Tampilkan dialog tambah/edit device.
     *
     * @param existingDevice null untuk tambah baru, non-null untuk edit
     */
    private void showAddEditDeviceDialog(DeviceConfig existingDevice) {
        boolean isEdit = (existingDevice != null);

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_device, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        TextInputEditText etName = dialogView.findViewById(R.id.etDeviceName);
        TextInputEditText etBroker = dialogView.findViewById(R.id.etBrokerUrl);
        TextInputEditText etTopicPrefix = dialogView.findViewById(R.id.etTopicPrefix);
        TextInputEditText etUsername = dialogView.findViewById(R.id.etMqttUsername);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etMqttPassword);

        boolean isHidroponik = DeviceConfig.TYPE_HIDROPONIK.equals(deviceType);
        String typeName = isHidroponik ? "Hidroponik" : "Media Tanah";

        tvTitle.setText(isEdit ? "Edit Device" : "Tambah Device " + typeName);
        tvSubtitle.setText("Masukkan konfigurasi MQTT device " + typeName);

        // Pre-fill untuk edit atau default untuk tambah baru
        if (isEdit) {
            etName.setText(existingDevice.getName());
            etBroker.setText(existingDevice.getBrokerUrl());
            etTopicPrefix.setText(existingDevice.getTopicPrefix());
            etUsername.setText(existingDevice.getMqttUsername());
            etPassword.setText(existingDevice.getMqttPassword());
        } else {
            // Default broker berdasarkan tipe
            if (isHidroponik) {
                etBroker.setText("tcp://broker.hivemq.com:1883");
                etTopicPrefix.setText("nutrisi");
            } else {
                etBroker.setText("tcp://broker.emqx.io:1883");
                etTopicPrefix.setText("smartfarm");
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Batal", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                String broker = etBroker.getText() != null ? etBroker.getText().toString().trim() : "";
                String topic = etTopicPrefix.getText() != null ? etTopicPrefix.getText().toString().trim() : "";
                String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
                String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

                // Validasi
                if (name.isEmpty()) {
                    etName.setError("Nama device wajib diisi");
                    etName.requestFocus();
                    return;
                }
                if (broker.isEmpty()) {
                    etBroker.setError("Broker URL wajib diisi");
                    etBroker.requestFocus();
                    return;
                }
                if (!broker.startsWith("tcp://") && !broker.startsWith("ssl://")) {
                    etBroker.setError("Format: tcp://host:port atau ssl://host:port");
                    etBroker.requestFocus();
                    return;
                }

                if (isEdit) {
                    existingDevice.setName(name);
                    existingDevice.setBrokerUrl(broker);
                    existingDevice.setTopicPrefix(topic);
                    existingDevice.setMqttUsername(username);
                    existingDevice.setMqttPassword(password);
                    deviceManager.updateDevice(existingDevice);
                    Toast.makeText(this, "✅ Device diperbarui", Toast.LENGTH_SHORT).show();
                } else {
                    DeviceConfig newDevice = new DeviceConfig(
                            name, deviceType, broker, username, password, topic);
                    deviceManager.addDevice(newDevice);
                    Toast.makeText(this, "✅ Device ditambahkan", Toast.LENGTH_SHORT).show();
                }

                refreshDeviceList();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    // ==================== NAVIGATE TO DEVICE ACTIVITY ====================

    /**
     * Buka Activity kontrol dengan konfigurasi MQTT dari device yang dipilih.
     * Konfigurasi dikirim via Intent extras.
     */
    private void openDeviceActivity(DeviceConfig device) {
        Class<?> targetActivity;
        if (DeviceConfig.TYPE_HIDROPONIK.equals(deviceType)) {
            targetActivity = HidroponikActivity.class;
        } else {
            targetActivity = MediaTanahActivity.class;
        }

        Intent intent = new Intent(this, targetActivity);
        intent.putExtra(EXTRA_DEVICE_ID, device.getId());
        intent.putExtra(EXTRA_DEVICE_NAME, device.getName());
        intent.putExtra(EXTRA_BROKER_URL, device.getBrokerUrl());
        intent.putExtra(EXTRA_MQTT_USERNAME, device.getMqttUsername());
        intent.putExtra(EXTRA_MQTT_PASSWORD, device.getMqttPassword());
        intent.putExtra(EXTRA_TOPIC_PREFIX, device.getTopicPrefix());
        startActivity(intent);
    }
}
