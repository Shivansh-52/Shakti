package com.example.shaketosave;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shaketosave.api.AuthManager;

public class SplashActivity extends AppCompatActivity {
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        authManager = new AuthManager(this);
        
        LinearLayout logoContainer = findViewById(R.id.logoContainer);
        
        // Blinkit-style bounce/pulse animation
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(logoContainer, "scaleX", 1f, 1.15f, 1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(logoContainer, "scaleY", 1f, 1.15f, 1f);
        scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        scaleX.setDuration(800);
        scaleY.setDuration(800);
        
        android.animation.AnimatorSet animatorSet = new android.animation.AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.start();

        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, RoleSelectionActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 2500);
    }
}
