package ht.albrec.runningdata.settings;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;

import ht.albrec.runningdata.R;

import static ht.albrec.runningdata.settings.SettingsActivity.OAUTH_ACTION;

public class OauthActivity extends WearableActivity {

    private OauthManager oauthManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        setAmbientEnabled();

        oauthManager = new OauthManager(this, new OauthManager.TokenFound() {
            @Override
            public void setToken(String token) {
                Intent intent = new Intent();
                intent.setAction(OAUTH_ACTION);
                intent.putExtra("token", token);
                sendBroadcast(intent);
                finish();
            }
        });
    }
}
