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

public class VolunteerHomeActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_home);
        
        checkPermissions();
        
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
        });
        
        // Handle incoming SOS buttons
        android.view.View cardIncomingSos = findViewById(R.id.cardIncomingSos);
        findViewById(R.id.btnAcceptSos).setOnClickListener(v -> {
            Toast.makeText(this, "SOS Accepted! Navigating to Citizen...", Toast.LENGTH_LONG).show();
            cardIncomingSos.setVisibility(android.view.View.GONE);
            startActivity(new Intent(this, SafeRouteActivity.class));
        });
        
        findViewById(R.id.btnDeclineSos).setOnClickListener(v -> {
            cardIncomingSos.setVisibility(android.view.View.GONE);
            Toast.makeText(this, "Alert passed to next nearest volunteer.", Toast.LENGTH_SHORT).show();
        });
        
        // Bind the new modern dashboard cards
        findViewById(R.id.cardSOS).setOnClickListener(v -> {
            startActivity(new Intent(this, EmergencyActivity.class));
        });
        
        findViewById(R.id.cardSafeRoutes).setOnClickListener(v -> {
            startActivity(new Intent(this, SafeRouteActivity.class));
        });
        
        findViewById(R.id.cardSafeWalk).setOnClickListener(v -> {
            startActivity(new Intent(this, SafeWalkActivity.class));
        });
        
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_sos) {
                Intent intent = new Intent(this, EmergencyActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.navigation_routes) {
                Intent intent = new Intent(this, SafeRouteActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.navigation_community) {
                Intent intent = new Intent(this, CommunityHelpActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.navigation_profile) {
                Intent intent = new Intent(this, CitizenProfileActivity.class);
                startActivity(intent);
                return true;
            }
            return true;
        });

        // Initialize Background Services if enabled by user in settings (MainActivity)
        boolean serviceEnabled = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE)
                .getBoolean("service_enabled", false);
        if (serviceEnabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, ShakeService.class));
                startForegroundService(new Intent(this, VoiceRecognitionService.class));
            } else {
                startService(new Intent(this, ShakeService.class));
                startService(new Intent(this, VoiceRecognitionService.class));
            }
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
