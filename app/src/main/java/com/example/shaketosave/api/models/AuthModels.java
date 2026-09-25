package com.example.shaketosave.api.models;

public class AuthModels {
    public static class RegisterRequest {
        public String name;
        public String email;
        public String phone;
        public String password;
        
        public RegisterRequest(String name, String email, String phone, String password) {
            this.name = name; this.email = email; this.phone = phone; this.password = password;
        }
    }

    public static class LoginRequest {
        public String email;
        public String password;
        
        public LoginRequest(String email, String password) {
            this.email = email; this.password = password;
        }
    }

    public static class UserResponse {
        public String user_id;
        public String name;
        public String email;
        public String phone;
        public String role;
        public boolean is_active;
        public boolean is_verified;
    }

    public static class AuthResponse {
        public UserResponse user;
        public String access_token;
        public String refresh_token;
    }

    public static class RefreshRequest {
        public String refresh_token;
        public RefreshRequest(String refresh_token) { this.refresh_token = refresh_token; }
    }

    public static class LogoutRequest {
        public String refresh_token;
        public LogoutRequest(String refresh_token) { this.refresh_token = refresh_token; }
    }

    public static class TokenResponse {
        public String access_token;
        public String refresh_token;
    }
}
