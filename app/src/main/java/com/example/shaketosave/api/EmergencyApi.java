package com.example.shaketosave.api;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface EmergencyApi {

    @Multipart
    @POST("api/emergency/send-video-email")
    Call<ResponseBody> sendVideoEmail(
            @Part("recipient_email") RequestBody recipientEmail,
            @Part("user_name") RequestBody userName,
            @Part("user_phone") RequestBody userPhone,
            @Part("maps_link") RequestBody mapsLink,
            @Part("message") RequestBody message,
            @Part MultipartBody.Part videoFile
    );
}
