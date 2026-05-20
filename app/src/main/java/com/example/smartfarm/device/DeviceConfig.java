package com.example.smartfarm.device;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Model yang merepresentasikan konfigurasi satu device IoT (ESP32).
 * Menyimpan informasi koneksi MQTT dan metadata device.
 *
 * Setiap device memiliki:
 * - ID unik (UUID)
 * - Nama device (diberikan oleh user)
 * - Tipe device: "hidroponik" atau "mediatanah"
 * - Konfigurasi MQTT (broker URL, username, password, topic prefix)
 */
public class DeviceConfig {

    public static final String TYPE_HIDROPONIK = "hidroponik";
    public static final String TYPE_MEDIA_TANAH = "mediatanah";

    private String id;
    private String name;
    private String type;           // "hidroponik" atau "mediatanah"
    private String brokerUrl;
    private String mqttUsername;
    private String mqttPassword;
    private String topicPrefix;    // Prefix topic MQTT, misal "smartfarm" atau "nutrisi"
    private long createdAt;

    /**
     * Constructor untuk membuat device baru (ID otomatis).
     */
    public DeviceConfig(String name, String type, String brokerUrl,
                        String mqttUsername, String mqttPassword, String topicPrefix) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.type = type;
        this.brokerUrl = brokerUrl;
        this.mqttUsername = mqttUsername;
        this.mqttPassword = mqttPassword;
        this.topicPrefix = topicPrefix;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Constructor dari JSON (untuk deserialisasi dari SharedPreferences).
     */
    public DeviceConfig(JSONObject json) throws JSONException {
        this.id = json.getString("id");
        this.name = json.getString("name");
        this.type = json.getString("type");
        this.brokerUrl = json.getString("brokerUrl");
        this.mqttUsername = json.optString("mqttUsername", "");
        this.mqttPassword = json.optString("mqttPassword", "");
        this.topicPrefix = json.optString("topicPrefix", "");
        this.createdAt = json.optLong("createdAt", System.currentTimeMillis());
    }

    /**
     * Serialisasi ke JSON untuk penyimpanan.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("type", type);
        json.put("brokerUrl", brokerUrl);
        json.put("mqttUsername", mqttUsername);
        json.put("mqttPassword", mqttPassword);
        json.put("topicPrefix", topicPrefix);
        json.put("createdAt", createdAt);
        return json;
    }

    // ==================== GETTERS ====================

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getBrokerUrl() { return brokerUrl; }
    public String getMqttUsername() { return mqttUsername; }
    public String getMqttPassword() { return mqttPassword; }
    public String getTopicPrefix() { return topicPrefix; }
    public long getCreatedAt() { return createdAt; }

    // ==================== SETTERS ====================

    public void setName(String name) { this.name = name; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public void setMqttUsername(String mqttUsername) { this.mqttUsername = mqttUsername; }
    public void setMqttPassword(String mqttPassword) { this.mqttPassword = mqttPassword; }
    public void setTopicPrefix(String topicPrefix) { this.topicPrefix = topicPrefix; }

    /**
     * Cek apakah device memiliki credential MQTT.
     */
    public boolean hasCredentials() {
        return mqttUsername != null && !mqttUsername.isEmpty();
    }
}
