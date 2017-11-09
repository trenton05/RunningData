package ht.albrec.runningdata;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Created by trent on 11/6/2017.
 */

public class HeartRateTracker implements SensorEventListener {
    private float heartRate = 0.0f;

    private Runnable update;
    public HeartRateTracker(Runnable update) {
        this.update = update;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            Log.d("HeartRateTracker", "accuracy: " + sensorEvent.accuracy);
            Log.d("HeartRateTracker", "value: " + (sensorEvent.values.length == 0 ? 0.0 : sensorEvent.values[0]));
            if (sensorEvent.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                heartRate = sensorEvent.values[0];
                update.run();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public float getHeartRate() {
        return heartRate;
    }
}
