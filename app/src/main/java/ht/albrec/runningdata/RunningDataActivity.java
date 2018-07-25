package ht.albrec.runningdata;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ht.albrec.runningdata.settings.Settings;
import ht.albrec.runningdata.settings.UploadActivity;

public class RunningDataActivity extends WearableActivity {
    private static final int MY_CHECK_TTS = 0;
    public static final String FILE_PREFIX = "running-data-";

    private boolean ambient = false;
    private long startTime = System.currentTimeMillis();
    private double startBattery = 0.0;
    private long batteryTime = System.currentTimeMillis();
    private double battery = 0.0;

    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private HeartRateTracker heartRateTracker;

//    private Sensor rotationSensor;
//    private SensorEventListener rotationListener;
//
//    private PowerManager powerManager;
//    private PowerManager.WakeLock wakeLock;

    private LocationTracker locationTracker;

    private GoogleApiClient googleApiClient;

    private LocationSummary allSummary = new LocationSummary(0.0, 0);
    private LocationSummary kmSummary = new LocationSummary(1000.0, 0);
    private LocationSummary hmSummary = new LocationSummary(100.0, 0);

    private int lastVibrate = 0;
    private int lastVoice = 0;

    private TextToSpeech tts;
    private Vibrator vibrator;

    private DataTracker data = null;
    private String file;

    private Settings settings;

    private ToggleButton toggleButton;
    private Button closeButton;

    private TextView allValue;
    private TextView kmValue;
    private TextView hmValue;
    private TextView distValue;
    private TextView timeValue;
    private TextView batValue;
    private TextView errValue;
    private TextView hrValue;

    private BroadcastReceiver batteryReceiver;
    private static final String FINAL_UTTERANCE = "ht.albrec.runningdata.FINAL_UTTERANCE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_data);

        settings = (Settings) getIntent().getSerializableExtra("settings");

        toggleButton = findViewById(R.id.toggleButton);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                closeButton.setVisibility(isChecked ? View.INVISIBLE : View.VISIBLE);
                toggleRunning(isChecked);
            }
        });
        closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop(null);
            }
        });

        ((TextView) findViewById(R.id.allLabel)).setText("*" + getString(settings.isMetric() ? R.string.km : R.string.mi));
        ((TextView) findViewById(R.id.kmLabel)).setText("1" + getString(settings.isMetric() ? R.string.km : R.string.mi));
        ((TextView) findViewById(R.id.hmLabel)).setText("0.1" + getString(settings.isMetric() ? R.string.km : R.string.mi));

        allValue = findViewById(R.id.allValue);
        kmValue = findViewById(R.id.kmValue);
        hmValue = findViewById(R.id.hmValue);
        timeValue = findViewById(R.id.timeValue);
        distValue = findViewById(R.id.distValue);
        batValue = findViewById(R.id.batValue);
        errValue = findViewById(R.id.errValue);
        hrValue = findViewById(R.id.hrValue);

        closeButton.setVisibility(View.INVISIBLE);

        kmSummary = new LocationSummary(settings.isMetric() ? 1000.0 : 1609.34, 0);
        hmSummary = new LocationSummary(settings.isMetric() ? 100.0 : 160.934, 0);

        // Enables Always-on
        setAmbientEnabled();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        file = FILE_PREFIX + System.currentTimeMillis() + (settings.isSpeed() ? "-ride" : "-run") + ".gpx";

        try {
            Intent checkIntent = new Intent();
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            startActivityForResult(checkIntent, MY_CHECK_TTS);
        } catch (ActivityNotFoundException e) {
            Log.e("RunningDataActivity", "TTS not found");
        }

        startTime = System.currentTimeMillis();
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                double newBattery = level / (double) scale;
                if (newBattery != battery) {
                    if (battery == 0.0) {
                        battery = newBattery;
                    } else {
                        battery = newBattery;
                        batteryTime = System.currentTimeMillis();
                        if (startBattery == 0.0) {
                            startBattery = battery;
                            startTime = System.currentTimeMillis();
                        }
                    }
                    Log.d("RunningDataActivity", "Battery: " + battery);
                    updateText(false);

                    if (battery * 100 <= settings.getLowBattery()) {
                        toggleRunning(false);
                        stop("Battery low stopping");
                    }
                }
            }
        };
        this.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) {
            Log.e("RunningDataActivity", "Unable to find vibrator [that's what she said]");
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        heartRateTracker = new HeartRateTracker(new Runnable() {
            @Override
            public void run() {
                updateText(false);
            }
        });

//        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
//        rotationListener = new SensorEventListener() {
//            private double lastPitch;
//
//            @Override
//            public void onSensorChanged(SensorEvent event) {
//                if (event.values.length > 4) {
//                    float[] truncatedRotationVectors = new float[4];
//                    System.arraycopy(event.values, 0, truncatedRotationVectors, 0, 4);
//                    onRotationChange(truncatedRotationVectors);
//                }
//            }
//
//            protected void onRotationChange(float[] vectors) {
//                float[] rotationMatrix = new float[9], adjustedMatrix = new float[9], orientation = new float[3];
//                SensorManager.getRotationMatrixFromVector(rotationMatrix, vectors);
//                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, adjustedMatrix);
//                SensorManager.getOrientation(adjustedMatrix, orientation);
//
//                double pitch = orientation[1] * 180 / Math.PI, roll = orientation[2] * 180 / Math.PI;
//                if (roll <= -90 || roll >= 90) {
//                    if (pitch >= 0) {
//                        pitch = 180 - pitch;
//                    } else {
//                        pitch = -180 - pitch;
//                    }
//                }
//                if (distance(lastPitch, pitch) >= 135) {
//                    lastPitch = pitch;
//                    wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
//                            "MyWakelockTag");
//                    wakeLock.acquire();
//                    wakeLock.release();
//                }
//            }
//
//            private double distance(double a1, double a2) {
//                double diff = Math.min(Math.abs(a1 - a2 + 360), Math.min(Math.abs(a1 - a2 - 360), Math.abs(a1 - a2)));
//                return diff;
//            }
//
//            @Override
//            public void onAccuracyChanged(Sensor sensor, int accuracy) {
//
//            }
//        };
//        sensorManager.registerListener(rotationListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);


        locationTracker = new LocationTracker(heartRateTracker, new Runnable() {
            @Override
            public void run() {
                updateExternal();
                updateText(false);
            }
        }, new LocationTracker.LocationUpdated() {
            @Override
            public void onDataPoint(DataPoint point) {
                if (data == null) {
                    try {
                        Log.d("RunningDataActivity", "Starting file " + file);
                        data = new DataTracker(openFileOutput(file, Context.MODE_PRIVATE));
                    } catch (IOException e) {
                        Log.e("onCreate()", "data tracker", e);
                    }
                }
                data.addData(point);

                allSummary.addPoint(point);
                kmSummary.addPoint(point);
                hmSummary.addPoint(point);

                updateExternal();
            }
        }, settings.getGpsEvery() * 1000);

        /*
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationTracker);
        } catch (SecurityException e) {
            Log.e("RunningDataActivity", "Failed to start gps", e);
            requestPermissions(new String[] { "android.permission.ACCESS_FINE_LOCATION" }, 0);
        }
        */

        Log.d("RunningDataActivity", "Initialized api");
        toggleButton.setChecked(true);
        locationTracker.setRunning(true);

        initHeartRate();
        initLocation();
    }

    private void updateExternal() {
        double pendingDistance = locationTracker.getPendingDistance();
        long pendingDuration = locationTracker.getPendingDuration();

        int newVibrate = getVibrateKey(pendingDistance, pendingDuration);
        int newVoice = getVoiceKey(pendingDistance, pendingDuration);

        if (newVibrate > lastVibrate) {
            lastVibrate = newVibrate;
            vibrator.vibrate(500);
        }
        if (newVoice > lastVoice && tts != null) {
            lastVoice = newVoice;
            tts.speak(getVoiceText(pendingDistance, pendingDuration), TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private void initHeartRate() {
        if (!sensorManager.registerListener(heartRateTracker, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            Log.e("RunningDataActivity", "Unable to register heart rate tracker");
            hrValue.setText("err");
        }
    }

    private void initLocation() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        try {
                            Log.d("RunningDataActivity", "Location listening");
                            LocationRequest locationRequest = LocationRequest.create()
                                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                                    .setInterval(settings.getGpsEvery() * 1000);
                            LocationServices.FusedLocationApi.requestLocationUpdates(
                                    googleApiClient, locationRequest, locationTracker);
                        } catch (SecurityException e) {
                            Log.e("onConnected()", "location init", e);
                        }
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.e("RunningDataActivity", "Connection suspended: " + i);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Log.e("RunningDataActivity", "Failed to connect api: " + connectionResult);
                        errValue.setText("err");
                    }
                })
                .addApi(LocationServices.API)
                .build();
        try {
            googleApiClient.connect();
        } catch (SecurityException e) {
            Log.e("RunningDataActivity", "Could not connect to api", e);
            errValue.setText("err");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.shutdown();
        }
        if (data != null) {
            data.close();
        }
        unregisterReceiver(batteryReceiver);
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
        sensorManager.unregisterListener(heartRateTracker);
//        sensorManager.unregisterListener(rotationListener);
    }

    private void updateText(boolean force) {
        if (!force && ambient) {
            return;
        }

        double pendingDistance = locationTracker.getPendingDistance();
        long pendingDuration = locationTracker.getPendingDuration();

        allValue.setText(settings.isSpeed() ? getSpeedText(allSummary.getDistance() + pendingDistance, allSummary.getDuration() + pendingDuration)
                : getTimeText(allSummary.getDuration() + pendingDuration, allSummary.getDistance() + pendingDistance));
        kmValue.setText(settings.isSpeed() ? getSpeedText(kmSummary.getDistance() + pendingDistance, kmSummary.getDuration() + pendingDuration)
                : getTimeText(kmSummary.getDuration() + pendingDuration, kmSummary.getDistance() + pendingDistance));
        hmValue.setText(settings.isSpeed() ? getSpeedText(hmSummary.getDistance() + pendingDistance, hmSummary.getDuration() + pendingDuration)
                : getTimeText(hmSummary.getDuration() + pendingDuration, hmSummary.getDistance() + pendingDistance));

        distValue.setText(getDistanceText(allSummary.getDistance() + pendingDistance));
        timeValue.setText(getTimeText(allSummary.getDuration() + pendingDuration));
        if (locationTracker.getLocation() != null) {
            errValue.setText(String.valueOf((int) locationTracker.getLocation().getAccuracy()) + "m");
        }

        if (startBattery == battery || startBattery == 0.0) {
            batValue.setText(battery == 0.0 ? "" : String.valueOf((int) (battery * 100.0)) + "%");
        } else {
            int secondsRemaining = (int) ((batteryTime - startTime) / (startBattery - battery) * (battery - settings.getLowBattery() / 100.0) - (System.currentTimeMillis() - batteryTime)) / 1000;
            batValue.setText((secondsRemaining / 60) + ":" + (secondsRemaining % 60 < 10 ? "0" : "") + (secondsRemaining % 60));
        }
        hrValue.setText(String.valueOf((int) heartRateTracker.getHeartRate()));
    }

    private String getDistanceText(double dist) {
        double distance = dist / (settings.isMetric() ? 1000.0 : 1609.34);
        return getString(settings.isMetric() ? R.string.kmv : R.string.miv, distance);
    }

    private String getSpeedText(double dist, long time) {
        double speed = time == 0 ? 0.0 : dist / (settings.isMetric() ? 1000.0 : 1609.34) / (time / (double) 3600000);
        return getString(settings.isMetric() ? R.string.kph : R.string.mph, speed);
    }

    private String getTimeText(long time, double distance) {
        if (distance == 0.0) {
            return "Forever";
        }
        long realTime = (long) (time / (distance / (settings.isMetric() ? 1000.0 : 1609.34)));
        return getTimeText(realTime);
    }

    private String getTimeText(long time) {
        long seconds = time / 1000;
        long minutes = (seconds / 60) % 60;
        long hours = seconds / 3600;
        seconds = seconds % 60;
        return hours + ":" + (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private String getVoiceText(double pendingDistance, long pendingDuration) {
        String text = getString(settings.isMetric() ? R.string.voice_text_metric : R.string.voice_text_imperial);
        String[] parts = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part: parts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (part.startsWith("$")) {
                sb.append(evaluate(part.substring(1), pendingDistance, pendingDuration));
            } else {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private String evaluate(String part, double pendingDistance, long pendingDuration) {
        int pi = part.indexOf('.');
        if (pi == -1) {
            return part;
        }

        LocationSummary summary;
        switch (part.substring(0, pi)) {
            case "all":
                summary = allSummary;
                break;
            case "km":
            case "mi":
                summary = kmSummary;
                break;
            case "hm":
                summary = hmSummary;
            default:
                return part;
        }
        switch (part.substring(pi + 1)) {
            case "time":
                return settings.isSpeed() ? getSpeedVoice(summary.getDistance() + pendingDistance, summary.getDuration() + pendingDuration)
                        : getTimeVoice(summary.getDuration() + pendingDuration, summary.getDistance() + pendingDistance);
            case "distance":
                return getDistanceVoice(summary.getDistance() + pendingDistance);
            case "duration":
                return getTimeVoice(summary.getDuration() + pendingDuration);
            default:
                return part;
        }
    }

    private String getDistanceVoice(double distance) {
        double n = distance / (settings.isMetric() ? 1000.0 : 1609.34);
        return getString(settings.isMetric() ? R.string.kilometers : R.string.miles, n);
    }

    private String getTimeVoice(long time, double distance) {
        if (distance == 0.0) {
            return getString(R.string.forever);
        }

        long realTime = (long) (time / (distance / (settings.isMetric() ? 1000.0 : 1609.34)));
        return getTimeVoice(realTime);
    }

    private String getTimeVoice(long time) {
        long seconds = time / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds / 60) % 60;
        seconds = seconds % 60;
        return (hours > 0 ? getString(hours == 1 ? R.string.hour : R.string.hours, hours) : "")
                + (minutes > 0 ? getString(hours == 1 ? R.string.minute : R.string.minutes, minutes) : "")
                + (seconds > 0 ? getString(hours == 1 ? R.string.second : R.string.seconds, seconds) : "");
    }

    private String getSpeedVoice(double distance, long time) {
        double speed = distance / (settings.isMetric() ? 1000.0 : 1609.34) / (time / (double) 3600000);
        return getString(settings.isMetric() ? R.string.kilometers_per_hour : R.string.miles_per_hour, speed);
    }

    public void toggleRunning(boolean running) {
        if (!running) {
            locationTracker.setRunning(false);
        } else {
            if (data != null) {
                data.newSegment();
            }
            locationTracker.setRunning(true);
        }
    }

    public void stop(String prompt) {
        locationTracker.setRunning(false);

        if (data != null) {
            data.close();
        }
        if (prompt != null) {
            if (tts != null) {
                tts.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, FINAL_UTTERANCE);
            } else {
                finish();
            }
        } else {
            finish();

            if (settings.getToken() != null) {
                Intent intent = new Intent(this, UploadActivity.class);
                intent.putExtra("token", settings.getToken());
                startActivity(intent);
            }
        }
    }


    private int getVibrateKey(double pendingDistance, long pendingDuration) {
        return settings.getVibrateEvery() == 0 ? 0 : (int) ((allSummary.getDistance() + pendingDistance) / (settings.isMetric() ? 100.0 : 160.934) / settings.getVibrateEvery());
    }

    private int getVoiceKey(double pendingDistance, long pendingDuration) {
        return settings.getVoiceEvery() == 0 ? 0 : (int) ((allSummary.getDistance() + pendingDistance) / (settings.isMetric() ? 100.0 : 160.934) / settings.getVoiceEvery());
    }

    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_CHECK_TTS) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int i) {
                        String localeString = getString(R.string.locale);
                        Locale locale = localeString == null ? null : Locale.forLanguageTag(localeString);
                        if (locale != null && tts.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE) {
                            tts.setLanguage(locale);
                            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                @Override
                                public void onStart(String utteranceId) {

                                }

                                @Override
                                public void onDone(String utteranceId) {
                                    if (FINAL_UTTERANCE.equals(utteranceId)) {
                                        finish();
                                    }
                                }

                                @Override
                                public void onError(String utteranceId) {
                                    if (FINAL_UTTERANCE.equals(utteranceId)) {
                                        finish();
                                    }
                                }
                            });
                        } else {
                            Log.e("RunningDataActivity", "Unable to set language " + locale);
                            tts.shutdown();
                            tts = null;
                        }
                    }
                });
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        ambient = true;
        updateText(true);
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateText(true);
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();
        updateText(true);
        ambient = false;
    }
}
