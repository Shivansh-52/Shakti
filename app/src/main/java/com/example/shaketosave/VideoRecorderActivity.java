package com.example.shaketosave;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class VideoRecorderActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 2001;
    private static final int RECORD_DURATION_SECONDS = 15;

    // Views
    private PreviewView previewView;
    private TextView timerText;
    private TextView recordingStatusText;
    private TextView statusMessage;
    private TextView centerCountdown;
    private View centerOverlay;
    private View recordingDot;
    private ProgressBar recordingProgress;
    private MaterialButton btnCancelRecording;
    private MaterialButton btnSwitchCamera;
    private MaterialButton btnShareMMS;
    private MaterialButton btnShareOther;
    private View shareButtonsLayout;

    // Camera
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private ProcessCameraProvider cameraProvider;
    private boolean isUsingBackCamera = true;
    private boolean isRecording = false;

    // Timer
    private CountDownTimer countDownTimer;

    // Data
    private String emergencyPhone;
    private String userName;
    private File videoFile;
    private Uri videoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on during recording
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_video_recorder);

        // Get data from intent
        emergencyPhone = getIntent().getStringExtra("emergency_phone");
        userName = getIntent().getStringExtra("user_name");

        initViews();
        setupListeners();

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        timerText = findViewById(R.id.timerText);
        recordingStatusText = findViewById(R.id.recordingStatusText);
        statusMessage = findViewById(R.id.statusMessage);
        centerCountdown = findViewById(R.id.centerCountdown);
        centerOverlay = findViewById(R.id.centerOverlay);
        recordingDot = findViewById(R.id.recordingDot);
        recordingProgress = findViewById(R.id.recordingProgress);
        btnCancelRecording = findViewById(R.id.btnCancelRecording);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnShareMMS = findViewById(R.id.btnShareMMS);
        btnShareOther = findViewById(R.id.btnShareOther);
        shareButtonsLayout = findViewById(R.id.shareButtonsLayout);

        statusMessage.setText(R.string.preparing_camera);
    }

    private void setupListeners() {
        btnCancelRecording.setOnClickListener(v -> cancelRecording());

        btnSwitchCamera.setOnClickListener(v -> {
            isUsingBackCamera = !isUsingBackCamera;
            if (cameraProvider != null) {
                // Stop current recording if active
                if (activeRecording != null) {
                    activeRecording.stop();
                }
                startCamera();
            }
        });

        btnShareMMS.setOnClickListener(v -> shareViaMMS());
        btnShareOther.setOnClickListener(v -> shareViaOther());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, getString(R.string.camera_not_available), Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        // Camera selector
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(isUsingBackCamera ?
                        CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT)
                .build();

        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Video capture
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
            // Start recording after camera is ready
            startVideoRecording();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.camera_not_available), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startVideoRecording() {
        if (videoCapture == null) return;

        // Create video file
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File videoDir = new File(getCacheDir(), "sos_videos");
        if (!videoDir.exists()) videoDir.mkdirs();
        videoFile = new File(videoDir, "SOS_" + timestamp + ".mp4");

        FileOutputOptions outputOptions = new FileOutputOptions.Builder(videoFile).build();

        // Check audio permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // Record without audio
            activeRecording = videoCapture.getOutput()
                    .prepareRecording(this, outputOptions)
                    .start(ContextCompat.getMainExecutor(this), this::handleVideoRecordEvent);
        } else {
            // Record with audio
            activeRecording = videoCapture.getOutput()
                    .prepareRecording(this, outputOptions)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(this), this::handleVideoRecordEvent);
        }

        isRecording = true;
        startCountdownTimer();
        startRecordingAnimation();
        statusMessage.setText(R.string.sos_video_recording);
    }

    private void handleVideoRecordEvent(VideoRecordEvent event) {
        if (event instanceof VideoRecordEvent.Start) {
            // Recording started
            runOnUiThread(() -> {
                recordingStatusText.setText(R.string.recording_sos_video);
            });
        } else if (event instanceof VideoRecordEvent.Finalize) {
            VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
            if (!finalizeEvent.hasError()) {
                // Recording completed successfully
                runOnUiThread(() -> {
                    videoUri = FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", videoFile);
                    onRecordingComplete();
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.video_recording_failed),
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }
    }

    private void startCountdownTimer() {
        centerOverlay.setVisibility(View.VISIBLE);

        countDownTimer = new CountDownTimer(RECORD_DURATION_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                timerText.setText(secondsLeft + "s");
                centerCountdown.setText(String.valueOf(secondsLeft));

                // Update progress bar
                int progress = (int) (((RECORD_DURATION_SECONDS * 1000L - millisUntilFinished)
                        * 100) / (RECORD_DURATION_SECONDS * 1000L));
                recordingProgress.setProgress(progress);

                // Hide center overlay after 3 seconds
                if (secondsLeft <= RECORD_DURATION_SECONDS - 3) {
                    centerOverlay.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                recordingProgress.setProgress(100);
                centerOverlay.setVisibility(View.GONE);
                stopRecording();
            }
        };
        countDownTimer.start();
    }

    private void startRecordingAnimation() {
        // Blinking red dot animation
        AlphaAnimation blinkAnimation = new AlphaAnimation(1.0f, 0.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        recordingDot.startAnimation(blinkAnimation);
    }

    private void stopRecording() {
        isRecording = false;
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        recordingDot.clearAnimation();
    }

    private void cancelRecording() {
        if (countDownTimer != null) countDownTimer.cancel();
        stopRecording();

        // Delete the video file if it exists
        if (videoFile != null && videoFile.exists()) {
            videoFile.delete();
        }

        Toast.makeText(this, getString(R.string.recording_cancelled), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onRecordingComplete() {
        // Show share buttons, hide recording controls
        btnCancelRecording.setVisibility(View.GONE);
        btnSwitchCamera.setVisibility(View.GONE);
        shareButtonsLayout.setVisibility(View.VISIBLE);

        statusMessage.setText(R.string.video_recording_complete);
        recordingStatusText.setText(R.string.video_saved);
        timerText.setText("✓");
        timerText.setTextColor(ContextCompat.getColor(this, R.color.success));

        // Stop recording dot animation
        recordingDot.clearAnimation();
        recordingDot.setVisibility(View.GONE);

        Toast.makeText(this, getString(R.string.video_recording_complete), Toast.LENGTH_LONG).show();

        // Auto-share via Email after a brief delay
        previewView.postDelayed(this::shareViaEmail, 1500);
    }

    private void shareViaEmail() {
        if (videoUri == null) return;

        try {
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
            
            String filePath = getRealPathFromURI(videoUri);
            if (filePath == null && videoFile != null) {
                filePath = videoFile.getAbsolutePath();
            }
            if (filePath == null) return;
            
            File vFile = new File(filePath);
            final String finalRecipient = recipientEmail;
            
            String subject = "🚨 URGENT SOS: Emergency Video - " + (userName != null ? userName : "Citizen");
            String messageBody = "🚨 [SURAKSHA SETU / SHAKTI AUTOMATED EMERGENCY ALERT]\n\n"
                    + "Emergency Video recorded from: " + (userName != null ? userName : "Citizen") + "\n"
                    + "Emergency Phone: " + (emergencyPhone != null ? emergencyPhone : "Not configured") + "\n"
                    + "Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n\n"
                    + "Automatic SOS video evidence attached.";

            // 1. Dispatch via Backend Relay
            try {
                com.example.shaketosave.api.EmergencyApi emergencyApi = com.example.shaketosave.api.ApiClient.getClient().create(com.example.shaketosave.api.EmergencyApi.class);
                okhttp3.RequestBody reqEmail = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), finalRecipient);
                okhttp3.RequestBody reqName = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), userName != null ? userName : "Citizen");
                okhttp3.RequestBody reqPhone = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), emergencyPhone != null ? emergencyPhone : "");
                okhttp3.RequestBody reqLocation = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), "Location coordinates broadcasted");
                okhttp3.RequestBody reqMsg = okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), messageBody);
                okhttp3.RequestBody reqFile = okhttp3.RequestBody.create(okhttp3.MediaType.parse("video/mp4"), vFile);
                okhttp3.MultipartBody.Part bodyPart = okhttp3.MultipartBody.Part.createFormData("video_file", vFile.getName(), reqFile);

                emergencyApi.sendVideoEmail(reqEmail, reqName, reqPhone, reqLocation, reqMsg, bodyPart)
                        .enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                            @Override
                            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> response) {
                                runOnUiThread(() -> Toast.makeText(VideoRecorderActivity.this,
                                        "✅ SOS Video automatically emailed to " + finalRecipient, Toast.LENGTH_LONG).show());
                            }

                            @Override
                            public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                                runOnUiThread(() -> Toast.makeText(VideoRecorderActivity.this,
                                        "✅ Emergency alert queued for " + finalRecipient, Toast.LENGTH_SHORT).show());
                            }
                        });
            } catch (Exception e) {
                Log.e("VideoRecorder", "Backend upload error", e);
            }

            // 2. Also try direct SMTP
            String senderEmail = prefs.getString("sender_email", "").trim();
            String senderPassword = prefs.getString("sender_password", "").trim();
            if (senderEmail.isEmpty() || senderPassword.isEmpty()) {
                senderEmail = "shivansh0962@gmail.com";
                senderPassword = "oidyywzqlqtuwxnl";
            }

            EmailSender emailSender = new EmailSender(senderEmail, senderPassword, finalRecipient,
                    subject, messageBody, vFile, new EmailSender.EmailCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> Toast.makeText(VideoRecorderActivity.this,
                            "SMTP Email sent to " + finalRecipient, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(String error) {
                    Log.e("VideoRecorder", "SMTP failed: " + error);
                }
            });
            emailSender.execute();

            Toast.makeText(this, "📧 Automatically emailing video to " + finalRecipient, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e("VideoRecorder", "Email dispatch error", e);
        }
    }
    
    private void saveVideoLocally() {
        try {
            // Create videos directory in main folder
            File videosDir = new File("/storage/emulated/0/videos");
            if (!videosDir.exists()) {
                videosDir.mkdirs();
            }
            
            // Create unique filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File localVideoFile = new File(videosDir, "SOS_Video_" + timestamp + ".mp4");
            
            // Copy video file to local directory
            String filePath = getRealPathFromURI(videoUri);
            if (filePath != null) {
                File sourceFile = new File(filePath);
                if (sourceFile.exists()) {
                    FileInputStream fis = new FileInputStream(sourceFile);
                    FileOutputStream fos = new FileOutputStream(localVideoFile);
                    
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                    
                    fis.close();
                    fos.close();
                    
                    Log.d("VideoRecorder", "Video saved locally: " + localVideoFile.getAbsolutePath());
                    Toast.makeText(this, "SOS video saved to device folder: videos/", Toast.LENGTH_LONG).show();
                    
                    // Show option to open the video
                    showOpenVideoDialog(localVideoFile);
                } else {
                    Log.e("VideoRecorder", "Source video file not found");
                    Toast.makeText(this, "Failed to save video locally", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("VideoRecorder", "Failed to get video file path");
                Toast.makeText(this, "Failed to save video locally", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e("VideoRecorder", "Failed to save video locally", e);
            Toast.makeText(this, "Error saving video to device", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showOpenVideoDialog(File videoFile) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Video Saved")
                .setMessage("SOS video saved to videos/ folder. Would you like to view it now?")
                .setPositiveButton("View Video", (dialog, which) -> {
                    Intent openIntent = new Intent(Intent.ACTION_VIEW);
                    openIntent.setDataAndType(Uri.fromFile(videoFile), "video/mp4");
                    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(openIntent);
                })
                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                .show();
    }
    
    private void openEmailIntent() {
        // Send email automatically without launching any chooser
        shareViaEmail();
    }
    
    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Video.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor != null) {
            try {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                cursor.moveToFirst();
                return cursor.getString(column_index);
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    
    private void shareViaMMS() {
        if (videoUri == null) return;

        try {
            Intent mmsIntent = new Intent(Intent.ACTION_SEND);
            mmsIntent.setType("video/mp4");
            mmsIntent.putExtra(Intent.EXTRA_STREAM, videoUri);
            mmsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Set recipient
            if (emergencyPhone != null && !emergencyPhone.isEmpty()) {
                mmsIntent.putExtra("address", emergencyPhone);
            }

            // Add SOS message
            String message = "SOS EMERGENCY VIDEO from " + (userName != null ? userName : "User")
                    + "! I need help!";
            mmsIntent.putExtra("sms_body", message);

            startActivity(mmsIntent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.share_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareViaOther() {
        if (videoUri == null) return;

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("video/mp4");
            shareIntent.putExtra(Intent.EXTRA_STREAM, videoUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String message = "🚨 SOS EMERGENCY VIDEO from " + (userName != null ? userName : "User")
                    + "! I need help!";
            shareIntent.putExtra(Intent.EXTRA_TEXT, message);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SOS Emergency Video");

            startActivity(Intent.createChooser(shareIntent, "Share SOS Video via"));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.share_failed), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            boolean cameraGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.CAMERA)) {
                    cameraGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }

            if (cameraGranted) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_needed),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        if (activeRecording != null) {
            activeRecording.stop();
        }
    }

    @Override
    public void onBackPressed() {
        if (isRecording) {
            cancelRecording();
        } else {
            super.onBackPressed();
        }
    }
}
