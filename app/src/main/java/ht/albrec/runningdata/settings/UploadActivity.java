package ht.albrec.runningdata.settings;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ht.albrec.runningdata.R;
import ht.albrec.runningdata.Uploader;

import static ht.albrec.runningdata.RunningDataActivity.FILE_PREFIX;

public class UploadActivity extends WearableActivity {
    private static final int MY_CHECK_SPEECH = 1;

    private TextView textView;
    private String token;
    private Handler handler = new Handler();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        setAmbientEnabled();

        textView = (TextView) findViewById(R.id.statusText);
        textView.setText(getString(R.string.upload_network));
        token = getIntent().getStringExtra("token");


        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        Network active = connectivityManager.getActiveNetwork();
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network n) {
                if (connectivityManager.bindProcessToNetwork(n)) {
                    network = n;

                    listen();
                } else {
                    Log.e("UploadActivity", "Unable to bind network");
                    textView.setText(getString(R.string.upload_network_failed, "Unable to bind network"));
                }
            }
        };

        if (active != null) {
            networkCallback.onAvailable(active);
        } else {
            this.networkCallback = networkCallback;

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            try {
                connectivityManager.requestNetwork(request, networkCallback);
            } catch (Exception e) {
                textView.setText(getString(R.string.upload_network_failed, e.getMessage()));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (networkCallback != null) {
            connectivityManager.bindProcessToNetwork(null);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    private void listen() {
        textView.setText(getString(R.string.upload_listen));

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.upload_listen));
        startActivityForResult(intent, MY_CHECK_SPEECH);
    }

    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_CHECK_SPEECH) {
            List<String> results = data == null ? Collections.singletonList((String) null) : data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            this.name = spokenText == null ? "" : spokenText;
            upload();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String name;
    private Network network;

    private void upload() {
        if (network == null) {
            textView.setText(getString(R.string.upload_network));
            return;
        } else if (name == null) {
            return;
        }
        textView.setText(getString(R.string.upload_name, name));

        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                boolean hasFailures = false;
                for (String file: fileList()) {
                    Exception firstError = null;
                    while (token != null && file.startsWith(FILE_PREFIX)) {
                        try {
                            Log.d("UploadActivity", "Uploading: " + file);
                            Uploader uploader = new Uploader("https://www.strava.com/api/v3/uploads", "UTF-8", token);
                            if (!"".equals(name)) {
                                uploader.addFormField("name", name);
                            }
                            uploader.addFormField("activity_type", file.indexOf("-ride") != -1 ? "ride" : "run");
                            uploader.addFormField("data_type", "gpx");
                            // If failed, maybe missing ending xml bad file
                            uploader.addFilePart("file", getFileStreamPath(file), firstError != null ? "</trkseg></trk></gpx>" : null, "text/xml; charset=UTF-8");
                            uploader.finish(firstError != null);
                            deleteFile(file);
                            Log.d("UploadActivity", "Uploaded: " + file);
                            break;
                        } catch (final Exception e) {
                            Log.e("upload()", "failed to upload", e);
                            if (firstError != null) {
                                final String message = firstError.getMessage();
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        textView.setText(getString(R.string.upload_network_failed, message));
                                    }
                                });
                                hasFailures = true;
                                break;
                            }
                            firstError = e;
                        }
                    }
                }
                Log.d("UploadActivity", "Done");
                if (!hasFailures) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText(getString(R.string.upload_success));
                            finish();
                        }
                    });
                }
            }
        });
    }
}
