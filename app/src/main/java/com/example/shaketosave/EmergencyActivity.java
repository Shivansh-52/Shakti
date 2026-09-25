package com.example.shaketosave;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.example.shaketosave.utils.ShakeDetector;

public class EmergencyActivity extends AppCompatActivity {
    
    private MaterialCardView btnSosHold;
    private ProgressBar progressSos;
    private TextView tvInstruction, tvCountdown;
    private Button btnCancelSos;
    
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private Vibrator mVibrator;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private int holdProgress = 0;
    private boolean isSosTriggered = false;
    
    private Runnable holdRunnable = new Runnable() {
        @Override
        public void run() {
            if (holdProgress < 100) {
                holdProgress += 10;
                progressSos.setProgress(holdProgress);
                handler.postDelayed(this, 300);
            } else {
                triggerSos();
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);
        
        btnSosHold = findViewById(R.id.btnSosHold);
        progressSos = findViewById(R.id.progressSos);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvCountdown = findViewById(R.id.tvCountdown);
        btnCancelSos = findViewById(R.id.btnCancelSos);
        
        com.google.android.material.switchmaterial.SwitchMaterial switchVoiceMsg = findViewById(R.id.switchVoiceMsg);
        com.google.android.material.switchmaterial.SwitchMaterial switchMsgPreview = findViewById(R.id.switchMsgPreview);
        com.google.android.material.switchmaterial.SwitchMaterial switchBgProtect = findViewById(R.id.switchBgProtect);
        Button btnTestSosAlert = findViewById(R.id.btnTestSosAlert);
        
        switchVoiceMsg.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, "Voice Message: " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });
        
        switchMsgPreview.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, "Message Preview: " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });
        
        switchBgProtect.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, "Background Protection: " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });
        
        btnTestSosAlert.setOnClickListener(v -> {
            Toast.makeText(this, "Testing SOS Alert & Email Video...", Toast.LENGTH_LONG).show();
            com.example.shaketosave.api.AuthManager authManager = new com.example.shaketosave.api.AuthManager(this);
            Intent videoIntent = new Intent(this, VideoRecorderActivity.class);
            videoIntent.putExtra("user_name", authManager.getUserName());
            videoIntent.putExtra("emergency_phone", authManager.getUserPhone());
            startActivity(videoIntent);
        });
        
        btnSosHold.setOnTouchListener((v, event) -> {
            if (isSosTriggered) return false;
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    holdProgress = 0;
                    progressSos.setProgress(0);
                    handler.post(holdRunnable);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(holdRunnable);
                    if (holdProgress < 100) {
                        holdProgress = 0;
                        progressSos.setProgress(0);
                        Toast.makeText(this, "Hold longer to activate SOS", Toast.LENGTH_SHORT).show();
                    }
                    return true;
            }
            return false;
        });
        
        btnCancelSos.setOnClickListener(v -> {
            isSosTriggered = false;
            holdProgress = 0;
            progressSos.setProgress(0);
            tvInstruction.setText("Hold button or shake phone to trigger SOS");
            btnCancelSos.setVisibility(View.GONE);
            tvCountdown.setText("SOS Cancelled");
        });
        
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(count -> {
            if (!isSosTriggered) {
                if (mVibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        mVibrator.vibrate(100);
                    }
                }
                
                if (count >= 3) {
                    Toast.makeText(this, "Shake SOS Triggered!", Toast.LENGTH_SHORT).show();
                    if (mVibrator != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            mVibrator.vibrate(500);
                        }
                    }
                    triggerSos();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onPause() {
        mSensorManager.unregisterListener(mShakeDetector);
        super.onPause();
    }
    
    private void triggerSos() {
        isSosTriggered = true;
        tvInstruction.setText("SOS TRIGGERED! Alerting responders...");
        btnCancelSos.setVisibility(View.VISIBLE);
        tvCountdown.setText("10 seconds to cancel...");
        
        new Thread(() -> {
            for (int i = 10; i >= 0; i--) {
                if (!isSosTriggered) return;
                final int count = i;
                runOnUiThread(() -> {
                    if (count > 0) {
                        tvCountdown.setText(count + " seconds to cancel...");
                    } else {
                        tvCountdown.setText("🚨 SOS DISPATCHED! Help is on the way.");
                        btnCancelSos.setVisibility(View.GONE);
                        // Send broadcast directly to ShakeService to send SOS SMS and alert
                        Intent sosBroadcast = new Intent(ShakeService.ACTION_SEND_NOW);
                        sosBroadcast.setPackage(getPackageName());
                        sendBroadcast(sosBroadcast);
                    }
                });
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
        }).start();
    }
}
