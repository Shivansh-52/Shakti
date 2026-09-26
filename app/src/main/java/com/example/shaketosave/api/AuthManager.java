package com.example.shaketosave.api;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class AuthManager {
    private static final String PREF_NAME = "secure_auth_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private SharedPreferences sharedPreferences;

    public AuthManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public void saveTokens(String accessToken, String refreshToken) {
        sharedPreferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public String getAccessToken() {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null);
    }

    public void saveUserDetails(String name, String email, String phone, String aadhaar) {
        sharedPreferences.edit()
                .putString("user_name", name)
                .putString("user_email", email)
                .putString("user_phone", phone)
                .putString("user_aadhaar", aadhaar)
                .apply();
    }

    public void saveUserDetails(String name, String phone, String aadhaar) {
        sharedPreferences.edit()
                .putString("user_name", name)
                .putString("user_phone", phone)
                .putString("user_aadhaar", aadhaar)
                .apply();
    }

    public void saveUserEmail(String email) {
        sharedPreferences.edit().putString("user_email", email).apply();
    }

    public String getUserEmail() {
        return sharedPreferences.getString("user_email", "");
    }

    public String getUserName() {
        return sharedPreferences.getString("user_name", "Citizen");
    }

    public String getUserPhone() {
        return sharedPreferences.getString("user_phone", "");
    }

    public String getUserAadhaar() {
        return sharedPreferences.getString("user_aadhaar", "");
    }

    public void saveSosMessage(String message) {
        sharedPreferences.edit().putString("sos_message", message).apply();
    }

    public String getSosMessage() {
        return sharedPreferences.getString("sos_message", "I am in danger and need immediate help! This is an automated SOS message from Suraksha Setu.");
    }

    public void clearSession() {
        sharedPreferences.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return getAccessToken() != null;
    }
}
