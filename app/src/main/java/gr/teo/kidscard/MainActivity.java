package gr.teo.kidscard;

import android.app.Activity;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private MediaPlayer player;
    private int seq = 0;          // κάθε νέο πάτημα ακυρώνει το προηγούμενο
    private String pendingId;     // ποιο ζώο περιμένει τον ήχο του μετά το όνομα
    private String pendingFx;

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
                @Override public void onDone(String id) { runOnUiThread(() -> afterName(id)); }
                @Override public void onError(String id) { runOnUiThread(() -> afterName(id)); }
            });
        });
    }

    /** Τελείωσε το όνομα: παίζει ο ήχος του ζώου. */
    private void afterName(String uttId) {
        if (!String.valueOf(seq).equals(uttId) || pendingId == null) return;
        String id = pendingId, fx = pendingFx;
        pendingId = null;
        web.postDelayed(() -> {
            if (String.valueOf(seq).equals(uttId)) playSound(id, fx, seq);
        }, 150);
    }

    private void stopAll() {
        if (tts != null) tts.stop();
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private void playSound(String id, String fx, int mySeq) {
        try {
            AssetFileDescriptor afd = getAssets().openFd("sounds/" + id + ".ogg");
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(m -> { if (mySeq == seq) done(id); });
            mp.setOnErrorListener((m, w, e) -> { if (mySeq == seq) done(id); return true; });
            mp.prepare();
            player = mp;
            mp.start();
        } catch (Exception e) {
            // Αν λείπει ο ήχος, η φωνή λέει το «γαβ γαβ»
            if (ttsReady && fx != null && !fx.isEmpty()) {
                pendingId = null;
                tts.speak(fx, TextToSpeech.QUEUE_FLUSH, null, "fx" + mySeq);
                web.postDelayed(() -> { if (mySeq == seq) done(id); }, 1500);
            } else {
                done(id);
            }
        }
    }

    private void done(String id) {
        web.evaluateJavascript("window.onPlayDone&&window.onPlayDone('" + id + "')", null);
    }

    public class Bridge {
        @JavascriptInterface
        public void play(String id, String say, String fx) {
            runOnUiThread(() -> {
                seq++;
                stopAll();
                String uttId = String.valueOf(seq);
                if (ttsReady) {
                    pendingId = id;
                    pendingFx = fx;
                    tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, uttId);
                } else {
                    playSound(id, fx, seq);
                }
            });
        }

        @JavascriptInterface
        public String credits() {
            try (InputStream in = getAssets().open("credits.json")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
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
        seq++;
        stopAll();
    }

    @Override
    protected void onDestroy() {
        stopAll();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
