package ht.albrec.runningdata.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.ToggleButton;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ht.albrec.runningdata.R;
import ht.albrec.runningdata.RunningDataActivity;
import ht.albrec.runningdata.Util;

public class SettingsActivity extends WearableActivity {
    public static final String OAUTH_ACTION = "ht.albrec.runningdata.settings.OAUTH_TOKEN";

    private static final String TOKEN_FILE = "settings";
    private Settings settings;

    private ToggleButton metricToggle;
    private ToggleButton speedToggle;
    private ToggleButton stravaToggle;
    private Spinner vibrateSpinner;
    private Spinner voiceSpinner;
    private Spinner gpsSpinner;
    private Spinner batterySpinner;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setAmbientEnabled();

        loadSettings();

        startButton = findViewById(R.id.startButton);
        metricToggle = findViewById(R.id.metricToggle);
        speedToggle = findViewById(R.id.speedButton);
        stravaToggle = findViewById(R.id.stravaButton);
        vibrateSpinner = findViewById(R.id.vibrateSpinner);
        voiceSpinner = findViewById(R.id.voiceSpinner);
        gpsSpinner = findViewById(R.id.gpsSpinner);
        batterySpinner = findViewById(R.id.batterySpinner);

        restoreSettings();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRun();
            }
        });

        metricToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setMetric(!settings.isMetric());
            }
        });

        speedToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setSpeed(!settings.isSpeed());
            }
        });

        stravaToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (settings.getToken() != null) {
                    OauthManager.deauthorize(settings.getToken());
                } else if (isChecked) {
                    registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            String token = intent.getStringExtra("token");
                            if (token == null) {
                                stravaToggle.setChecked(false);
                            } else {
                                settings.setToken(token);
                            }
                            unregisterReceiver(this);
                        }
                    }, new IntentFilter(OAUTH_ACTION));
                    startOauth();
                }
            }
        });
        vibrateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        settings.setVibrateEvery(0);
                        break;
                    case 1:
                        settings.setVibrateEvery(1);
                        break;
                    case 2:
                        settings.setVibrateEvery(2);
                        break;
                    case 3:
                        settings.setVibrateEvery(5);
                        break;
                    case 4:
                        settings.setVibrateEvery(10);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        settings.setVoiceEvery(0);
                        break;
                    case 1:
                        settings.setVoiceEvery(5);
                        break;
                    case 2:
                        settings.setVoiceEvery(10);
                        break;
                    case 3:
                        settings.setVoiceEvery(20);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        gpsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        settings.setGpsEvery(1);
                        break;
                    case 1:
                        settings.setGpsEvery(2);
                        break;
                    case 2:
                        settings.setGpsEvery(5);
                        break;
                    case 3:
                        settings.setGpsEvery(10);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        batterySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        settings.setLowBattery(1);
                        break;
                    case 1:
                        settings.setLowBattery(2);
                        break;
                    case 2:
                        settings.setLowBattery(5);
                        break;
                    case 3:
                        settings.setLowBattery(10);
                        break;
                    case 4:
                        settings.setLowBattery(15);
                        break;
                    case 5:
                        settings.setLowBattery(25);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        saveSettings();
    }

    private void restoreSettings() {

        metricToggle.setChecked(settings.isMetric());
        speedToggle.setChecked(settings.isSpeed());
        stravaToggle.setChecked(settings.getToken() != null);

        switch (settings.getVibrateEvery()) {
            case 0:
                vibrateSpinner.setSelection(0);
                break;
            case 2:
                vibrateSpinner.setSelection(2);
                break;
            case 5:
                vibrateSpinner.setSelection(3);
                break;
            case 10:
                vibrateSpinner.setSelection(4);
                break;
            default:
                vibrateSpinner.setSelection(1);
                break;
        }

        switch (settings.getVoiceEvery()) {
            case 0:
                voiceSpinner.setSelection(0);
                break;
            case 10:
                voiceSpinner.setSelection(2);
                break;
            case 20:
                voiceSpinner.setSelection(3);
                break;
            default:
                voiceSpinner.setSelection(1);
                break;
        }

        switch (settings.getGpsEvery()) {
            case 1:
                gpsSpinner.setSelection(0);
                break;
            case 5:
                gpsSpinner.setSelection(2);
                break;
            case 10:
                gpsSpinner.setSelection(3);
                break;
            default:
                gpsSpinner.setSelection(1);
                break;
        }

        switch (settings.getLowBattery()) {
            case 1:
                batterySpinner.setSelection(0);
                break;
            case 5:
                batterySpinner.setSelection(2);
                break;
            case 10:
                batterySpinner.setSelection(3);
                break;
            case 15:
                batterySpinner.setSelection(4);
                break;
            case 25:
                batterySpinner.setSelection(5);
                break;
            default:
                batterySpinner.setSelection(1);
                break;
        }
    }


    private void startRun() {
        Intent intent = new Intent(this, RunningDataActivity.class);
        intent.putExtra("settings", settings);
        finish();
        startActivity(intent);
    }

    private void startOauth() {
        Intent intent = new Intent(this, OauthActivity.class);
        startActivity(intent);
    }


    private void loadSettings() {
        try {
            FileInputStream fis = openFileInput(TOKEN_FILE);
            try {
                settings = Settings.fromString(Util.readInput(fis));
            } finally {
                fis.close();
            }
        } catch (Exception e) {
            settings = new Settings();
        }
    }

    private void saveSettings() {
        try {
            FileOutputStream fos = openFileOutput(TOKEN_FILE, Context.MODE_PRIVATE);
            fos.write(settings.toString().getBytes());
            fos.close();
        } catch (IOException e) {
            Log.e("saveToken()", "Unable to save token", e);
        }
    }

}
