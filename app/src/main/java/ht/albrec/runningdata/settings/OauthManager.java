package ht.albrec.runningdata.settings;

import android.content.Context;
import android.net.Uri;
import android.support.wearable.authentication.OAuthClient;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ht.albrec.runningdata.Util;

public class OauthManager {
    public interface TokenFound {
        void setToken(String token);
    }

    public static final String AUTH_URL = "https://www.strava.com/oauth/authorize";
    public static final String DEAUTH_URL = "https://www.strava.com/oauth/deauthorize";
    public static final String TOKEN_URL = "https://www.strava.com/oauth/token";
    public static final String CLIENT_ID = "16159";
    public static final String CLIENT_SECRET = "7d36940f3381a84e207cf86af35b3c42ac47aadb";
    private static final String HTTP_REDIRECT_URL = "https://www.android.com/wear/3p_auth";

    public OauthManager(Context context, final TokenFound tokenFound) {
        final OAuthClient oAuthClient = OAuthClient.create(context);
        oAuthClient.sendAuthorizationRequest(Uri.parse(AUTH_URL + "?response_type=code&scope=write&redirect_uri=" + redirectUrl(context) + "&client_id=" + CLIENT_ID),
                new OAuthClient.Callback() {
                    @Override
                    public void onAuthorizationResponse(Uri request, final Uri response) {
                        ExecutorService executor = Executors.newCachedThreadPool();
                        executor.submit(new Runnable() {
                           @Override
                            public void run() {
                               String token = token(response.getEncodedQuery());
                               tokenFound.setToken(token);
                               oAuthClient.destroy();
                           }
                        });
                    }

                    @Override
                    public void onAuthorizationError(int i) {
                        Log.e("OauthManager", "auth failed: " + i);
                        tokenFound.setToken(null);
                        oAuthClient.destroy();
                    }
                });

    }

    private String redirectUrl(Context context) {
        // Ensure you register the redirect URI in your Google OAuth 2.0 client configuration.
        // Normally this would be the server that would handle the token exchange after receiving
        // the authorization code.
        return Uri.encode(HTTP_REDIRECT_URL + "/" + context.getApplicationContext().getPackageName());
    }

    public static void deauthorize(final String token) {
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(DEAUTH_URL);
                    HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                    httpConn.setUseCaches(false);
                    httpConn.setDoOutput(true); // indicates POST method
                    httpConn.setDoInput(true);
                    httpConn.setRequestProperty("Authorization", "Bearer " + token);
                    OutputStream outputStream = httpConn.getOutputStream();
                    outputStream.close();

                    int status = httpConn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_OK) {
                        // Do nothing
                        Util.readInput(httpConn.getInputStream());
                    } else {
                        String response = Util.readInput(httpConn.getErrorStream());
                        throw new IOException("Server returned non-OK status: " + status + ", response: " + response);
                    }
                } catch (IOException e) {
                    Log.e("getToken()", "Could not deauthorize token", e);
                }
            }
        });
    }

    public static String token(String query) {
        try {
            String post = "client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET + "&" + query;
            Log.d("getToken()", "post: " + post);
            URL url = new URL(TOKEN_URL);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setUseCaches(false);
            httpConn.setDoOutput(true); // indicates POST method
            httpConn.setDoInput(true);
            httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream outputStream = httpConn.getOutputStream();
            outputStream.write(post.getBytes("UTF-8"));
            outputStream.close();

            int status = httpConn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                String response = Util.readInput(httpConn.getInputStream());
                Log.d("getToken()", "token response: " + response);

                JSONObject json = new JSONObject(response);
                return json.getString("access_token");
            } else {
                String response = Util.readInput(httpConn.getErrorStream());
                throw new IOException("Server returned non-OK status: " + status + ", response: " + response);
            }
        } catch (IOException | JSONException e) {
            Log.e("getToken()", "Could not trade token", e);
            return null;
        }
    }
}
