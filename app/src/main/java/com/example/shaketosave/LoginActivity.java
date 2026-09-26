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
import com.example.shaketosave.api.AuthManager;
import com.example.shaketosave.api.models.AuthModels;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etPassword;
    private Button btnLogin, btnCreateAccount, btnFingerprintLogin;
    private ProgressBar progressBar;
    private AuthManager authManager;
    private AuthApi authApi;
    
    private String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new AuthManager(this);
        authApi = ApiClient.getClient().create(AuthApi.class);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        btnFingerprintLogin = findViewById(R.id.btnFingerprintLogin);
        progressBar = findViewById(R.id.progressBar);
        
        role = getIntent().getStringExtra("ROLE");
        if ("CITIZEN".equals(role)) {
            btnFingerprintLogin.setVisibility(View.VISIBLE);
        }

        btnFingerprintLogin.setOnClickListener(v -> {
            androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Fingerprint Authentication")
                    .setSubtitle("Use your fingerprint to log in quickly")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            androidx.biometric.BiometricPrompt biometricPrompt = new androidx.biometric.BiometricPrompt(LoginActivity.this,
                    androidx.core.content.ContextCompat.getMainExecutor(LoginActivity.this),
                    new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            Toast.makeText(LoginActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Toast.makeText(LoginActivity.this, "Fingerprint Verified! Logging in...", Toast.LENGTH_SHORT).show();
                            // Bypass form and login directly
                            startActivity(new Intent(LoginActivity.this, CitizenHomeActivity.class));
                            finish();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Toast.makeText(LoginActivity.this, "Fingerprint not recognized.", Toast.LENGTH_SHORT).show();
                        }
                    });

            biometricPrompt.authenticate(promptInfo);
        });

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnCreateAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    // Removed onActivityResult for camera
    
    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        AuthModels.LoginRequest loginRequest = new AuthModels.LoginRequest(email, password);
        authApi.login(loginRequest).enqueue(new Callback<AuthModels.AuthResponse>() {
            @Override
            public void onResponse(Call<AuthModels.AuthResponse> call, Response<AuthModels.AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    AuthModels.AuthResponse authRes = response.body();
                    authManager.saveTokens(authRes.access_token, authRes.refresh_token);
                    
                    if (authRes.user != null) {
                        String userEmail = authRes.user.email != null && !authRes.user.email.isEmpty() ? authRes.user.email : email;
                        authManager.saveUserDetails(authRes.user.name, userEmail, authRes.user.phone, "");
                        authManager.saveUserEmail(userEmail);
                        getSharedPreferences("SafeShakePrefs", MODE_PRIVATE).edit()
                                .putString("emergency_email", userEmail)
                                .putString("recipient_email", userEmail)
                                .putString("user_name", authRes.user.name)
                                .apply();
                    } else {
                        authManager.saveUserEmail(email);
                        getSharedPreferences("SafeShakePrefs", MODE_PRIVATE).edit()
                                .putString("emergency_email", email)
                                .putString("recipient_email", email)
                                .apply();
                    }
                    
                    if ("CITIZEN".equals(role)) {
                        startActivity(new Intent(LoginActivity.this, CitizenHomeActivity.class));
                    } else {
                        startActivity(new Intent(LoginActivity.this, VolunteerHomeActivity.class));
                    }
                    finish();
                } else {
                    String errorMsg = "Invalid credentials";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg = response.errorBody().string();
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<AuthModels.AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}
