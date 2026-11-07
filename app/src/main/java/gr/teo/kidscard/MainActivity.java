package gr.teo.kidscard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private MediaPlayer player;

    // Τρέχουσα σειρά βημάτων (φωνή / ήχος ζώου). Κάθε νέα σειρά ακυρώνει την προηγούμενη.
    private int gen = 0;
    private String token = null;
    private JSONArray steps = null;
    private int idx = 0;

    private final AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.parseColor("#bfe6ff"));
        getWindow().setNavigationBarColor(Color.parseColor("#d9f7c8"));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#bfe6ff"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.setHapticFeedbackEnabled(false);
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        web.addJavascriptInterface(new Bridge(), "KidsBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(new Locale("el", "GR"));
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setSpeechRate(0.85f);
            tts.setPitch(1.15f);
            tts.setAudioAttributes(attrs);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { runOnUiThread(() -> stepDone(id)); }
                @Override public void onError(String id) { runOnUiThread(() -> stepDone(id)); }
            });
        });
    }

    private String uttId(int g, int i) { return g + "_" + i; }

    /** Τελείωσε ένα βήμα: συνεχίζει στο επόμενο μετά από μικρή παύση. */
    private void stepDone(String id) {
        if (!uttId(gen, idx).equals(id)) return;
        final int g = gen;
        web.postDelayed(() -> { if (g == gen) { idx++; nextStep(); } }, 150);
    }

    private void nextStep() {
        if (steps == null) return;
        if (idx >= steps.length()) {
            String t = token;
            steps = null;
            web.evaluateJavascript("window.onPlayDone&&window.onPlayDone('" + t + "')", null);
            return;
        }
        JSONObject st = steps.optJSONObject(idx);
        String id = uttId(gen, idx);
        if (st == null) { stepDone(id); return; }
        if (st.has("say")) {
            if (ttsReady) tts.speak(st.optString("say"), TextToSpeech.QUEUE_FLUSH, null, id);
            else stepDone(id);
        } else if (st.has("snd")) {
            playSound(st.optString("snd"), st.optString("fx", ""), id);
        } else {
            stepDone(id);
        }
    }

    private void playSound(String animal, String fx, String id) {
        try {
            AssetFileDescriptor afd = getAssets().openFd("sounds/" + animal + ".ogg");
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(m -> stepDone(id));
            mp.setOnErrorListener((m, w, e) -> { stepDone(id); return true; });
            mp.prepare();
            player = mp;
            mp.start();
        } catch (Exception e) {
            // Αν λείπει ο ήχος, η φωνή λέει το «γαβ γαβ»
            if (ttsReady && !fx.isEmpty()) tts.speak(fx, TextToSpeech.QUEUE_FLUSH, null, id);
            else stepDone(id);
        }
    }

    private void stopAll() {
        gen++;
        steps = null;
        if (tts != null) tts.stop();
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("kids", Context.MODE_PRIVATE);
    }

    public class Bridge {
        @JavascriptInterface
        public void run(String tok, String json) {
            runOnUiThread(() -> {
                stopAll();
                try { steps = new JSONArray(json); } catch (Exception e) { steps = new JSONArray(); }
                token = tok;
                idx = 0;
                nextStep();
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(MainActivity.this::stopAll);
        }

        @JavascriptInterface
        public String getPref(String key) {
            return prefs().getString(key, "");
        }

        @JavascriptInterface
        public void setPref(String key, String value) {
            prefs().edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public String asset(String name) {
            if (name.contains("..")) return "[]";
            try (InputStream in = getAssets().open(name)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "[]";
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Πλήρης οθόνη: κρύβει τις μπάρες ώστε το παιδί να μην πατάει κατά λάθος
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onBackPressed() {
        // Απενεργοποιημένο για να μη βγαίνει το παιδί κατά λάθος
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAll();
    }

    @Override
    protected void onDestroy() {
        stopAll();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
