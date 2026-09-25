package com.example.shaketosave;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SafePlacesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_places);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        Button btnNavPolice = findViewById(R.id.btnNavPolice);
        Button btnCallPolice = findViewById(R.id.btnCallPolice);
        Button btnNavPharmacy = findViewById(R.id.btnNavPharmacy);
        Button btnCallPharmacy = findViewById(R.id.btnCallPharmacy);
        Button btnNavHospital = findViewById(R.id.btnNavHospital);
        Button btnCallHospital = findViewById(R.id.btnCallHospital);

        if (btnNavPolice != null) {
            btnNavPolice.setOnClickListener(v -> openNavigation("Sector 17 Police Station, Chandigarh", 30.7380, 76.7820));
        }
        if (btnCallPolice != null) {
            btnCallPolice.setOnClickListener(v -> dialHelpline("112"));
        }
        if (btnNavPharmacy != null) {
            btnNavPharmacy.setOnClickListener(v -> openNavigation("Apollo Pharmacy Sector 17, Chandigarh", 30.7350, 76.7790));
        }
        if (btnCallPharmacy != null) {
            btnCallPharmacy.setOnClickListener(v -> dialHelpline("01722740001"));
        }
        if (btnNavHospital != null) {
            btnNavHospital.setOnClickListener(v -> openNavigation("Max Hospital, Phase 6", 30.7250, 76.7650));
        }
        if (btnCallHospital != null) {
            btnCallHospital.setOnClickListener(v -> dialHelpline("108"));
        }
    }

    private void openNavigation(String name, double lat, double lng) {
        Toast.makeText(this, "Navigating to " + name, Toast.LENGTH_SHORT).show();
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + lat + "," + lng + "&mode=w");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Uri fallbackUri = Uri.parse("https://maps.google.com/?q=" + lat + "," + lng);
            startActivity(new Intent(Intent.ACTION_VIEW, fallbackUri));
        }
    }

    private void dialHelpline(String phone) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phone));
        startActivity(intent);
    }
}
