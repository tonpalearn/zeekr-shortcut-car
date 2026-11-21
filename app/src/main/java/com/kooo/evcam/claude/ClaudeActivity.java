/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kooo.evcam.AppLog;
import com.kooo.evcam.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * หน้าจอ Claude: พิมพ์หรือพูดถาม → ส่ง Claude → แสดงคำตอบ + อ่านออกเสียง.
 *
 * <p>เปิดได้ 3 ทาง: เมนูข้าง (Claude) · ปุ่มลัด/สติกเกอร์/ปุ่มลอยที่ตั้งเป็น "ถาม Claude"
 * (เปิดแล้วเริ่มฟังทันที, {@link #EXTRA_LISTEN}) · กดซ้ำตอนเปิดอยู่ = ฟังรอบใหม่</p>
 *
 * <p>รถวิ่ง (ความเร็ว &gt; {@link ClaudePrompt#MOVING_KMH}): ซ่อนช่องพิมพ์ เหลือแต่ปุ่มไมค์ และสั่ง Claude ให้ตอบสั้น</p>
 */
public class ClaudeActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    public static final String EXTRA_LISTEN = "com.tonpalearn.tplzeekr.LISTEN";
    /** ปุ่มตั้งให้เปิดแอปทางการ แต่ไม่พบแอป (ClaudeLauncher) */
    public static final String EXTRA_OFFICIAL_MISSING = "com.tonpalearn.tplzeekr.OFFICIAL_MISSING";

    private static final String TAG = "ClaudeActivity";
    private static final int REQ_MIC = 4101;
    private static final int REQ_SPEECH_ACTIVITY = 4102;
    private static final int MAX_TURNS = 24;
    private static final long SPEED_POLL_MS = 1000;

    private static final Locale THAI = new Locale("th", "TH");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<ClaudeClient.Turn> history = Collections.synchronizedList(new ArrayList<>());

    private ClaudePrefs prefs;

    private ScrollView scroll;
    private LinearLayout messages;
    private TextView status;
    private EditText input;
    private MaterialButton send;
    private MaterialButton mic;

    private TextToSpeech tts;
    private boolean ttsReady;
    /** null = ยังไม่รู้ (TTS ยังโหลดไม่เสร็จ) */
    private Boolean ttsThai;
    private boolean toldNoThaiVoice;

    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean busy;
    private boolean moving;
    private boolean pendingListen;

    private final Runnable speedPoll = new Runnable() {
        @Override
        public void run() {
            applyDrivingState(ClaudePrompt.isMoving(CarSpeed.kmh()));
            main.postDelayed(this, SPEED_POLL_MS);
        }
    };

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        com.kooo.evcam.screen.ScreenState.userActed("ui");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claude);
        prefs = new ClaudePrefs(this);

        scroll = findViewById(R.id.claude_scroll);
        messages = findViewById(R.id.claude_messages);
        status = findViewById(R.id.claude_status);
        input = findViewById(R.id.claude_input);
        send = findViewById(R.id.claude_send);
        mic = findViewById(R.id.claude_mic);

        findViewById(R.id.claude_back).setOnClickListener(v -> finish());
        findViewById(R.id.claude_settings).setOnClickListener(v -> showSettings());
        findViewById(R.id.claude_check).setOnClickListener(v -> showReadiness());
        findViewById(R.id.claude_clear).setOnClickListener(v -> clearConversation());
        findViewById(R.id.claude_official).setOnClickListener(v -> {
            if (tts != null) {
                tts.stop();
            }
            if (!ClaudeLauncher.openOfficial(this)) {
                addBubble(false, getString(R.string.claude_official_missing), true);
            }
        });
        send.setOnClickListener(v -> sendTyped());
        mic.setOnClickListener(v -> onMicPressed());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped();
                return true;
            }
            return false;
        });

        addBubble(false, getString(R.string.claude_welcome), true);
        noteOfficialMissing(getIntent());
        tts = new TextToSpeech(this, this);
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_LISTEN, false)) {
            pendingListen = true;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        noteOfficialMissing(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_LISTEN, false)) {
            startListening();
        }
    }

    private void noteOfficialMissing(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_OFFICIAL_MISSING, false)) {
            addBubble(false, getString(R.string.claude_official_missing_msg), true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ปุ่ม "เปิดแอป Claude" โชว์เฉพาะเมื่อมีแอปทางการติดตั้งอยู่
        findViewById(R.id.claude_official).setVisibility(
                ClaudeLauncher.officialInstalled(this) ? View.VISIBLE : View.GONE);
        main.removeCallbacks(speedPoll);
        main.post(speedPoll);
        if (pendingListen) {
            pendingListen = false;
            main.postDelayed(this::startListening, 400);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(speedPoll);
        stopListening();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ รถวิ่ง / จอด

    private void applyDrivingState(boolean nowMoving) {
        if (nowMoving == moving) {
            return;
        }
        moving = nowMoving;
        int vis = moving ? View.GONE : View.VISIBLE;
        input.setVisibility(vis);
        send.setVisibility(vis);
        if (moving) {
            hideKeyboard();
            if (!busy && !listening) {
                setStatus(getString(R.string.claude_status_moving));
            }
        } else if (!busy && !listening) {
            setStatus(getString(R.string.claude_status_ready));
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        input.clearFocus();
    }

    // ------------------------------------------------------------------ ถาม Claude

    private void sendTyped() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setText("");
        hideKeyboard();
        ask(text);
    }

    private void ask(String question) {
        if (busy) {
            return;
        }
        if (!prefs.hasKey()) {
            String msg = getString(R.string.claude_need_key);
            if (ClaudeLauncher.officialInstalled(this)) {
                msg += " หรือกดปุ่ม “เปิดแอป Claude” ด้านบน เพื่อใช้บัญชี subscription";
            }
            addBubble(false, msg, true);
            speak(msg);
            return;
        }
        addBubble(true, question, false);
        history.add(new ClaudeClient.Turn("user", question));
        trimHistory();
        busy = true;
        setStatus(getString(R.string.claude_status_thinking));
        mic.setEnabled(false);
        send.setEnabled(false);

        final List<ClaudeClient.Turn> snapshot = new ArrayList<>(history);
        final String key = prefs.apiKey();
        final String model = prefs.model();
        final Float speed = CarSpeed.kmh();
        final String system = ClaudePrompt.system(speed, now());
        final int maxTokens = ClaudePrompt.isMoving(speed)
                ? ClaudePrompt.MAX_TOKENS_MOVING : ClaudePrompt.MAX_TOKENS_PARKED;
        worker.execute(() -> {
            try {
                String reply = ClaudeClient.ask(key, model, system, snapshot, maxTokens);
                main.post(() -> onReply(reply));
            } catch (ClaudeClient.Failure f) {
                AppLog.w(TAG, "Claude failed: " + f.getMessage());
                main.post(() -> onFailure(f));
            }
        });
    }

    private void onReply(String reply) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        busy = false;
        mic.setEnabled(true);
        send.setEnabled(true);
        history.add(new ClaudeClient.Turn("assistant", reply));
        addBubble(false, reply, false);
        if (!speak(reply)) {
            setStatus(readyText());
        }
    }

    private void onFailure(ClaudeClient.Failure f) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        busy = false;
        mic.setEnabled(true);
        send.setEnabled(true);
        // ถอนคำถามที่ไม่ได้คำตอบออก ให้บทสนทนาสลับ user/assistant ถูกเสมอ
        synchronized (history) {
            if (!history.isEmpty() && "user".equals(history.get(history.size() - 1).role)) {
                history.remove(history.size() - 1);
            }
        }
        addBubble(false, "⚠ " + f.thaiMessage, true);
        if (!speak(f.thaiMessage)) {
            setStatus(readyText());
        }
    }

    private void trimHistory() {
        synchronized (history) {
            while (history.size() > MAX_TURNS) {
                history.remove(0);
            }
            // ข้อความแรกต้องเป็น user เสมอ (กติกาของ API)
            while (!history.isEmpty() && !"user".equals(history.get(0).role)) {
                history.remove(0);
            }
        }
    }

    private void clearConversation() {
        if (busy) {
            return;
        }
        history.clear();
        messages.removeAllViews();
        if (tts != null) {
            tts.stop();
        }
        addBubble(false, getString(R.string.claude_welcome), true);
        setStatus(readyText());
    }

    private String readyText() {
        return getString(moving ? R.string.claude_status_moving : R.string.claude_status_ready);
    }

    private static String now() {
        return new SimpleDateFormat("EEEE d MMMM yyyy HH:mm", new Locale("th", "TH")).format(new Date());
    }

    // ------------------------------------------------------------------ ฟังเสียง

    private void onMicPressed() {
        if (listening) {
            stopListening();
            setStatus(readyText());
        } else {
            startListening();
        }
    }

    private void startListening() {
        if (busy || listening || isFinishing()) {
            return;
        }
        if (tts != null) {
            tts.stop();
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        Intent intent = speechIntent();
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new Listener());
            }
            listening = true;
            mic.setText(R.string.claude_status_listening);
            setStatus(getString(R.string.claude_status_listening));
            try {
                recognizer.startListening(intent);
            } catch (RuntimeException e) {
                AppLog.w(TAG, "startListening failed", e);
                listening = false;
                mic.setText(R.string.claude_mic);
                addBubble(false, getString(R.string.claude_no_stt), true);
            }
            return;
        }
        // ไม่มีบริการฟังเสียงเบื้องหลัง: ลองหน้าต่างฟังเสียงของระบบ (ถ้ามี)
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                startActivityForResult(intent, REQ_SPEECH_ACTIVITY);
                return;
            } catch (RuntimeException e) {
                AppLog.w(TAG, "speech activity failed", e);
            }
        }
        String msg = getString(R.string.claude_no_stt);
        addBubble(false, msg, true);
        speak(msg);
    }

    private void stopListening() {
        if (!listening) {
            return;
        }
        listening = false;
        mic.setText(R.string.claude_mic);
        if (recognizer != null) {
            recognizer.cancel();
        }
    }

    private static Intent speechIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "th-TH");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        return intent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                addBubble(false, getString(R.string.claude_mic_denied), true);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH_ACTIVITY && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && !results.get(0).trim().isEmpty()) {
                ask(results.get(0).trim());
            }
        }
    }

    private final class Listener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            if (!listening) {
                return;
            }
            listening = false;
            mic.setText(R.string.claude_mic);
            String msg;
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    msg = getString(R.string.claude_heard_nothing);
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    msg = getString(R.string.claude_mic_denied);
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    msg = "แปลงเสียงไม่สำเร็จ เพราะต่อเน็ตไม่ได้";
                    break;
                case 12: // ERROR_LANGUAGE_NOT_SUPPORTED (API 31)
                case 13: // ERROR_LANGUAGE_UNAVAILABLE (API 31)
                    msg = "ระบบฟังเสียงของจอรถไม่รองรับภาษาไทย";
                    break;
                default:
                    msg = "ฟังเสียงไม่สำเร็จ (รหัส " + error + ")";
            }
            setStatus(msg);
            AppLog.w(TAG, "speech error " + error);
        }

        @Override
        public void onResults(Bundle results) {
            listening = false;
            mic.setText(R.string.claude_mic);
            ArrayList<String> list = results == null ? null
                    : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = list == null || list.isEmpty() ? "" : list.get(0).trim();
            if (text.isEmpty()) {
                setStatus(getString(R.string.claude_heard_nothing));
            } else {
                ask(text);
            }
        }

        @Override
        public void onPartialResults(Bundle partial) {
            ArrayList<String> list = partial == null ? null
                    : partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty() && !list.get(0).trim().isEmpty()) {
                setStatus("“" + list.get(0).trim() + "”");
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    // ------------------------------------------------------------------ อ่านออกเสียง

    @Override
    public void onInit(int result) {
        if (tts == null) {
            return;
        }
        ttsReady = result == TextToSpeech.SUCCESS;
        if (ttsReady) {
            int lang = tts.isLanguageAvailable(THAI);
            ttsThai = lang >= TextToSpeech.LANG_AVAILABLE;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    main.post(() -> {
                        if (!busy && !listening) {
                            setStatus(readyText());
                        }
                    });
                }

                @Override
                public void onError(String utteranceId) {
                    onDone(utteranceId);
                }
            });
        } else {
            ttsThai = false;
        }
        AppLog.i(TAG, "TTS ready=" + ttsReady + " thai=" + ttsThai);
    }

    /** @return true ถ้าเริ่มพูดจริง */
    private boolean speak(String text) {
        if (!prefs.speak() || tts == null || !ttsReady || text == null) {
            return false;
        }
        String clean = ClaudePrompt.forSpeech(text);
        boolean thaiText = clean.matches("(?s).*[\\u0E00-\\u0E7F].*");
        Locale locale;
        if (thaiText) {
            if (!Boolean.TRUE.equals(ttsThai)) {
                if (!toldNoThaiVoice) {
                    toldNoThaiVoice = true;
                    addBubble(false, getString(R.string.claude_no_thai_tts), true);
                }
                return false;
            }
            locale = THAI;
        } else {
            locale = Locale.US;
        }
        tts.setLanguage(locale);
        int max = TextToSpeech.getMaxSpeechInputLength();
        if (clean.length() > max) {
            clean = clean.substring(0, max);
        }
        int r = tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "claude-" + SystemClock.uptimeMillis());
        if (r == TextToSpeech.SUCCESS) {
            setStatus(getString(R.string.claude_status_speaking));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ ตั้งค่า

    private void showSettings() {
        int pad = dp(24);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(8), pad, 0);

        SwitchCompat useApp = new SwitchCompat(this);
        useApp.setText(R.string.claude_settings_use_app);
        useApp.setTextAppearance(R.style.TextAppearance_Cam_Body);
        useApp.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        useApp.setChecked(prefs.useOfficialApp());
        useApp.setPadding(0, 0, 0, dp(4));
        box.addView(useApp);
        box.addView(small(getString(R.string.claude_settings_use_app_note,
                getString(ClaudeLauncher.officialInstalled(this)
                        ? R.string.claude_official_installed : R.string.claude_official_missing))));

        TextView keyLabel = label(getString(R.string.claude_settings_key));
        keyLabel.setPadding(0, dp(18), 0, 0);
        box.addView(keyLabel);
        EditText key = new EditText(this);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSingleLine(true);
        key.setHint("sk-ant-…");
        box.addView(key);
        TextView keyNow = small(prefs.hasKey()
                ? getString(R.string.claude_settings_key_current, ClaudePrefs.masked(prefs.apiKey()))
                : getString(R.string.claude_settings_key_none));
        box.addView(keyNow);

        TextView modelLabel = label(getString(R.string.claude_settings_model));
        modelLabel.setPadding(0, dp(16), 0, 0);
        box.addView(modelLabel);
        RadioGroup models = new RadioGroup(this);
        for (int i = 0; i < ClaudePrefs.MODELS.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setTag(ClaudePrefs.MODELS[i]);
            rb.setText(ClaudePrefs.MODEL_LABELS[i]);
            rb.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            models.addView(rb);
            if (i == prefs.modelIndex()) {
                rb.setChecked(true);
            }
        }
        box.addView(models);

        SwitchCompat speakSwitch = new SwitchCompat(this);
        speakSwitch.setText(R.string.claude_settings_speak);
        speakSwitch.setTextAppearance(R.style.TextAppearance_Cam_Body);
        speakSwitch.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        speakSwitch.setChecked(prefs.speak());
        speakSwitch.setPadding(0, dp(12), 0, dp(4));
        box.addView(speakSwitch);

        box.addView(small(getString(R.string.claude_settings_note)));


        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this, R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle(R.string.claude_settings)
                .setView(sv)
                .setNegativeButton(R.string.claude_cancel, null)
                .setPositiveButton(R.string.claude_save, (d, w) -> {
                    String typed = key.getText().toString().trim();
                    if (!typed.isEmpty()) {
                        prefs.setApiKey(typed);
                    }
                    View checked = models.findViewById(models.getCheckedRadioButtonId());
                    if (checked != null && checked.getTag() != null) {
                        prefs.setModel(checked.getTag().toString());
                    }
                    prefs.setSpeak(speakSwitch.isChecked());
                    prefs.setUseOfficialApp(useApp.isChecked());
                    if (!prefs.speak() && tts != null) {
                        tts.stop();
                    }
                });
        if (prefs.hasKey()) {
            b.setNeutralButton(R.string.claude_settings_remove_key, (d, w) -> prefs.setApiKey(""));
        }
        b.show();
    }

    // ------------------------------------------------------------------ ตรวจความพร้อม

    private void showReadiness() {
        Float speed = CarSpeed.kmh();
        StringBuilder sb = new StringBuilder();
        sb.append(line(prefs.hasKey(), "กุญแจ Claude API",
                prefs.hasKey() ? "ใส่แล้ว " + ClaudePrefs.masked(prefs.apiKey()) : "ยังไม่ได้ใส่ (กดปุ่มตั้งค่า มุมขวาบนสุด)"));
        sb.append(line(true, "รุ่น", prefs.model()));
        sb.append(line(hasInternet(), "อินเทอร์เน็ต", hasInternet() ? "ต่ออยู่" : "ไม่ได้ต่อ"));
        boolean stt = SpeechRecognizer.isRecognitionAvailable(this);
        boolean sttActivity = speechIntent().resolveActivity(getPackageManager()) != null;
        sb.append(line(stt || sttActivity, "แปลงเสียงเป็นข้อความ",
                stt ? "มี (ฟังเบื้องหลัง)" : sttActivity ? "มี (หน้าต่างของระบบ)" : "ไม่มี — ใช้พิมพ์แทน"));
        boolean mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        sb.append(line(mic, "สิทธิ์ไมโครโฟน", mic ? "อนุญาตแล้ว" : "ยังไม่อนุญาต (จะถามตอนกดไมค์)"));
        String ttsText = !ttsReady ? (ttsThai == null ? "กำลังโหลด… ลองเปิดหน้านี้ใหม่" : "ไม่มีระบบเสียงอ่าน")
                : Boolean.TRUE.equals(ttsThai) ? "มีเสียงไทย" : "ไม่มีเสียงไทย (อ่านได้เฉพาะคำตอบภาษาอังกฤษ)";
        sb.append(line(ttsReady && Boolean.TRUE.equals(ttsThai), "เสียงอ่านภาษาไทย", ttsText));
        String official = ClaudeLauncher.officialVersion(this);
        sb.append(line(official != null, "แอป Claude ทางการ",
                (official != null ? "ติดตั้งแล้ว v" + official : "ไม่พบ (" + ClaudeLauncher.OFFICIAL_PACKAGE + ")")
                        + (prefs.useOfficialApp() ? " · ปุ่มถาม Claude ตั้งให้เปิดแอปนี้" : "")));
        sb.append(line(speed != null, "ความเร็วรถ",
                speed == null ? "อ่านไม่ได้ (ไม่ได้อยู่บนรถ?)" : String.format(Locale.US, "%.0f กม./ชม.", speed)));

        new MaterialAlertDialogBuilder(this, R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle(R.string.claude_check_title)
                .setMessage(sb.toString().trim())
                .setNegativeButton(R.string.claude_close, null)
                .setPositiveButton(R.string.claude_check_ping, (d, w) -> ping())
                .show();
    }

    /** เรียก Claude หนึ่งครั้งเพื่อวัดว่าใช้ได้และนานแค่ไหน (ไม่ใส่ในบทสนทนา) */
    private void ping() {
        if (busy) {
            return;
        }
        if (!prefs.hasKey()) {
            addBubble(false, getString(R.string.claude_need_key), true);
            return;
        }
        busy = true;
        setStatus(getString(R.string.claude_status_thinking));
        final String key = prefs.apiKey();
        final String model = prefs.model();
        final long start = SystemClock.elapsedRealtime();
        worker.execute(() -> {
            String result;
            try {
                List<ClaudeClient.Turn> one = new ArrayList<>();
                one.add(new ClaudeClient.Turn("user", getString(R.string.claude_ping_prompt)));
                String reply = ClaudeClient.ask(key, model, null, one, 20);
                result = String.format(Locale.US, "✓ เรียก %s สำเร็จใน %.1f วินาที: %s", model,
                        (SystemClock.elapsedRealtime() - start) / 1000f, reply);
            } catch (ClaudeClient.Failure f) {
                result = "⚠ ทดสอบไม่ผ่าน: " + f.thaiMessage + " (" + f.getMessage() + ")";
            }
            final String text = result;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                busy = false;
                addBubble(false, text, true);
                setStatus(readyText());
            });
        });
    }

    private boolean hasInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        try {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException e) {
            return false;
        }
    }

    private static String line(boolean ok, String what, String value) {
        return (ok ? "✓ " : "✗ ") + what + ": " + value + "\n\n";
    }

    // ------------------------------------------------------------------ แสดงผล

    private void setStatus(String text) {
        status.setText(text);
    }

    /** fromUser = ฝั่งผู้ใช้ (ชิดขวา) · note = ข้อความของแอปเอง (สีจาง) */
    private void addBubble(boolean fromUser, String text, boolean note) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(fromUser ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapLp.topMargin = dp(10);
        wrap.setLayoutParams(wrapLp);

        TextView who = new TextView(this);
        who.setText(fromUser ? getString(R.string.claude_you) : getString(R.string.claude_title));
        who.setTextAppearance(R.style.TextAppearance_Cam_Caption);
        who.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
        wrap.addView(who, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText(text);
        body.setTextIsSelectable(!fromUser);
        body.setTextAppearance(R.style.TextAppearance_Cam_Row);
        body.setTextColor(ContextCompat.getColor(this,
                note ? R.color.text_secondary : R.color.text_primary));
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(ContextCompat.getColor(this, fromUser ? R.color.sunken : R.color.surface));
        body.setBackground(bg);
        body.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
        // กว้างตามเนื้อความ: คำถามชิดขวา คำตอบชิดซ้าย
        wrap.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        messages.addView(wrap);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextAppearance(R.style.TextAppearance_Cam_Body);
        t.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        return t;
    }

    private TextView small(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextAppearance(R.style.TextAppearance_Cam_Caption);
        t.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
