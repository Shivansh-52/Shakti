package com.example.shaketosave.api;

import com.example.shaketosave.api.models.SafeRouteModels;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SafeRouteApi {

    @POST("api/safe-route/score-route")
    Call<SafeRouteModels.RouteResponse> scoreRoute(
            @Body SafeRouteModels.RouteRequest request
    );

    @POST("api/safe-route/score-point")
    Call<SafeRouteModels.SegmentSafety> scorePoint(
            @Body SafeRouteModels.PointRequest request,
            @Query("is_night") boolean isNight
    );
}
