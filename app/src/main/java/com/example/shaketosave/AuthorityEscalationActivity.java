package com.example.shaketosave;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AuthorityEscalationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authority_escalation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        Button btnEscalate112 = findViewById(R.id.btnEscalate112);
        Button btnCall1091 = findViewById(R.id.btnCall1091);
        Button btnCall100 = findViewById(R.id.btnCall100);
        Button btnCall108 = findViewById(R.id.btnCall108);

        if (btnEscalate112 != null) btnEscalate112.setOnClickListener(v -> dialNumber("112"));
        if (btnCall1091 != null) btnCall1091.setOnClickListener(v -> dialNumber("1091"));
        if (btnCall100 != null) btnCall100.setOnClickListener(v -> dialNumber("100"));
        if (btnCall108 != null) btnCall108.setOnClickListener(v -> dialNumber("108"));
    }

    private void dialNumber(String number) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + number));
        startActivity(intent);
    }
}
