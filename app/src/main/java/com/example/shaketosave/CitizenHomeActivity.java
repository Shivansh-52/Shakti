package com.example.shaketosave;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class CitizenHomeActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_citizen_home);
        
        checkPermissions();
        
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        

        
        // Bind Main SOS Card
        findViewById(R.id.cardSOS).setOnClickListener(v -> {
            startActivity(new Intent(this, EmergencyActivity.class));
        });
        
        // 1. SOS & Check-in (Safe Walk)
        findViewById(R.id.cardSOSCheckIn).setOnClickListener(v -> {
            startActivity(new Intent(this, SafeWalkActivity.class));
        });
        
        // 2. Live Location & Route Tracking
        findViewById(R.id.cardLiveLocation).setOnClickListener(v -> {
            startActivity(new Intent(this, SafeRouteActivity.class));
        });

        // 3. Smart Analysis
        findViewById(R.id.cardSmartAnalysis).setOnClickListener(v -> {
            startActivity(new Intent(this, SafetyDashboardActivity.class));
        });

        // 4. Rapid Responders
        findViewById(R.id.cardRapidResponders).setOnClickListener(v -> {
            startActivity(new Intent(this, CommunityHelpActivity.class));
        });

        // 5. Alerts & Notifications
        findViewById(R.id.cardAlerts).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
        });
        findViewById(R.id.btnNotificationBell).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
        });

        // 6. Safe Places
        findViewById(R.id.cardSafePlaces).setOnClickListener(v -> {
            startActivity(new Intent(this, SafePlacesActivity.class));
        });

        // 7. Authority Escalation
        findViewById(R.id.cardAuthority).setOnClickListener(v -> {
            startActivity(new Intent(this, AuthorityEscalationActivity.class));
        });

        // 8. Incident Tracking
        findViewById(R.id.cardIncidentTracking).setOnClickListener(v -> {
            startActivity(new Intent(this, ReportIncidentActivity.class));
        });

        findViewById(R.id.cardProfileHeader).setOnClickListener(v -> {
            startActivity(new Intent(this, CitizenProfileActivity.class));
        });
        
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                return true;
            } else if (itemId == R.id.navigation_sos) {
                Intent intent = new Intent(this, EmergencyActivity.class);
                startActivity(intent);
                return false;
            } else if (itemId == R.id.navigation_routes) {
                Intent intent = new Intent(this, SafeRouteActivity.class);
                startActivity(intent);
                return false;
            } else if (itemId == R.id.navigation_community) {
                Intent intent = new Intent(this, CommunityHelpActivity.class);
                startActivity(intent);
                return false;
            } else if (itemId == R.id.navigation_profile) {
                Intent intent = new Intent(this, CitizenProfileActivity.class);
                startActivity(intent);
                return false;
            }
            return false;
        });

        com.example.shaketosave.api.AuthManager authManager = new com.example.shaketosave.api.AuthManager(this);
        String userName = authManager.getUserName();
        android.widget.TextView tvWelcomeName = findViewById(R.id.tvWelcomeName);
        if (tvWelcomeName != null && userName != null && !userName.isEmpty()) {
            tvWelcomeName.setText("Hi, " + userName + "!");
        }
        
        String token = authManager.getAccessToken();
        if (token != null && !token.isEmpty()) {
            com.example.shaketosave.api.AuthApi authApi = com.example.shaketosave.api.ApiClient.getClient().create(com.example.shaketosave.api.AuthApi.class);
            authApi.getMe("Bearer " + token).enqueue(new retrofit2.Callback<com.example.shaketosave.api.models.AuthModels.UserResponse>() {
                @Override
                public void onResponse(retrofit2.Call<com.example.shaketosave.api.models.AuthModels.UserResponse> call, retrofit2.Response<com.example.shaketosave.api.models.AuthModels.UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String realName = response.body().name;
                        String realEmail = response.body().email;
                        String realPhone = response.body().phone;
                        authManager.saveUserDetails(realName, realEmail, realPhone, "");
                        if (realEmail != null && !realEmail.isEmpty()) {
                            getSharedPreferences("SafeShakePrefs", MODE_PRIVATE).edit()
                                    .putString("emergency_email", realEmail)
                                    .apply();
                        }
                        if (tvWelcomeName != null && realName != null) {
                            tvWelcomeName.setText("Hi, " + realName + "!");
                        }
                    }
                }
                @Override
                public void onFailure(retrofit2.Call<com.example.shaketosave.api.models.AuthModels.UserResponse> call, Throwable t) {}
            });
        }
        // Initialize Background Services (Shake & Voice SOS)
        getSharedPreferences("SafeShakePrefs", MODE_PRIVATE).edit().putBoolean("service_enabled", true).apply();
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, ShakeService.class));
                startForegroundService(new Intent(this, VoiceRecognitionService.class));
            } else {
                startService(new Intent(this, ShakeService.class));
                startService(new Intent(this, VoiceRecognitionService.class));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.navigation_home);
        }
    }

    private void checkPermissions() {
        java.util.List<String> permissionsNeeded = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 100);
        }
    }
}
