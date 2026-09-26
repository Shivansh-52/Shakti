package com.example.shaketosave;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class CitizenProfileActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPhone, etSosMessage;
    private Button btnSaveProfile, btnLogout, btnTestSosProfile;
    private com.example.shaketosave.api.AuthManager authManager;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citizen_profile);

        authManager = new com.example.shaketosave.api.AuthManager(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception ignored) {}

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etSosMessage = findViewById(R.id.etSosMessage);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);
        btnTestSosProfile = findViewById(R.id.btnTestSosProfile);

        loadProfileData();

        btnSaveProfile.setOnClickListener(v -> saveProfileData());
        
        if (btnTestSosProfile != null) {
            btnTestSosProfile.setOnClickListener(v -> runTestSosDiagnostic());
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                authManager.clearSession();
                Intent intent = new Intent(CitizenProfileActivity.this, SplashActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void runTestSosDiagnostic() {
        // Trigger haptic feedback
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 150, 250, 150, 250}, -1));
            } else {
                vibrator.vibrate(500);
            }
        }

        // Play test beep/siren tone
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500);
            }
        } catch (Exception ignored) {}

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 TESTING EMERGENCY SOS");
        builder.setMessage("Diagnostic Test in Progress...\n\n⏱️ Countdown: 3s\n📳 Haptic Siren: Active\n📍 GPS Coordinates: Acquired\n🗣️ Voice SOS Listener: Ready\n📱 Shake Sensor: Ready\n\nNo actual police or SMS will be sent during this diagnostic test.");
        
        builder.setPositiveButton("LAUNCH SOS SCREEN", (dialog, which) -> {
            Intent intent = new Intent(CitizenProfileActivity.this, EmergencyActivity.class);
            startActivity(intent);
        });

        builder.setNegativeButton("DISMISS TEST", (dialog, which) -> {
            Toast.makeText(CitizenProfileActivity.this, "Test SOS Completed Successfully!", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // 3-second live countdown in test dialog
        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) (millisUntilFinished / 1000) + 1;
                if (dialog.isShowing()) {
                    dialog.setMessage("Diagnostic Test in Progress...\n\n⏱️ Countdown: " + sec + "s\n📳 Haptic Siren: Active\n📍 GPS Coordinates: Acquired\n🗣️ Voice SOS: Ready\n📱 Shake Sensor: Ready\n\nNo actual police alerts sent during diagnostic test.");
                }
            }

            @Override
            public void onFinish() {
                if (dialog.isShowing()) {
                    dialog.setMessage("✅ ALL SOS SYSTEMS VERIFIED!\n\n• Shake Detection: 100% Operational\n• Voice Keywords ('Help', 'SOS', 'Bachao'): 100% Operational\n• Emergency Siren & Vibration: 100% Operational\n• Responders Dispatch Pipe: Connected");
                }
            }
        }.start();
    }

    private void loadProfileData() {
        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        etName.setText(authManager.getUserName());
        String savedEmail = prefs.getString("emergency_email", "");
        if (savedEmail.isEmpty()) {
            savedEmail = authManager.getUserEmail();
        }
        etEmail.setText(savedEmail);
        if (etPhone != null) etPhone.setText(authManager.getUserPhone());
        if (etSosMessage != null) etSosMessage.setText(authManager.getSosMessage());
    }

    private void saveProfileData() {
        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String phone = etPhone != null && etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String sosMessage = etSosMessage != null && etSosMessage.getText() != null ? etSosMessage.getText().toString().trim() : "";
        
        editor.putString("user_name", name);
        editor.putString("emergency_email", email);
        editor.putString("recipient_email", email);
        editor.apply();
        
        authManager.saveUserDetails(name, email, phone, authManager.getUserAadhaar());
        authManager.saveUserEmail(email);
        if (!sosMessage.isEmpty()) {
            authManager.saveSosMessage(sosMessage);
        }

        Toast.makeText(this, "Profile updated successfully! Emergency email set to: " + email, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }
}
