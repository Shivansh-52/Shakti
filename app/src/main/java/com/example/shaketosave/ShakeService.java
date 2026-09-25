package com.example.shaketosave;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.media.MediaPlayer;
import android.view.KeyEvent;
import android.telephony.SmsManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ShakeService extends Service implements ShakeDetector.OnShakeListener {

    private static final String CHANNEL_ID = "SafeShakeChannel";
    private static final String SOS_CHANNEL_ID = "SOSAlertChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SOS_NOTIFICATION_ID = 1002;
    private static final String PREFS_NAME = "SafeShakePrefs";
    private static final int SHAKE_THRESHOLD = 2;
    private static final int COUNTDOWN_SECONDS = 5;

    public static final String ACTION_SEND_NOW = "com.example.shaketosave.SEND_NOW";
    public static final String ACTION_CANCEL_SOS = "com.example.shaketosave.CANCEL_SOS";
    public static final String ACTION_START_COUNTDOWN = "com.example.shaketosave.START_COUNTDOWN";

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;
    private Vibrator vibrator;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private BroadcastReceiver bluetoothDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                if (!isSendingSOS && !isCountingDown) {
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300, 200, 300}, -1));
                    }
                    startSOSCountdown();
                }
            }
        }
    };
    private NotificationManager notificationManager;
    private CountDownTimer countDownTimer;
    private Handler handler;

    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private boolean hasLocation = false;
    private boolean isSendingSOS = false;
    private boolean isCountingDown = false;
    
    private MediaSession mediaSession;
    private long lastMediaButtonTime = 0;

    private BroadcastReceiver sosActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_SEND_NOW.equals(action)) {
                cancelCountdown();
                sendSOS();
            } else if (ACTION_CANCEL_SOS.equals(action)) {
                cancelSOS();
            } else if (ACTION_START_COUNTDOWN.equals(action)) {
                if (!isSendingSOS && !isCountingDown) {
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300, 200, 300}, -1));
                    }
                    startSOSCountdown();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannels();
        initSensors();
        initLocation();
        initMediaSession();
        registerSOSReceiver();
    }

    private void registerSOSReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SEND_NOW);
        filter.addAction(ACTION_CANCEL_SOS);
        filter.addAction(ACTION_START_COUNTDOWN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sosActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sosActionReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        
        if (intent != null && ACTION_START_COUNTDOWN.equals(intent.getAction())) {
            if (!isSendingSOS && !isCountingDown) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300, 200, 300}, -1));
                }
                startSOSCountdown();
            }
        }
        
        IntentFilter btFilter = new IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothDisconnectReceiver, btFilter);
        
        registerShakeListener();
        startLocationUpdates();
        if (mediaSession != null) mediaSession.setActive(true);
        return START_STICKY;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "SafeShake Protection", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps shake detection running in background");
            
            NotificationChannel sosChannel = new NotificationChannel(
                    SOS_CHANNEL_ID, "SOS Alerts", NotificationManager.IMPORTANCE_HIGH);
            sosChannel.setDescription("Emergency SOS countdown and alerts");
            sosChannel.enableVibration(true);
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                notificationManager.createNotificationChannel(sosChannel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeShake Active")
                .setContentText("Protection is running. Shake to send SOS.")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();
        shakeDetector.setOnShakeListener(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    currentLatitude = location.getLatitude();
                    currentLongitude = location.getLongitude();
                    hasLocation = true;
                }
            }
        };
    }

    private void initMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSession(this, "SOS_MediaSession");
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                    KeyEvent keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        int keyCode = keyEvent.getKeyCode();
                        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                            keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                            
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastMediaButtonTime < 1000) {
                                // Double tap detected
                                if (!isSendingSOS && !isCountingDown) {
                                    if (vibrator != null && vibrator.hasVibrator()) {
                                        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300, 200, 300}, -1));
                                    }
                                    startSOSCountdown();
                                }
                                lastMediaButtonTime = 0; // reset
                            } else {
                                lastMediaButtonTime = currentTime;
                            }
                            // Don't consume the event entirely so normal music play/pause still works
                            return false; 
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent);
                }
            });
            
            PlaybackState state = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                            PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build();
            mediaSession.setPlaybackState(state);
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            
            mediaSession.setCallback(new MediaSession.Callback() {
                private long firstMediaPressTime = 0;
                private int mediaPressCount = 0;

                @Override
                public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                    KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        int keyCode = event.getKeyCode();
                        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                            keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                            
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - firstMediaPressTime > 3000) {
                                mediaPressCount = 1;
                                firstMediaPressTime = currentTime;
                            } else {
                                mediaPressCount++;
                            }

                            if (mediaPressCount >= 3) {
                                if (!isSendingSOS && !isCountingDown) {
                                    if (vibrator != null && vibrator.hasVibrator()) {
                                        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300, 200, 300}, -1));
                                    }
                                    startSOSCountdown();
                                }
                                mediaPressCount = 0;
                                firstMediaPressTime = 0;
                            }
                            return true; // CONSUME IT
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent);
                }
            });

            // Aggressively steal audio focus
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setOnAudioFocusChangeListener(focusChange -> {
                                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                    // If we lose focus, steal it back!
                                    if (mediaSession != null) {
                                        mediaSession.setActive(false);
                                        mediaSession.setActive(true);
                                    }
                                }
                            })
                            .build();
                    audioManager.requestAudioFocus(request);
                } else {
                    audioManager.requestAudioFocus(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            if (mediaSession != null) {
                                mediaSession.setActive(false);
                                mediaSession.setActive(true);
                            }
                        }
                    }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                }
            }

            mediaSession.setActive(true);
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000).build();
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void registerShakeListener() {
        if (accelerometer != null) {
            sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterShakeListener() {
        if (sensorManager != null) sensorManager.unregisterListener(shakeDetector);
    }

    @Override
    public void onShake(int count) {
        if (isSendingSOS || isCountingDown) return;
        if (count < SHAKE_THRESHOLD) return;

        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300, 200, 300}, -1));
        }
        startSOSCountdown();
    }

    private void startSOSCountdown() {
        isCountingDown = true;
        countDownTimer = new CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                showCountdownNotification(secondsLeft);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }

            @Override
            public void onFinish() {
                isCountingDown = false;
                sendSOS();
            }
        };
        countDownTimer.start();
    }

    private void showCountdownNotification(int secondsLeft) {
        Intent sendIntent = new Intent(ACTION_SEND_NOW);
        sendIntent.setPackage(getPackageName());
        PendingIntent sendPendingIntent = PendingIntent.getBroadcast(this, 1, sendIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(ACTION_CANCEL_SOS);
        cancelIntent.setPackage(getPackageName());
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SOS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle("🚨 SOS ALERT - " + secondsLeft + " seconds")
                .setContentText("Emergency SMS will be sent automatically")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .addAction(0, "🚀 SEND NOW", sendPendingIntent)
                .addAction(0, "❌ CANCEL", cancelPendingIntent);

        if (notificationManager != null) notificationManager.notify(SOS_NOTIFICATION_ID, builder.build());
    }

    private void cancelCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        isCountingDown = false;
        dismissSOSNotification();
    }

    private void cancelSOS() {
        cancelCountdown();
        isSendingSOS = false;
        showResultNotification("SOS Cancelled", "Emergency alert was cancelled");
    }

    private void dismissSOSNotification() {
        if (notificationManager != null) notificationManager.cancel(SOS_NOTIFICATION_ID);
    }

    private void sendSOS() {
        isSendingSOS = true;
        dismissSOSNotification();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String phone1 = prefs.getString("emergency_phone1", "");
        String phone2 = prefs.getString("emergency_phone2", "");
        String phone3 = prefs.getString("emergency_phone3", "");
        com.example.shaketosave.api.AuthManager authManager = new com.example.shaketosave.api.AuthManager(this);
        String name = authManager.getUserName();
        if (name == null || name.trim().isEmpty()) {
            name = prefs.getString("user_name", "Citizen");
        }

        boolean hasAnyPhone = (!phone1.trim().isEmpty() || !phone2.trim().isEmpty() || !phone3.trim().isEmpty());
        if (!hasAnyPhone) {
            String defaultPhone = authManager.getUserPhone();
            if (defaultPhone != null && !defaultPhone.trim().isEmpty()) {
                phone1 = defaultPhone;
                hasAnyPhone = true;
            }
        }

        String mapsLink;
        if (hasLocation) {
            mapsLink = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", currentLatitude, currentLongitude);
        } else {
            mapsLink = "Location unavailable";
        }

        String customMsg = authManager.getSosMessage();

        // Keep message short for SMS
        String message = customMsg + " - " + name + " " + mapsLink;

        try {
            SmsManager smsManager = SmsManager.getDefault();
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            
            String[] phones = {phone1, phone2, phone3};
            int sentCount = 0;
            String firstValidPhone = "";
            
            for (String p : phones) {
                if (p != null && !p.trim().isEmpty()) {
                    if (firstValidPhone.isEmpty()) firstValidPhone = p;
                    if (parts.size() > 1) {
                        smsManager.sendMultipartTextMessage(p, null, parts, null, null);
                    } else {
                        smsManager.sendTextMessage(p, null, message, null, null);
                    }
                    sentCount++;
                }
            }
            
            final int finalSentCount = sentCount;
            final String videoPhone = firstValidPhone;
            final String finalName = name;
            final String finalMapsLink = mapsLink;
            
            handler.post(() -> {
                showResultNotification("SOS Sent!", "Emergency SMS sent to " + finalSentCount + " contacts");
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 100, 100, 100, 100, 100}, -1));
                }

                // Launch video recorder
                launchVideoRecorder(videoPhone, finalName, finalMapsLink);
            });
        } catch (Exception e) {
            handler.post(() -> showResultNotification("SOS Failed", "Error: " + e.getMessage()));
        }
        isSendingSOS = false;
    }

    private void showResultNotification(String title, String message) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SOS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (notificationManager != null) notificationManager.notify(SOS_NOTIFICATION_ID, builder.build());
    }

    private void launchVideoRecorder(String phone, String name, String mapsLink) {
        Intent videoIntent = new Intent(this, VideoRecordingService.class);
        videoIntent.putExtra("emergency_phone", phone);
        videoIntent.putExtra("user_name", name);
        videoIntent.putExtra("maps_link", mapsLink);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(videoIntent);
        } else {
            startService(videoIntent);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        unregisterShakeListener();
        try { unregisterReceiver(sosActionReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(bluetoothDisconnectReceiver); } catch (Exception ignored) {}
        if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback);
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }
}
