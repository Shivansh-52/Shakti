package com.example.shaketosave;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class NotificationsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        Button btnTestAlert = findViewById(R.id.btnTestAlert);
        Button btnClearAlerts = findViewById(R.id.btnClearAlerts);

        if (btnTestAlert != null) {
            btnTestAlert.setOnClickListener(v -> {
                Toast.makeText(this, "🔔 Test Broadcast Alert Received: All Safety Systems Operating Normally.", Toast.LENGTH_LONG).show();
            });
        }

        if (btnClearAlerts != null) {
            btnClearAlerts.setOnClickListener(v -> {
                Toast.makeText(this, "All notifications marked as read.", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
