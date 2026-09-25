package com.example.shaketosave;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class CommunityHelpActivity extends AppCompatActivity {

    private Button btnBroadcastAlert, btnConnectRohan, btnConnectAnanya, btnConnectPatrol;
    private TextView tvResponderStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community_help);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        btnBroadcastAlert = findViewById(R.id.btnBroadcastAlert);
        tvResponderStatus = findViewById(R.id.tvResponderStatus);
        btnConnectRohan = findViewById(R.id.btnConnectRohan);
        btnConnectAnanya = findViewById(R.id.btnConnectAnanya);
        btnConnectPatrol = findViewById(R.id.btnConnectPatrol);

        btnBroadcastAlert.setOnClickListener(v -> broadcastEmergencyAlert());
        btnConnectRohan.setOnClickListener(v -> requestSpecificResponder("Rohan Sharma", "280m away"));
        btnConnectAnanya.setOnClickListener(v -> requestSpecificResponder("Ananya Verma", "450m away"));
        btnConnectPatrol.setOnClickListener(v -> requestSpecificResponder("Night Community Patrol", "620m away"));
    }

    private void broadcastEmergencyAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Broadcasting to Nearby Responders");
        builder.setMessage("Broadcasting your encrypted location to 6 verified volunteers & night patrol units within 1km...");
        builder.setPositiveButton("Cancel Broadcast", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.setTitle("Responder Accepted!");
                dialog.setMessage("✅ Verified Volunteer 'Rohan Sharma' (280m away) has accepted your alert and is rushing to your location!\n\nETA: ~2 Minutes");
            }
        }, 2500);
    }

    private void requestSpecificResponder(String name, String distance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Request Companion: " + name);
        builder.setMessage("Send request to " + name + " (" + distance + ") to accompany you or provide safety assistance?");
        builder.setPositiveButton("Send Request", (dialog, which) -> {
            Toast.makeText(this, "Request sent to " + name + "!", Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Request Accepted!")
                        .setMessage(name + " has accepted your request. They are on their way to meet you.")
                        .setPositiveButton("Open Safe Route", (d, w) -> {
                            startActivity(new Intent(this, SafeRouteActivity.class));
                        })
                        .setNegativeButton("OK", null)
                        .show();
            }, 2000);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
