package com.vchat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.vchat.app.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Splash screen delay of 2 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            checkUserAndNavigate();
        }, 2000);
    }

    private void checkUserAndNavigate() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // User is signed in, navigate to Main
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        } else {
            // No user signed in, navigate to Login
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        }
        finish();
    }
}
