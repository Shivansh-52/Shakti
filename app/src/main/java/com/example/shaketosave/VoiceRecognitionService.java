package com.example.shaketosave;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VoiceRecognitionService extends Service {

    private static final String TAG = "VoiceSOSService";
    private static final String CHANNEL_ID = "VoiceServiceChannel";
    private static final int NOTIFICATION_ID = 1003;

    private SpeechRecognizer speechRecognizer;
    private Handler mainHandler;
    private Vibrator vibrator;
    private boolean isListening = false;
    private boolean isDestroyed = false;

    private final List<String> emergencyTriggers = Arrays.asList(
            "help", "sos", "save me", "danger", "bachao", "police", "shakti", "suraksha", "emergency", "stop", "attack"
    );

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannel();
        initSpeechRecognizer();
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition is not available on this device.");
            return;
        }

        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    if (isDestroyed) return;
                    // Restart listening after a short pause
                    mainHandler.postDelayed(() -> {
                        if (!isDestroyed) {
                            startListening();
                        }
                    }, 800);
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    handleSpeechResults(results);
                    if (!isDestroyed) {
                        mainHandler.postDelayed(() -> startListening(), 400);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    handleSpeechResults(partialResults);
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer", e);
        }
    }

    private void handleSpeechResults(Bundle results) {
        if (results == null) return;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        String customTrigger = prefs.getString("voice_trigger_word", "help").toLowerCase().trim();

        for (String match : matches) {
            if (match == null) continue;
            String text = match.toLowerCase();
            Log.d(TAG, "Recognized text: " + text);

            boolean triggered = text.contains(customTrigger);
            if (!triggered) {
                for (String word : emergencyTriggers) {
                    if (text.contains(word)) {
                        triggered = true;
                        break;
                    }
                }
            }

            if (triggered) {
                triggerVoiceSos();
                break;
            }
        }
    }

    private void triggerVoiceSos() {
        Log.i(TAG, "Voice SOS Triggered!");
        mainHandler.post(() -> {
            Toast.makeText(VoiceRecognitionService.this, "🎙️ Voice SOS Keyword Detected!", Toast.LENGTH_LONG).show();
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300}, -1));
                } else {
                    vibrator.vibrate(500);
                }
            }

            // Broadcast to ShakeService to begin countdown
            Intent countdownIntent = new Intent(ShakeService.ACTION_START_COUNTDOWN);
            countdownIntent.setPackage(getPackageName());
            sendBroadcast(countdownIntent);

            // Also open EmergencyActivity directly
            try {
                Intent emergencyIntent = new Intent(VoiceRecognitionService.this, EmergencyActivity.class);
                emergencyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(emergencyIntent);
            } catch (Exception ignored) {}
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("🎙️ Suraksha Voice SOS Active")
                    .setContentText("Listening for emergency keywords ('Help', 'SOS', 'Bachao')...")
                    .setSmallIcon(R.drawable.ic_shield)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } catch (Exception e) {
                    startForeground(NOTIFICATION_ID, notification);
                }
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        }

        startListening();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice SOS Recognition",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps voice SOS detection active in background");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void startListening() {
        if (isListening || isDestroyed) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted for Voice SOS.");
            return;
        }

        try {
            if (speechRecognizer == null) {
                initSpeechRecognizer();
            }
            if (speechRecognizer != null) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                speechRecognizer.startListening(intent);
                isListening = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in startListening", e);
            isListening = false;
        }
    }

    @Override
    public void onDestroy() {
        isDestroyed = true;
        super.onDestroy();
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}