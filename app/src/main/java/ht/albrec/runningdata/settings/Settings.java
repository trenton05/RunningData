package ht.albrec.runningdata.settings;

import java.io.Serializable;

public class Settings implements Serializable {

    private boolean metric = true;
    private boolean speed = false;
    private int vibrateEvery = 1;
    private int voiceEvery = 5;
    private int gpsEvery = 2;
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
}
