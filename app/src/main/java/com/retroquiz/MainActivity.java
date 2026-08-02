package com.retroquiz;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;

public class MainActivity extends AppCompatActivity {
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4478373683231277/1277462189";
    private static final String APP_OPEN_AD_UNIT_ID = "ca-app-pub-4478373683231277/9910510533";
    private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-4478373683231277/1438224223";
    private static final String LEADERBOARD_ID = "CgkI3Jj6kowbEAIQAw";

    private WebView webView;
    private InterstitialAd interstitialAd;
    private AppOpenAd appOpenAd;
    private RewardedAd rewardedAd;
    private boolean launchAdShown = false;

    private String interstitialStatus = "Sin intentar";
    private String appOpenStatus = "Sin intentar";
    private String rewardedStatus = "Sin intentar";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MobileAds.initialize(this, initializationStatus -> {});
        PlayGamesSdk.initialize(this);
        attemptPlayGamesSignIn();
        checkAppIntegrity();
        loadInterstitialAd();
        loadAppOpenAd();
        loadRewardedAd();

        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                result.confirm();
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                view.loadData("<html><body style='background:#0a0a2e;color:#ff6;padding:40px;font-size:18px'><h2>Error " + errorCode + "</h2><p>" + description + "</p></body></html>", "text/html", "UTF-8");
            }
        });

        try {
            InputStream is = getAssets().open("index.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            webView.loadDataWithBaseURL("https://localhost/", sb.toString(), "text/html", "UTF-8", null);
        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : "Unknown error";
            webView.loadData("<html><body style='background:#0a0a2e;color:white;padding:40px;text-align:center;font-size:18px'><h2>Error</h2><p>" + err + "</p></body></html>", "text/html", "UTF-8");
        }
    }

    // ===== INTERSTITIAL (antes de usar la IA) =====
    private void attemptPlayGamesSignIn() {
        GamesSignInClient signInClient = PlayGames.getGamesSignInClient(this);
        signInClient.signIn();
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript(
            "if (typeof handleBackPress === 'function') { handleBackPress(); }",
            null
        );
    }

    private void checkAppIntegrity() {
        try {
            IntegrityManager integrityManager = IntegrityManagerFactory.create(this);
            byte[] nonceBytes = new byte[16];
            new java.security.SecureRandom().nextBytes(nonceBytes);
            String nonce = android.util.Base64.encodeToString(nonceBytes,
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);

            IntegrityTokenRequest request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .setCloudProjectNumber(930973912156L)
                .build();

            integrityManager.requestIntegrityToken(request)
                .addOnSuccessListener(response -> sendIntegrityTokenToBackend(response.token()))
                .addOnFailureListener(e -> android.util.Log.w("PlayIntegrity", "No se pudo obtener el token", e));
        } catch (Exception e) {
            android.util.Log.w("PlayIntegrity", "Error iniciando chequeo de integridad", e);
        }
    }

    private void sendIntegrityTokenToBackend(String token) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://retroquiz-api.charly-tricks.dev/api/verify-integrity");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                String body = "{\"token\":\"" + token + "\"}";
                conn.getOutputStream().write(body.getBytes("UTF-8"));
                int code = conn.getResponseCode();
                java.io.InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                String responseBody = scanner.hasNext() ? scanner.next() : "";
                android.util.Log.d("PlayIntegrity", "Verificacion (" + code + "): " + responseBody);
            } catch (Exception e) {
                android.util.Log.w("PlayIntegrity", "No se pudo verificar con el backend", e);
            }
        }).start();
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, INTERSTITIAL_AD_UNIT_ID, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                interstitialAd = ad;
                interstitialStatus = "OK - cargado correctamente";
            }
            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                interstitialAd = null;
                interstitialStatus = "ERROR: " + loadAdError.getMessage() + " (codigo " + loadAdError.getCode() + ")";
            }
        });
    }

    private void showInterstitialIfReady() {
        if (interstitialAd != null) {
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    loadInterstitialAd();
                }
                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    interstitialAd = null;
                    loadInterstitialAd();
                }
            });
            interstitialAd.show(this);
        }
    }

    // ===== INICIO DE APLICACION (al abrir la app) =====
    private void loadAppOpenAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        AppOpenAd.load(this, APP_OPEN_AD_UNIT_ID, adRequest, new AppOpenAd.AppOpenAdLoadCallback() {
            @Override
            public void onAdLoaded(AppOpenAd ad) {
                appOpenAd = ad;
                appOpenStatus = "OK - cargado correctamente";
                if (!launchAdShown) {
                    launchAdShown = true;
                    showAppOpenAdIfReady();
                }
            }
            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                appOpenAd = null;
                appOpenStatus = "ERROR: " + loadAdError.getMessage() + " (codigo " + loadAdError.getCode() + ")";
            }
        });
    }

    private void showAppOpenAdIfReady() {
        if (appOpenAd != null) {
            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    appOpenAd = null;
                }
                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    appOpenAd = null;
                }
            });
            appOpenAd.show(this);
        }
    }

    // ===== RECOMPENSADO (revivir) =====
    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, REWARDED_AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                rewardedAd = ad;
                rewardedStatus = "OK - cargado correctamente";
            }
            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                rewardedAd = null;
                rewardedStatus = "ERROR: " + loadAdError.getMessage() + " (codigo " + loadAdError.getCode() + ")";
            }
        });
    }

    // ===== Puente JavaScript <-> Android =====
    public class WebAppInterface {
        @JavascriptInterface
        public void showAd() {
            runOnUiThread(() -> showInterstitialIfReady());
        }

        @JavascriptInterface
        public void getAdStatus(final String jsCallback) {
            runOnUiThread(() -> {
                String json = "{"
                    + "\"interstitial\":\"" + interstitialStatus.replace("\"", "'") + "\","
                    + "\"appOpen\":\"" + appOpenStatus.replace("\"", "'") + "\","
                    + "\"rewarded\":\"" + rewardedStatus.replace("\"", "'") + "\""
                    + "}";
                webView.evaluateJavascript(jsCallback + "(" + json + ")", null);
            });
        }

        @JavascriptInterface
        public void showRewardedRevive(final String jsCallback) {
            runOnUiThread(() -> {
                if (rewardedAd != null) {
                    final boolean[] rewardEarned = {false};
                    rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            rewardedAd = null;
                            loadRewardedAd();
                            if (!rewardEarned[0]) {
                                webView.evaluateJavascript(jsCallback + "(false)", null);
                            }
                        }
                        @Override
                        public void onAdFailedToShowFullScreenContent(AdError adError) {
                            rewardedAd = null;
                            loadRewardedAd();
                            webView.evaluateJavascript(jsCallback + "(false)", null);
                        }
                    });
                    rewardedAd.show(MainActivity.this, rewardItem -> {
                        rewardEarned[0] = true;
                        webView.evaluateJavascript(jsCallback + "(true)", null);
                    });
                } else {
                    webView.evaluateJavascript(jsCallback + "(false)", null);
                }
            });
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> finish());
        }

        @JavascriptInterface
        public void shareScore(String text) {
            runOnUiThread(() -> {
                android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, text);
                startActivity(android.content.Intent.createChooser(shareIntent, "Compartir puntaje"));
            });
        }

        @JavascriptInterface
        public void checkPlayGamesSignedIn(final String jsCallback) {
            runOnUiThread(() -> {
                GamesSignInClient client = PlayGames.getGamesSignInClient(MainActivity.this);
                client.isAuthenticated().addOnCompleteListener(task -> {
                    boolean isAuth = task.isSuccessful() && task.getResult().isAuthenticated();
                    webView.evaluateJavascript(jsCallback + "(" + isAuth + ")", null);
                });
            });
        }

        @JavascriptInterface
        public void playGamesSignIn() {
            runOnUiThread(() -> {
                GamesSignInClient client = PlayGames.getGamesSignInClient(MainActivity.this);
                client.signIn();
            });
        }

        @JavascriptInterface
        public void submitToPlayGamesLeaderboard(long score) {
            runOnUiThread(() -> {
                LeaderboardsClient client = PlayGames.getLeaderboardsClient(MainActivity.this);
                client.submitScore(LEADERBOARD_ID, score);
            });
        }

        @JavascriptInterface
        public void showPlayGamesLeaderboard() {
            runOnUiThread(() -> {
                LeaderboardsClient client = PlayGames.getLeaderboardsClient(MainActivity.this);
                client.getLeaderboardIntent(LEADERBOARD_ID)
                    .addOnSuccessListener(intent -> startActivityForResult(intent, 9004))
                    .addOnFailureListener(e ->
                        webView.evaluateJavascript("showModal('🎮 Google Play Games','Inicia sesi\\u00f3n con tu cuenta de Google primero.')", null)
                    );
            });
        }
    }
}
