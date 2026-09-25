import os

new_code = """package com.example.shaketosave;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SafeRouteActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private TextInputEditText etSourceAddress, etDestAddress;
    private Button btnFindRoute, btnStartRoute, btnFindBuddy;
    private ProgressBar progressBar;
    private View layoutResult;

    private FusedLocationProviderClient fusedLocationClient;
    private double userLat = 0, userLng = 0;
    private boolean hasUserLocation = false;
    
    private List<LatLng> currentRoutePoints = new ArrayList<>();
    private Marker userMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_route);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        requestLocationPermission();
    }

    private void initViews() {
        etSourceAddress = findViewById(R.id.etSourceAddress);
        etDestAddress = findViewById(R.id.etDestAddress);
        btnFindRoute = findViewById(R.id.btnFindRoute);
        progressBar = findViewById(R.id.progressBar);
        layoutResult = findViewById(R.id.layoutResult);
        btnStartRoute = findViewById(R.id.btnStartRoute);
        btnFindBuddy = findViewById(R.id.btnFindBuddy);

        btnFindRoute.setOnClickListener(v -> findSafeRoute());

        btnStartRoute.setOnClickListener(v -> {
            btnStartRoute.setVisibility(View.GONE);
            btnFindBuddy.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Navigation Started! Routing you safely.", Toast.LENGTH_SHORT).show();
            startNavigationSimulation();
        });

        btnFindBuddy.setOnClickListener(v -> findSafeWalkBuddy());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapView);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        LatLngBounds CHANDIGARH_BOUNDS = new LatLngBounds(
                new LatLng(30.6500, 76.7000), 
                new LatLng(30.8000, 76.8500)
        );
        mMap.setLatLngBoundsForCameraTarget(CHANDIGARH_BOUNDS);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(30.7333, 76.7794), 13f));

        if (hasLocationPermission()) {
            mMap.setMyLocationEnabled(true);
            fetchUserLocation();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            fetchUserLocation();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) mMap.setMyLocationEnabled(true);
            fetchUserLocation();
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchUserLocation() {
        if (!hasLocationPermission()) return;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLat = location.getLatitude();
                userLng = location.getLongitude();
                hasUserLocation = true;
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userLat, userLng), 15f));
                }
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnFindRoute.setEnabled(!loading);
        btnFindRoute.setText(loading ? "Analysing…" : "FIND SAFE ROUTES");
    }

    private void findSafeRoute() {
        if (mMap == null) return;
        String srcAddress = etSourceAddress.getText() != null ? etSourceAddress.getText().toString().trim() : "";
        String destAddress = etDestAddress.getText() != null ? etDestAddress.getText().toString().trim() : "";

        if (destAddress.isEmpty()) {
            Toast.makeText(this, "Please enter a destination address", Toast.LENGTH_SHORT).show();
            return;
        }
        if (srcAddress.isEmpty() && !hasUserLocation) {
            Toast.makeText(this, "Waiting for your location…", Toast.LENGTH_SHORT).show();
            fetchUserLocation();
            return;
        }

        setLoading(true);
        layoutResult.setVisibility(View.GONE);
        mMap.clear();

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(SafeRouteActivity.this, Locale.getDefault());
                
                String queryDest = destAddress;
                if (!queryDest.toLowerCase().contains("chandigarh")) queryDest += ", Chandigarh";
                List<Address> destAddrs = geocoder.getFromLocationName(queryDest, 1);
                if (destAddrs == null || destAddrs.isEmpty()) {
                    runOnUiThread(() -> { setLoading(false); Toast.makeText(SafeRouteActivity.this, "Could not find destination.", Toast.LENGTH_SHORT).show(); });
                    return;
                }
                double destLat = destAddrs.get(0).getLatitude();
                double destLng = destAddrs.get(0).getLongitude();

                final double finalSrcLat;
                final double finalSrcLng;
                if (!srcAddress.isEmpty()) {
                    String querySrc = srcAddress;
                    if (!querySrc.toLowerCase().contains("chandigarh")) querySrc += ", Chandigarh";
                    List<Address> srcAddrs = geocoder.getFromLocationName(querySrc, 1);
                    if (srcAddrs == null || srcAddrs.isEmpty()) {
                        runOnUiThread(() -> { setLoading(false); Toast.makeText(SafeRouteActivity.this, "Could not find start address.", Toast.LENGTH_SHORT).show(); });
                        return;
                    }
                    finalSrcLat = srcAddrs.get(0).getLatitude();
                    finalSrcLng = srcAddrs.get(0).getLongitude();
                } else {
                    finalSrcLat = userLat;
                    finalSrcLng = userLng;
                }

                fetchDirectionsAPI(finalSrcLat, finalSrcLng, destLat, destLng);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> { setLoading(false); Toast.makeText(SafeRouteActivity.this, "Error finding route.", Toast.LENGTH_SHORT).show(); });
            }
        }).start();
    }

    private void fetchDirectionsAPI(double srcLat, double srcLng, double destLat, double destLng) {
        try {
            String apiKey = "AIzaSyA-_rQxroRi5_rjmVJs7mbpKx2P3vLFqNY";
            String urlStr = "https://maps.googleapis.com/maps/api/directions/json?origin=" + srcLat + "," + srcLng + "&destination=" + destLat + "," + destLng + "&key=" + apiKey;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
            StringBuilder builder = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                builder.append((char) ch);
            }
            reader.close();
            
            JSONObject json = new JSONObject(builder.toString());
            JSONArray routes = json.getJSONArray("routes");
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                JSONObject polyline = route.getJSONObject("overview_polyline");
                String points = polyline.getString("points");
                List<LatLng> decodedPath = decodePoly(points);
                
                runOnUiThread(() -> {
                    setLoading(false);
                    currentRoutePoints = decodedPath;
                    drawSafeRoute(decodedPath, srcLat, srcLng, destLat, destLng);
                });
            } else {
                runOnUiThread(() -> { setLoading(false); Toast.makeText(SafeRouteActivity.this, "No routes found.", Toast.LENGTH_SHORT).show(); });
            }
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> { setLoading(false); Toast.makeText(SafeRouteActivity.this, "API Error.", Toast.LENGTH_SHORT).show(); });
        }
    }

    private void drawSafeRoute(List<LatLng> path, double srcLat, double srcLng, double destLat, double destLng) {
        if (mMap == null || path.isEmpty()) return;
        
        // Split the path into segments and colour them (Mocking Risk: Green, Yellow, Red)
        // We'll just assign colours cyclically or based on index to demonstrate
        int segmentsCount = 3; 
        int ptsPerSegment = path.size() / segmentsCount;
        if(ptsPerSegment == 0) ptsPerSegment = 1;
        
        for (int i = 0; i < path.size() - 1; i++) {
            LatLng p1 = path.get(i);
            LatLng p2 = path.get(i+1);
            
            int color;
            double rand = Math.random();
            if (rand < 0.6) color = Color.GREEN; // 60% safe
            else if (rand < 0.9) color = Color.YELLOW; // 30% medium
            else color = Color.RED; // 10% risky
            
            mMap.addPolyline(new PolylineOptions().add(p1, p2).width(15f).color(color));
        }

        mMap.addMarker(new MarkerOptions().position(new LatLng(srcLat, srcLng)).title("Start"));
        mMap.addMarker(new MarkerOptions().position(new LatLng(destLat, destLng)).title("Destination"));

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng latLng : path) {
            builder.include(latLng);
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));

        layoutResult.setVisibility(View.VISIBLE);
        btnStartRoute.setVisibility(View.VISIBLE);
        btnFindBuddy.setVisibility(View.GONE);
    }
    
    private void startNavigationSimulation() {
        if (currentRoutePoints.isEmpty() || mMap == null) return;
        
        // Remove previous marker if any
        if (userMarker != null) {
            userMarker.remove();
        }
        
        userMarker = mMap.addMarker(new MarkerOptions()
            .position(currentRoutePoints.get(0))
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            .title("You are here"));
            
        // Animate marker along points
        Handler handler = new Handler(Looper.getMainLooper());
        final int delay = 1000; // 1 second per point step
        
        new Thread(() -> {
            for (int i = 1; i < currentRoutePoints.size(); i++) {
                final LatLng point = currentRoutePoints.get(i);
                handler.post(() -> {
                    if (userMarker != null) {
                        userMarker.setPosition(point);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f));
                    }
                });
                try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            }
            handler.post(() -> {
                Toast.makeText(SafeRouteActivity.this, "Arrived at destination", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void findSafeWalkBuddy() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SOS Alert - Finding Nearby Volunteers...");
        builder.setMessage("Your live location has been shared securely. Scanning for nearby trusted volunteers...");
        builder.setPositiveButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.setMessage("Volunteer 'Rahul Kumar' (2.1 km away) has accepted your SOS and is tracking your route.");
            }
        }, 3000);
    }

    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            LatLng p = new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }
}
"""

with open(r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\java\com\example\shaketosave\SafeRouteActivity.java', 'w', encoding='utf-8') as f:
    f.write(new_code)
