package com.example.shaketosave;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;

public class EmergencyContactsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SafeShakePrefs";
    private static final String KEY_PHONE1 = "emergency_phone1";
    private static final String KEY_PHONE2 = "emergency_phone2";
    private static final String KEY_PHONE3 = "emergency_phone3";
    private static final String KEY_NAME1 = "contact1_name";
    private static final String KEY_NAME2 = "contact2_name";
    private static final String KEY_NAME3 = "contact3_name";

    private TextInputEditText etContact1Name, etContact1Phone;
    private TextInputEditText etContact2Name, etContact2Phone;
    private TextInputEditText etContact3Name, etContact3Phone;
    private Button btnSaveContacts, btnTestLiveTracking;

    private SharedPreferences prefs;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        initViews();
        loadSavedContacts();
    }

    private void initViews() {
        etContact1Name = findViewById(R.id.etContact1Name);
        etContact1Phone = findViewById(R.id.etContact1Phone);
        etContact2Name = findViewById(R.id.etContact2Name);
        etContact2Phone = findViewById(R.id.etContact2Phone);
        etContact3Name = findViewById(R.id.etContact3Name);
        etContact3Phone = findViewById(R.id.etContact3Phone);
        btnSaveContacts = findViewById(R.id.btnSaveContacts);
        btnTestLiveTracking = findViewById(R.id.btnTestLiveTracking);

        btnSaveContacts.setOnClickListener(v -> saveContacts());
        btnTestLiveTracking.setOnClickListener(v -> testShareLiveTracking());
    }

    private void loadSavedContacts() {
        etContact1Phone.setText(prefs.getString(KEY_PHONE1, ""));
        etContact1Name.setText(prefs.getString(KEY_NAME1, "Mom / Family"));

        etContact2Phone.setText(prefs.getString(KEY_PHONE2, ""));
        etContact2Name.setText(prefs.getString(KEY_NAME2, "Close Friend"));

        etContact3Phone.setText(prefs.getString(KEY_PHONE3, ""));
        etContact3Name.setText(prefs.getString(KEY_NAME3, "Emergency Contact"));
    }

    private void saveContacts() {
        String p1 = etContact1Phone.getText() != null ? etContact1Phone.getText().toString().trim() : "";
        String n1 = etContact1Name.getText() != null ? etContact1Name.getText().toString().trim() : "";

        String p2 = etContact2Phone.getText() != null ? etContact2Phone.getText().toString().trim() : "";
        String n2 = etContact2Name.getText() != null ? etContact2Name.getText().toString().trim() : "";

        String p3 = etContact3Phone.getText() != null ? etContact3Phone.getText().toString().trim() : "";
        String n3 = etContact3Name.getText() != null ? etContact3Name.getText().toString().trim() : "";

        prefs.edit()
                .putString(KEY_PHONE1, p1)
                .putString(KEY_NAME1, n1)
                .putString(KEY_PHONE2, p2)
                .putString(KEY_NAME2, n2)
                .putString(KEY_PHONE3, p3)
                .putString(KEY_NAME3, n3)
                .apply();

        Toast.makeText(this, "Trusted contacts updated successfully!", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("MissingPermission")
    private void testShareLiveTracking() {
        String p1 = prefs.getString(KEY_PHONE1, "");
        if (p1.isEmpty() && etContact1Phone.getText() != null) {
            p1 = etContact1Phone.getText().toString().trim();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }

        final String targetPhone = p1;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            double lat = location != null ? location.getLatitude() : 30.7333;
            double lng = location != null ? location.getLongitude() : 76.7794;
            String liveTrackingUrl = "https://maps.google.com/?q=" + lat + "," + lng;

            String message = "🛡️ [Shakti Safe Walk Alert]: I am sharing my live safety tracking with you. Follow my live GPS location here: " + liveTrackingUrl;

            // Intent share sheet so user can choose WhatsApp, SMS, or Telegram
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Live Safety Location Tracking");
            shareIntent.putExtra(Intent.EXTRA_TEXT, message);
            startActivity(Intent.createChooser(shareIntent, "Share Live Tracking with Trusted Person"));
        });
    }
}
