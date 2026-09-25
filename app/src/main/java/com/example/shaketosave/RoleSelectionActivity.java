package com.example.shaketosave;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class RoleSelectionActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);
        
        Button btnCitizen = findViewById(R.id.btnCitizen);
        Button btnVolunteer = findViewById(R.id.btnVolunteer);
        
        btnCitizen.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("ROLE", "CITIZEN");
            startActivity(intent);
        });
        
        btnVolunteer.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("ROLE", "VOLUNTEER");
            startActivity(intent);
        });
    }
}
