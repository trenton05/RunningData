package ht.albrec.runningdata;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ht.albrec.runningdata.settings.Settings;
import ht.albrec.runningdata.settings.UploadActivity;

public class RunningDataActivity extends WearableActivity {
    private static final int MY_CHECK_TTS = 0;
    private static final double TARGET_BATTERY = 0.02;
    public static final String FILE_PREFIX = "running-data-";

    private long startTime = System.currentTimeMillis();
    private double startBattery = 0.0;
    private double battery = 0.0;

    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private HeartRateTracker heartRateTracker;

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
                stop(true);
            }
        });

        ((TextView) findViewById(R.id.allLabel)).setText(settings.isMetric() ? "*km" : "*mi");
        ((TextView) findViewById(R.id.kmLabel)).setText(settings.isMetric() ? "1km" : "1mi");
        ((TextView) findViewById(R.id.hmLabel)).setText(settings.isMetric() ? "0.1km" : "0.1mi");

        allValue = findViewById(R.id.allValue);
        kmValue = findViewById(R.id.kmValue);
        hmValue = findViewById(R.id.hmValue);
        timeValue = findViewById(R.id.timeValue);
        distValue = findViewById(R.id.distValue);
        batValue = findViewById(R.id.batValue);
        errValue = findViewById(R.id.errValue);
        hrValue = findViewById(R.id.hrValue);

        closeButton.setVisibility(View.INVISIBLE);

        kmSummary = new LocationSummary(settings.isMetric() ? 1000.0 : 1600.0, 0);
        hmSummary = new LocationSummary(settings.isMetric() ? 100.0 : 160.0, 0);

        // Enables Always-on
        setAmbientEnabled();


        file = FILE_PREFIX + System.currentTimeMillis() + (settings.isSpeed() ? "-ride" : "-run") + ".gpx";

        try {
            Intent checkIntent = new Intent();
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            startActivityForResult(checkIntent, MY_CHECK_TTS);
        } catch (ActivityNotFoundException e) {
            Log.e("RunningDataActivity", "TTS not found");
        }

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                updateText();
            }
        };

        startTime = System.currentTimeMillis();
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                battery = level / (double) scale;
                if (startBattery == 0.0) {
                    startBattery = battery;
                }
                Log.d("RunningDataActivity", "Battery: " + battery);
                updateText();

                if (battery <= TARGET_BATTERY) {
                    toggleRunning(false);
                    stop(false);

                    if (tts != null) {
                        tts.speak("Battery low stopping", TextToSpeech.QUEUE_FLUSH, null);
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
        heartRateTracker = new HeartRateTracker(refresh);

        List<String> neededPermissions = new ArrayList<>();

        if (!sensorManager.registerListener(heartRateTracker, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            Log.e("RunningDataActivity", "Unable to register heart rate tracker");
            neededPermissions.add("android.permissions.BODY_SENSORS");
        }

        locationTracker = new LocationTracker(heartRateTracker, refresh, new LocationTracker.LocationUpdated() {
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
            }
        });

        /*
        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationTracker);
        } catch (SecurityException e) {
            Log.e("RunningDataActivity", "Failed to start gps", e);
            requestPermissions(new String[] { "android.permission.ACCESS_FINE_LOCATION" }, 0);
        }
        */
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
                        Log.e("RunningDataActivity", "Failed to connect api: " + connectionResult.getErrorMessage());
                    }
                })
                .addApi(LocationServices.API)
                .build();
        try {
            googleApiClient.connect();
        } catch (SecurityException e) {
            neededPermissions.add("android.permission.ACCESS_FINE_LOCATION");
        }

        if (neededPermissions.size() > 0) {
            requestPermissions(neededPermissions.toArray(new String[0]), 0);
        }

        Log.d("RunningDataActivity", "Initialized api");
        toggleButton.setChecked(true);
        locationTracker.setRunning(true);
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
        googleApiClient.disconnect();
        sensorManager.unregisterListener(heartRateTracker);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] resultCode) {
        for (String permission: permissions) {
            if ("android.permission.BODY_SENSORS".equals(permission)) {
                if (!sensorManager.registerListener(heartRateTracker, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                    Log.e("RunningDataActivity", "Failed to start hr");
                    hrValue.setText("err");
                }
            } else if ("android.permission.ACCESS_FINE_LOCATION".equals(permission)){
                try {
                    googleApiClient.connect();
                } catch (SecurityException e) {
                    Log.e("RunningDataActivity", "Failed to start gps", e);
                    errValue.setText("err");
                }
            }
        }
    }

    private void updateText() {
        int newVibrate = getVibrateKey();
        int newVoice = getVoiceKey();

        if (newVibrate != lastVibrate) {
            lastVibrate = newVibrate;
            vibrator.vibrate(500);
        }
        if (newVoice != lastVoice && tts != null) {
            lastVoice = newVoice;
            tts.speak(getVoiceText(), TextToSpeech.QUEUE_FLUSH, null);
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

        if (startBattery == battery) {
            batValue.setText(battery == 0.0 ? "" : String.valueOf((int) (battery * 100.0)) + "%");
        } else {
            int secondsRemaining = (int) ((System.currentTimeMillis() - startTime) / (startBattery - battery) * (battery - TARGET_BATTERY) / 1000);
            batValue.setText((secondsRemaining / 60) + ":" + (secondsRemaining % 60 < 10 ? "0" : "") + (secondsRemaining % 60));
        }
        hrValue.setText(String.valueOf((int) heartRateTracker.getHeartRate()));
    }

    private String getDistanceText(double dist) {
        dist /= 1000.0;
        int whole = (int) dist;
        int part = ((int)(dist * 100.0) % 100);
        return whole + "." + (part < 10 ? "0" : "") + part + " " + (settings.isMetric() ? "km" : "mi");
    }

    private String getSpeedText(double dist, long time) {
        double speed = dist / (settings.isMetric() ? 1000.0 : 1600.0) / (time / (double) 3600000);

        int whole = (int) speed;
        int part = ((int) (speed * 10) % 10);
        return whole + "." + part + " " + (settings.isMetric() ? "kmh" : "mph");
    }

    private String getTimeText(long time, double distance) {
        if (distance == 0.0) {
            return "Forever";
        }
        long realTime = (long) (time / (distance / (settings.isMetric() ? 1000.0 : 1600.0)));
        return getTimeText(realTime);
    }

    private String getTimeText(long time) {
        long seconds = time / 1000;
        long minutes = (seconds / 60) % 60;
        long hours = seconds / 3600;
        seconds = seconds % 60;
        return hours + ":" + (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private String getVoiceText() {
        StringBuilder sb = new StringBuilder();
        sb.append("distance ").append(getDistanceVoice(allSummary.getDistance()));
        sb.append(" duration ").append(getTimeVoice(allSummary.getDuration()));
        sb.append(" last " + (settings.isMetric() ? "kilometer" : "mile")).append(settings.isSpeed() ? getSpeedVoice(kmSummary.getDistance(), kmSummary.getDuration())
                : getTimeVoice(kmSummary.getDuration()));
        return sb.toString();
    }

    private String getDistanceVoice(double distance) {
        double n = distance / (settings.isMetric() ? 1000.0 : 1600.0);
        int whole = (int) n;
        int part = ((int) (n * 10) % 10);
        return whole + "." + part + " " + (settings.isMetric() ? "kilometers" : "miles");
    }

    private String getTimeVoice(long time) {
        long seconds = time / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds / 60) % 60;
        seconds = seconds % 60;
        return (hours > 0 ? hours + " " + (hours == 1 ? "hour" : "hours") : "")
                + (minutes > 0 ? minutes + " " + (minutes == 1 ? "minute" : "minutes") : "")
                + (seconds > 0 ? seconds + " " + (seconds == 1 ? "second" : "seconds") : "");
    }

    private String getSpeedVoice(double distance, long time) {
        double speed = distance / (settings.isMetric() ? 1000.0 : 1600.0) / (time / (double) 3600000);
        int whole = (int) speed;
        int part = ((int) (speed * 10) % 10);
        return whole + "." + part + " " + (settings.isMetric() ? "kilometers per hour" : "miles per hour");
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

    public void stop(boolean prompt) {
        locationTracker.setRunning(false);

        if (data != null) {
            data.close();
        }
        finish();

        if (settings.getToken() != null && prompt) {
            Intent intent = new Intent(this, UploadActivity.class);
            intent.putExtra("token", settings.getToken());
            startActivity(intent);
        }
    }


    private int getVibrateKey() {
        return settings.getVibrateEvery() == 0 ? 0 : (int) (allSummary.getDistance() / (settings.isMetric() ? 100.0 : 160.0) / settings.getVibrateEvery());
    }

    private int getVoiceKey() {
        return settings.getVoiceEvery() == 0 ? 0 : (int) (allSummary.getDistance() / (settings.isMetric() ? 100.0 : 160.0) / settings.getVoiceEvery());
    }

    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_CHECK_TTS) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int i) {
                        tts.setLanguage(Locale.US);
                    }
                });
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
