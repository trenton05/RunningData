package ht.albrec.runningdata.settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class Settings implements Serializable {

    private boolean metric = true;
    private boolean speed = false;
    private int vibrateEvery = 1;
    private int voiceEvery = 5;
    private int gpsEvery = 1;
    private int lowBattery = 2;
    private String token = null;

    public boolean isMetric() {
        return metric;
    }

    public void setMetric(boolean metric) {
        this.metric = metric;
    }

    public boolean isSpeed() {
        return speed;
    }

    public void setSpeed(boolean speed) {
        this.speed = speed;
    }

    public int getVibrateEvery() {
        return vibrateEvery;
    }

    public void setVibrateEvery(int vibrateEvery) {
        this.vibrateEvery = vibrateEvery;
    }

    public int getVoiceEvery() {
        return voiceEvery;
    }

    public void setVoiceEvery(int voiceEvery) {
        this.voiceEvery = voiceEvery;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getGpsEvery() {
        return gpsEvery;
    }

    public void setGpsEvery(int gpsEvery) {
        this.gpsEvery = gpsEvery;
    }

    public int getLowBattery() {
        return lowBattery;
    }

    public void setLowBattery(int lowBattery) {
        this.lowBattery = lowBattery;
    }

    public String toString() {
        try {
            JSONObject json = new JSONObject();
            json.put("metric", metric);
            json.put("speed", speed);
            json.put("vibrateEvery", vibrateEvery);
            json.put("voiceEvery", voiceEvery);
            json.put("gpsEvery", gpsEvery);
            json.put("token", token);
            json.put("lowBattery", lowBattery);
            return json.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static Settings fromString(String str) {
        if (str == null || "".equals(str)) {
            return new Settings();
        }

        try {
            Settings settings = new Settings();
            JSONObject json = new JSONObject(str);
            settings.setMetric(json.optBoolean("metric", settings.isMetric()));
            settings.setSpeed(json.optBoolean("speed", settings.isSpeed()));
            settings.setVibrateEvery(json.optInt("vibrateEvery", settings.getVibrateEvery()));
            settings.setVoiceEvery(json.optInt("voiceEvery", settings.getVoiceEvery()));
            settings.setGpsEvery(json.optInt("gpsEvery", settings.getGpsEvery()));
            settings.setLowBattery(json.optInt("lowBattery", settings.getLowBattery()));
            settings.setToken(json.optString("token", settings.getToken()));
            return settings;
        } catch (Exception e) {
            return new Settings();
        }
    }
}
