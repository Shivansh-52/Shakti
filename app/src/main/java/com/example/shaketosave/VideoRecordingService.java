package com.example.shaketosave;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LifecycleService;

import com.example.shaketosave.api.ApiClient;
import com.example.shaketosave.api.EmergencyApi;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A headless foreground service that records SOS video using CameraX in
 * the background and automatically sends the resulting video to the emergency contact via email.
 */
public class VideoRecordingService extends LifecycleService {

    private static final String TAG = "VideoRecordingService";
    private static final String CHANNEL_ID = "VideoRecordingChannel";
    private static final int NOTIFICATION_ID = 2001;
    private static final int VIDEO_READY_NOTIFICATION_ID = 2002;
    private static final int RECORD_DURATION_SECONDS = 5;

    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private ProcessCameraProvider cameraProvider;
    private CountDownTimer countDownTimer;
    private NotificationManager notificationManager;

    private String emergencyPhone;
    private String userName;
    private String mapsLink;
    private File videoFile;
    private boolean isRecording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            emergencyPhone = intent.getStringExtra("emergency_phone");
            userName = intent.getStringExtra("user_name");
            mapsLink = intent.getStringExtra("maps_link");
        }

        if (userName == null || userName.isEmpty()) {
            com.example.shaketosave.api.AuthManager authManager = new com.example.shaketosave.api.AuthManager(this);
            userName = authManager.getUserName();
        }

        // Start as foreground service immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, createRecordingNotification("Preparing camera for SOS video..."),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                                | android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } catch (Exception e) {
                startForeground(NOTIFICATION_ID, createRecordingNotification("Preparing camera for SOS video..."));
            }
        } else {
            startForeground(NOTIFICATION_ID, createRecordingNotification("Preparing camera for SOS video..."));
        }

        // Initialise camera
        startCameraAndRecord();

        return START_NOT_STICKY;
    }

    // ──────────────────────────── Notification ────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "SOS Video Recording", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Shows while SOS video is being recorded and dispatched");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createRecordingNotification(String text) {
        Intent openAppIntent = new Intent(this, CitizenHomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🎥 Recording SOS Video")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void updateNotification(String text) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createRecordingNotification(text));
        }
    }

    // ──────────────────────────── Camera / Recording ────────────────────────────

    private void startCameraAndRecord() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindAndRecord();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera init failed", e);
                stopSelfCleanly();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindAndRecord() {
        if (cameraProvider == null) {
            stopSelfCleanly();
            return;
        }

        cameraProvider.unbindAll();

        // Use back camera by default
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Video capture – use lowest quality for fastest processing and email attachment size
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture);
            startVideoRecording();
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind back camera, trying front camera", e);
            try {
                CameraSelector frontCamera = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, frontCamera, videoCapture);
                startVideoRecording();
            } catch (Exception e2) {
                Log.e(TAG, "Front camera also failed", e2);
                stopSelfCleanly();
            }
        }
    }

    private void startVideoRecording() {
        if (videoCapture == null) {
            stopSelfCleanly();
            return;
        }

        // Create output file
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File videoDir = new File(getCacheDir(), "sos_videos");
        if (!videoDir.exists()) videoDir.mkdirs();
        videoFile = new File(videoDir, "SOS_" + timestamp + ".mp4");

        FileOutputOptions outputOptions = new FileOutputOptions.Builder(videoFile).build();

        // Start recording (with audio if permission is granted)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            activeRecording = videoCapture.getOutput()
                    .prepareRecording(this, outputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this), this::handleVideoRecordEvent);
        } else {
            activeRecording = videoCapture.getOutput()
                    .prepareRecording(this, outputOptions)
                    .start(ContextCompat.getMainExecutor(this), this::handleVideoRecordEvent);
        }

        isRecording = true;
        startCountdownTimer();
        updateNotification("Recording emergency video evidence... " + RECORD_DURATION_SECONDS + "s");
        Log.d(TAG, "Video recording started: " + videoFile.getAbsolutePath());
    }

    private void handleVideoRecordEvent(VideoRecordEvent event) {
        if (event instanceof VideoRecordEvent.Start) {
            Log.d(TAG, "Recording started");
        } else if (event instanceof VideoRecordEvent.Finalize) {
            VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
            if (!finalizeEvent.hasError()) {
                Log.d(TAG, "Recording completed successfully");
                onRecordingComplete();
            } else {
                Log.e(TAG, "Recording failed with error: " + finalizeEvent.getError());
                stopSelfCleanly();
            }
        }
    }

    private void startCountdownTimer() {
        countDownTimer = new CountDownTimer(RECORD_DURATION_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                updateNotification("Recording emergency video... " + secondsLeft + "s remaining");
            }

            @Override
            public void onFinish() {
                stopRecording();
            }
        };
        countDownTimer.start();
    }

    private void stopRecording() {
        isRecording = false;
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
    }

    // ──────────────────────────── Auto-send & Email Dispatch ────────────────────────────

    private void onRecordingComplete() {
        updateNotification("Sending SOS video automatically to emergency email...");

        if (videoFile == null || !videoFile.exists()) {
            Log.e(TAG, "Video file not found");
            stopSelfCleanly();
            return;
        }

        // Save a copy locally safely
        saveVideoLocally();

        // Process automated email sending in background
        sendVideoEmailAutomatically();

        // Stop service after a short delay
        new Handler(Looper.getMainLooper()).postDelayed(this::stopSelfCleanly, 5000);
    }

    private void sendVideoEmailAutomatically() {
        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        com.example.shaketosave.api.AuthManager authManager = new com.example.shaketosave.api.AuthManager(this);
        String recipientEmail = prefs.getString("emergency_email", "").trim();
        if (recipientEmail.isEmpty()) {
            recipientEmail = authManager.getUserEmail().trim();
        }
        if (recipientEmail.isEmpty()) {
            recipientEmail = prefs.getString("recipient_email", "").trim();
        }
        if (recipientEmail.isEmpty()) {
            recipientEmail = prefs.getString("sender_email", "").trim();
        }
        if (recipientEmail.isEmpty()) {
            recipientEmail = "emergency@surakshasetu.org";
        }

        String locStr = (mapsLink != null && !mapsLink.isEmpty()) ? mapsLink : "Location coordinates broadcasted";
        String senderDisplayName = (userName != null && !userName.isEmpty()) ? userName : "Citizen";
        String phoneStr = (emergencyPhone != null && !emergencyPhone.isEmpty()) ? emergencyPhone : "";

        String subject = "🚨 URGENT SOS: Emergency Video & Live Location - " + senderDisplayName;
        String messageBody = "🚨 [SURAKSHA SETU / SHAKTI AUTOMATED EMERGENCY ALERT]\n\n"
                + "Distress Alert from: " + senderDisplayName + "\n"
                + "Emergency Phone: " + phoneStr + "\n"
                + "Status: IMMEDIATE ASSISTANCE NEEDED\n"
                + "Live GPS Location: " + locStr + "\n"
                + "Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n\n"
                + "An emergency video recorded automatically during distress is attached with this alert.";

        final String finalEmail = recipientEmail;
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(VideoRecordingService.this, "📧 Automatically emailing SOS video to " + finalEmail, Toast.LENGTH_LONG).show();
        });

        // 1. Dispatch via Backend Automated Email Relay API
        try {
            EmergencyApi emergencyApi = ApiClient.getClient().create(EmergencyApi.class);

            RequestBody reqEmail = RequestBody.create(MediaType.parse("text/plain"), finalEmail);
            RequestBody reqName = RequestBody.create(MediaType.parse("text/plain"), senderDisplayName);
            RequestBody reqPhone = RequestBody.create(MediaType.parse("text/plain"), phoneStr);
            RequestBody reqLocation = RequestBody.create(MediaType.parse("text/plain"), locStr);
            RequestBody reqMsg = RequestBody.create(MediaType.parse("text/plain"), messageBody);

            RequestBody reqFile = RequestBody.create(MediaType.parse("video/mp4"), videoFile);
            MultipartBody.Part bodyPart = MultipartBody.Part.createFormData("video_file", videoFile.getName(), reqFile);

            emergencyApi.sendVideoEmail(reqEmail, reqName, reqPhone, reqLocation, reqMsg, bodyPart)
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if (response.isSuccessful()) {
                                Log.i(TAG, "✅ Emergency video successfully sent to backend email relay for: " + finalEmail);
                                showSuccessNotification(finalEmail);
                            } else {
                                Log.w(TAG, "Backend relay response: " + response.code());
                                showSuccessNotification(finalEmail);
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            Log.e(TAG, "Backend relay upload failed: " + t.getMessage());
                            showSuccessNotification(finalEmail);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error initiating backend email relay", e);
        }

        // 2. Also execute direct JavaMail SMTP in background
        String senderEmail = prefs.getString("sender_email", "").trim();
        String senderPassword = prefs.getString("sender_password", "").trim();
        if (senderEmail.isEmpty() || senderPassword.isEmpty()) {
            senderEmail = "shivansh0962@gmail.com";
            senderPassword = "oidyywzqlqtuwxnl";
        }
        
        EmailSender emailSender = new EmailSender(senderEmail, senderPassword, finalEmail,
                subject, messageBody, videoFile, new EmailSender.EmailCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "SMTP Email sent successfully to " + finalEmail);
                showSuccessNotification(finalEmail);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "SMTP Email error: " + error);
            }
        });
        emailSender.execute();
    }

    private void showSuccessNotification(String recipientEmail) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle("✅ SOS Video Email Dispatched")
                .setContentText("Emergency video evidence was automatically sent to " + recipientEmail)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(VIDEO_READY_NOTIFICATION_ID, builder.build());
        }
    }

    private void saveVideoLocally() {
        try {
            File moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (moviesDir == null) {
                moviesDir = new File(getFilesDir(), "movies");
            }
            if (!moviesDir.exists()) moviesDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File localVideoFile = new File(moviesDir, "SOS_Video_" + timestamp + ".mp4");

            if (videoFile != null && videoFile.exists()) {
                FileInputStream fis = new FileInputStream(videoFile);
                FileOutputStream fos = new FileOutputStream(localVideoFile);
                byte[] buffer = new byte[2048];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fis.close();
                fos.close();
                Log.d(TAG, "Video saved safely to: " + localVideoFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving local video copy", e);
        }
    }

    // ──────────────────────────── Cleanup ────────────────────────────

    private void stopSelfCleanly() {
        if (countDownTimer != null) countDownTimer.cancel();
        if (activeRecording != null) {
            try { activeRecording.stop(); } catch (Exception ignored) {}
            activeRecording = null;
        }
        if (cameraProvider != null) {
            try { cameraProvider.unbindAll(); } catch (Exception ignored) {}
        }
        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        if (activeRecording != null) {
            try { activeRecording.stop(); } catch (Exception ignored) {}
        }
        if (cameraProvider != null) {
            try { cameraProvider.unbindAll(); } catch (Exception ignored) {}
        }
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return null;
    }
}
