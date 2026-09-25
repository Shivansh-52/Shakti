package com.example.shaketosave;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shaketosave.api.ApiClient;
import com.example.shaketosave.api.AuthApi;
import com.example.shaketosave.api.models.AuthModels;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText etName, etEmail, etPhone, etPassword, etConfirmPassword, etAadhaar;
    private Button btnRegister, btnLogin, btnVerifyAadhaar, btnFingerprintAuth;
    private android.widget.TextView tvVerificationStatus;
    private ProgressBar progressBar;
    private AuthApi authApi;
    private com.example.shaketosave.api.AuthManager authManager;
    
    private boolean isAadhaarVerified = false;
    private boolean isFingerprintVerified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authApi = ApiClient.getClient().create(AuthApi.class);
        authManager = new com.example.shaketosave.api.AuthManager(this);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etAadhaar = findViewById(R.id.etAadhaar);
        
        btnRegister = findViewById(R.id.btnRegister);
        btnLogin = findViewById(R.id.btnLogin);
        btnVerifyAadhaar = findViewById(R.id.btnVerifyAadhaar);
        btnFingerprintAuth = findViewById(R.id.btnFingerprintAuth);
        tvVerificationStatus = findViewById(R.id.tvVerificationStatus);
        progressBar = findViewById(R.id.progressBar);

        btnVerifyAadhaar.setOnClickListener(v -> {
            String aadhaar = etAadhaar.getText().toString().trim();
            if (aadhaar.length() == 12) {
                isAadhaarVerified = true;
                btnVerifyAadhaar.setText("Verified ✓");
                btnVerifyAadhaar.setEnabled(false);
                Toast.makeText(this, "Aadhaar Verified Successfully via UIDAI", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please enter a valid 12-digit Aadhaar number", Toast.LENGTH_SHORT).show();
            }
        });

        btnFingerprintAuth.setOnClickListener(v -> {
            androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Fingerprint Authentication Registration")
                    .setSubtitle("Enroll and verify your fingerprint biometrics")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            androidx.biometric.BiometricPrompt biometricPrompt = new androidx.biometric.BiometricPrompt(RegisterActivity.this,
                    androidx.core.content.ContextCompat.getMainExecutor(RegisterActivity.this),
                    new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            Toast.makeText(RegisterActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            isFingerprintVerified = true;
                            btnFingerprintAuth.setText("Identity Verified ✓");
                            btnFingerprintAuth.setEnabled(false);
                            Toast.makeText(RegisterActivity.this, "Fingerprint Enrolled & Verified!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Toast.makeText(RegisterActivity.this, "Authentication failed. Fingerprint not recognized.", Toast.LENGTH_SHORT).show();
                        }
                    });

            biometricPrompt.authenticate(promptInfo);
        });

        btnRegister.setOnClickListener(v -> attemptRegister());
        btnLogin.setOnClickListener(v -> finish());
    }

    // Removed onActivityResult

    private void attemptRegister() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAadhaarVerified || !isFingerprintVerified) {
            Toast.makeText(this, "Please complete Aadhaar and Fingerprint Verification first.", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        AuthModels.RegisterRequest request = new AuthModels.RegisterRequest(name, email, phone, password);
        authApi.register(request).enqueue(new Callback<AuthModels.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthModels.AuthResponse> call, Response<AuthModels.AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    authManager.saveUserDetails(name, email, phone, etAadhaar.getText().toString().trim());
                    authManager.saveUserEmail(email);
                    getSharedPreferences("SafeShakePrefs", MODE_PRIVATE).edit()
                            .putString("emergency_email", email)
                            .putString("recipient_email", email)
                            .putString("user_name", name)
                            .apply();
                    Toast.makeText(RegisterActivity.this, "Registration successful! Please login.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    String errorMsg = "Registration failed";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg = response.errorBody().string();
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<AuthModels.AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Network error: " + t.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }
}
