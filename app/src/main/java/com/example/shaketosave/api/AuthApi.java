package com.example.shaketosave.api;

import com.example.shaketosave.api.models.AuthModels.*;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("api/auth/refresh")
    Call<TokenResponse> refresh(@Body RefreshRequest request);

    @POST("api/auth/logout")
    Call<Void> logout(@Body LogoutRequest request);

    @GET("api/auth/me")
    Call<UserResponse> getMe(@retrofit2.http.Header("Authorization") String authHeader);
}
