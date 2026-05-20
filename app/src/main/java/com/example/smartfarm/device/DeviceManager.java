package com.example.smartfarm.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager untuk menyimpan dan mengambil daftar device dari SharedPreferences.
 *
 * Data disimpan sebagai JSON array di SharedPreferences dengan key:
 * - "devices_hidroponik" → daftar device Hidroponik
 * - "devices_mediatanah" → daftar device Media Tanah
 */
public class DeviceManager {

    private static final String TAG = "DeviceManager";
    private static final String PREFS_NAME = "smartfarm_devices";
    private static final String KEY_PREFIX = "devices_";

    private final SharedPreferences prefs;

    public DeviceManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Ambil semua device berdasarkan tipe.
     *
     * @param type DeviceConfig.TYPE_HIDROPONIK atau DeviceConfig.TYPE_MEDIA_TANAH
     */
    public List<DeviceConfig> getDevices(String type) {
        List<DeviceConfig> devices = new ArrayList<>();
        String json = prefs.getString(KEY_PREFIX + type, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                devices.add(new DeviceConfig(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading devices: " + e.getMessage());
        }
        return devices;
    }

    /**
     * Tambahkan device baru.
     */
    public void addDevice(DeviceConfig device) {
        List<DeviceConfig> devices = getDevices(device.getType());
        devices.add(device);
        saveDevices(device.getType(), devices);
    }

    /**
     * Update device yang sudah ada (berdasarkan ID).
     */
    public void updateDevice(DeviceConfig device) {
        List<DeviceConfig> devices = getDevices(device.getType());
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getId().equals(device.getId())) {
                devices.set(i, device);
                break;
            }
        }
        saveDevices(device.getType(), devices);
    }

    /**
     * Hapus device berdasarkan ID dan tipe.
     */
    public void removeDevice(String type, String deviceId) {
        List<DeviceConfig> devices = getDevices(type);
        devices.removeIf(d -> d.getId().equals(deviceId));
        saveDevices(type, devices);
    }

    /**
     * Ambil device berdasarkan ID.
     */
    public DeviceConfig getDeviceById(String type, String deviceId) {
        List<DeviceConfig> devices = getDevices(type);
        for (DeviceConfig device : devices) {
            if (device.getId().equals(deviceId)) {
                return device;
            }
        }
        return null;
    }

    /**
     * Simpan daftar device ke SharedPreferences.
     */
    private void saveDevices(String type, List<DeviceConfig> devices) {
        try {
            JSONArray array = new JSONArray();
            for (DeviceConfig device : devices) {
                array.put(device.toJson());
            }
            prefs.edit()
                    .putString(KEY_PREFIX + type, array.toString())
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving devices: " + e.getMessage());
        }
    }
}
