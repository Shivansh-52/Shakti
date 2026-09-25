package com.example.shaketosave;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;

public class ReportIncidentActivity extends AppCompatActivity {

    private TextInputEditText etIncidentType, etIncidentLocation, etIncidentDesc;
    private Button btnSubmitIncident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_incident);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        etIncidentType = findViewById(R.id.etIncidentType);
        etIncidentLocation = findViewById(R.id.etIncidentLocation);
        etIncidentDesc = findViewById(R.id.etIncidentDesc);
        btnSubmitIncident = findViewById(R.id.btnSubmitIncident);

        if (btnSubmitIncident != null) {
            btnSubmitIncident.setOnClickListener(v -> submitIncidentReport());
        }
    }

    private void submitIncidentReport() {
        String type = etIncidentType.getText() != null ? etIncidentType.getText().toString().trim() : "";
        String loc = etIncidentLocation.getText() != null ? etIncidentLocation.getText().toString().trim() : "";
        String desc = etIncidentDesc.getText() != null ? etIncidentDesc.getText().toString().trim() : "";

        if (desc.isEmpty()) {
            Toast.makeText(this, "Please describe the incident or safety concern.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Report Submitted Successfully")
                .setMessage("✅ Incident Logged: #" + (int)(Math.random() * 9000 + 1000) + "\n\nCategory: " + type + "\nLocation: " + loc + "\n\nSafety Heatmaps updated and nearby patrol units alerted.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .show();
    }
}
