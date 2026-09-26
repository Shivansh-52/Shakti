package com.example.shaketosave.api.models;

import java.util.List;

public class SafeRouteModels {

    // ---------- Request ----------
    public static class LatLng {
        public double lat;
        public double lng;

        public LatLng(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static class RouteRequest {
        public LatLng origin;
        public LatLng destination;
        public boolean is_night;

        public RouteRequest(LatLng origin, LatLng destination, boolean is_night) {
            this.origin = origin;
            this.destination = destination;
            this.is_night = is_night;
        }
    }

    public static class PointRequest {
        public double lat;
        public double lng;

        public PointRequest(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }

    // ---------- Response ----------
    public static class SegmentSafety {
        public double lat;
        public double lng;
        public double safety_score;
        public String safety_label;
        public String safety_color;
    }

    public static class RouteResponse {
        public double overall_safety_score;
        public String overall_safety_label;
        public double distance_km;
        public double duration_min;
        /** Each element is [lat, lng] */
        public List<List<Double>> polyline;
        public List<SegmentSafety> segments;
        public List<String> tips;
    }
}
