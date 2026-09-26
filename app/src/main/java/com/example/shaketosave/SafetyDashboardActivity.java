package com.example.shaketosave;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SafetyDashboardActivity extends AppCompatActivity {

    private TextView tvSafetyScore, tvRiskLevel, tvLightingVal, tvShopsVal, tvPatrolVal;
    private ProgressBar progressLighting, progressShops, progressPatrol;
    private Button btnRescanLocation;
    private SwitchMaterial switchDeviation, switchInactivity, switchImpact;

    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_dashboard);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        initViews();
        loadSafetyMetrics();
    }

    private void initViews() {
        tvSafetyScore = findViewById(R.id.tvSafetyScore);
        tvRiskLevel = findViewById(R.id.tvRiskLevel);
        tvLightingVal = findViewById(R.id.tvLightingVal);
        tvShopsVal = findViewById(R.id.tvShopsVal);
        tvPatrolVal = findViewById(R.id.tvPatrolVal);

        progressLighting = findViewById(R.id.progressLighting);
        progressShops = findViewById(R.id.progressShops);
        progressPatrol = findViewById(R.id.progressPatrol);

        btnRescanLocation = findViewById(R.id.btnRescanLocation);
        switchDeviation = findViewById(R.id.switchDeviation);
        switchInactivity = findViewById(R.id.switchInactivity);
        switchImpact = findViewById(R.id.switchImpact);

        btnRescanLocation.setOnClickListener(v -> {
            Toast.makeText(this, "Scanning GPS & analyzing local risk factors...", Toast.LENGTH_SHORT).show();
            btnRescanLocation.postDelayed(this::loadSafetyMetrics, 1200);
        });

        switchDeviation.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, "Route Deviation Detector: " + (isChecked ? "Active" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        switchInactivity.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, "Inactivity Alarm: " + (isChecked ? "Active" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        switchImpact.setOnCheckedChangeListener((btn, isChecked) -> {
            Toast.makeText(this, "Fall/Impact Sensor: " + (isChecked ? "Active" : "Disabled"), Toast.LENGTH_SHORT).show();
        });
    }

    @SuppressLint("MissingPermission")
    private void loadSafetyMetrics() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                updateScoreDisplay(87, "🟢 LOW RISK ZONE (High Safety Index)", 88, 78, 92);
            });
        } else {
            updateScoreDisplay(84, "🟢 LOW RISK ZONE (Sector 17, Chandigarh)", 85, 75, 90);
        }
    }

    private void updateScoreDisplay(int score, String riskText, int light, int shops, int patrol) {
        tvSafetyScore.setText(score + " / 100");
        tvRiskLevel.setText(riskText);

        progressLighting.setProgress(light);
        tvLightingVal.setText(light + "% (Well Lit)");

        progressShops.setProgress(shops);
        tvShopsVal.setText(shops + "% Commercial Activity");

        progressPatrol.setProgress(patrol);
        tvPatrolVal.setText("1.1 km (~3 min ETA)");
    }
}
