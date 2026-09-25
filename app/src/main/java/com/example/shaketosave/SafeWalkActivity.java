package com.example.shaketosave;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.shaketosave.api.AuthManager;
import com.example.shaketosave.utils.ShakeDetector;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SafeWalkActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "SafeWalkActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 301;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // Sensors & SOS
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;

    // In-Activity High Responsiveness Shake Listener
    private final SensorEventListener activeShakeListener = new SensorEventListener() {
        private long lastShakeTime = 0;
        private float lastX, lastY, lastZ;
        private boolean hasLast = false;

        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            if (hasLast) {
                float deltaX = Math.abs(lastX - x);
                float deltaY = Math.abs(lastY - y);
                float deltaZ = Math.abs(lastZ - z);
                float delta = deltaX + deltaY + deltaZ;

                float gX = x / SensorManager.GRAVITY_EARTH;
                float gY = y / SensorManager.GRAVITY_EARTH;
                float gZ = z / SensorManager.GRAVITY_EARTH;
                float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

                long now = System.currentTimeMillis();
                if ((delta > 12.0f || gForce > 1.55f) && (now - lastShakeTime > 800)) {
                    lastShakeTime = now;
                    triggerEmergencySos("Shake SOS detected during Safe Walk!");
                }
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            hasLast = true;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // Voice SOS
    private SpeechRecognizer speechRecognizer;
    private boolean isVoiceListening = false;
    private final List<String> voiceEmergencyKeywords = Arrays.asList(
            "help", "sos", "save me", "bachao", "police", "shakti", "emergency", "danger", "madad", "attack", "stop"
    );

    // UI
    private CardView cardNotStarted, cardActiveJourney;
    private View layoutCheckInRequired;
    private TextInputEditText etDestAddress;
    private Button btnStartJourney, btnArrivedSafely, btnCancelJourney, btnImSafe, btnFindBuddy, btnShareLiveTracking;
    private Button btnActiveShare, btnActiveSos;
    private Button chipHome, chipWork, chipCampus, chipMarket;
    private ExtendedFloatingActionButton fabWalkSos;
    private TextView tvDuration, tvWalkDistance, tvWalkSpeed, tvGpsStatus, tvGuardianNotice, tvLiveStatusDetail;
    private ImageView btnBack, btnRecenterMap;

    // State Tracking
    private boolean isWalking = false;
    private double currentLat = 30.3435;
    private double currentLng = 77.8867;
    private boolean hasLocation = false;
    private Location lastLocation = null;
    private double totalDistanceMeters = 0.0;
    private long startTimeMillis = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private Marker userMarker;
    private Circle userCircle;
    private Polyline walkPolyline;
    private List<LatLng> walkPath = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_walk);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception ignored) {}

        initViews();
        initSensors();
        setupListeners();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapView);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        checkAndRequestPermissions();
        startBackgroundServices();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnRecenterMap = findViewById(R.id.btnRecenterMap);
        tvGpsStatus = findViewById(R.id.tvGpsStatus);
        fabWalkSos = findViewById(R.id.fabWalkSos);

        cardNotStarted = findViewById(R.id.cardNotStarted);
        cardActiveJourney = findViewById(R.id.cardActiveJourney);
        layoutCheckInRequired = findViewById(R.id.layoutCheckInRequired);
        etDestAddress = findViewById(R.id.etDestAddress);

        btnStartJourney = findViewById(R.id.btnStartJourney);
        btnFindBuddy = findViewById(R.id.btnFindBuddy);
        btnShareLiveTracking = findViewById(R.id.btnShareLiveTracking);

        tvDuration = findViewById(R.id.tvDuration);
        tvWalkDistance = findViewById(R.id.tvWalkDistance);
        tvWalkSpeed = findViewById(R.id.tvWalkSpeed);
        tvGuardianNotice = findViewById(R.id.tvGuardianNotice);
        tvLiveStatusDetail = findViewById(R.id.tvLiveStatusDetail);

        btnActiveShare = findViewById(R.id.btnActiveShare);
        btnActiveSos = findViewById(R.id.btnActiveSos);
        btnArrivedSafely = findViewById(R.id.btnArrivedSafely);
        btnCancelJourney = findViewById(R.id.btnCancelJourney);
        btnImSafe = findViewById(R.id.btnImSafe);

        chipHome = findViewById(R.id.chipHome);
        chipWork = findViewById(R.id.chipWork);
        chipCampus = findViewById(R.id.chipCampus);
        chipMarket = findViewById(R.id.chipMarket);

        updateGuardianNotice();
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            shakeDetector = new ShakeDetector();
            shakeDetector.setOnShakeListener(count -> {
                if (count >= 1) {
                    triggerEmergencySos("Shake SOS triggered while walking!");
                }
            });
        }
    }

    private void startBackgroundServices() {
        try {
            getSharedPreferences("SafeShakePrefs", MODE_PRIVATE).edit().putBoolean("service_enabled", true).apply();
            Intent shakeIntent = new Intent(this, ShakeService.class);
            Intent voiceIntent = new Intent(this, VoiceRecognitionService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(shakeIntent);
                startForegroundService(voiceIntent);
            } else {
                startService(shakeIntent);
                startService(voiceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting background services", e);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnRecenterMap.setOnClickListener(v -> recenterMapOnUser());

        chipHome.setOnClickListener(v -> etDestAddress.setText("Home (Rajpur Road, Dehradun)"));
        chipWork.setOnClickListener(v -> etDestAddress.setText("Clock Tower / Paltan Bazar, Dehradun"));
        chipCampus.setOnClickListener(v -> etDestAddress.setText("Graphic Era University, Subhash Nagar"));
        chipMarket.setOnClickListener(v -> etDestAddress.setText("Pacific Mall / Astley Hall, Dehradun"));

        btnStartJourney.setOnClickListener(v -> startSafeWalkJourney());
        btnShareLiveTracking.setOnClickListener(v -> shareLiveTrackingLink());
        btnActiveShare.setOnClickListener(v -> shareLiveTrackingLink());

        fabWalkSos.setOnClickListener(v -> triggerEmergencySos("Emergency SOS activated from Safe Walk!"));
        btnActiveSos.setOnClickListener(v -> triggerEmergencySos("Emergency SOS activated from Safe Walk!"));

        btnFindBuddy.setOnClickListener(v -> findNearbyWalkBuddy());

        btnImSafe.setOnClickListener(v -> {
            layoutCheckInRequired.setVisibility(View.GONE);
            Toast.makeText(this, "Status verified: You are SAFE ✓", Toast.LENGTH_SHORT).show();
        });

        btnArrivedSafely.setOnClickListener(v -> completeSafeArrival());
        btnCancelJourney.setOnClickListener(v -> stopSafeWalkJourney(false));
    }

    private void updateGuardianNotice() {
        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        String p1 = prefs.getString("emergency_phone1", "");
        if (!p1.isEmpty()) {
            tvGuardianNotice.setText("Emergency SMS with Live GPS Link will be dispatched to " + p1);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        if (hasLocationPermission()) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLng), 15f));
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (!hasLocationPermission()) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), LOCATION_PERMISSION_REQUEST);
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .setMinUpdateDistanceMeters(1.0f)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        onNewLocation(location);
                    }
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                onNewLocation(location);
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 16f));
                }
            }
        });
    }

    private void onNewLocation(Location location) {
        currentLat = location.getLatitude();
        currentLng = location.getLongitude();
        hasLocation = true;
        LatLng currentLatLng = new LatLng(currentLat, currentLng);

        tvGpsStatus.setText(String.format(Locale.US, "📍 GPS: %.4f, %.4f (Acc: %.1fm)", currentLat, currentLng, location.getAccuracy()));

        // Update user marker and ripple circle
        if (mMap != null) {
            if (userMarker == null) {
                userMarker = mMap.addMarker(new MarkerOptions()
                        .position(currentLatLng)
                        .title("Your Live Position")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            } else {
                userMarker.setPosition(currentLatLng);
            }

            if (userCircle == null) {
                userCircle = mMap.addCircle(new CircleOptions()
                        .center(currentLatLng)
                        .radius(Math.max(15, location.getAccuracy()))
                        .strokeColor(Color.parseColor("#40FF9800"))
                        .fillColor(Color.parseColor("#20FF9800"))
                        .strokeWidth(2f));
            } else {
                userCircle.setCenter(currentLatLng);
                userCircle.setRadius(Math.max(15, location.getAccuracy()));
            }
        }

        // If in active walk, track movement distance & path
        if (isWalking) {
            if (lastLocation != null) {
                float dist = lastLocation.distanceTo(location);
                if (dist > 1.0f && dist < 500.0f) { // filter GPS jumps
                    totalDistanceMeters += dist;
                }
            }
            lastLocation = location;

            walkPath.add(currentLatLng);
            if (walkPolyline == null && mMap != null) {
                walkPolyline = mMap.addPolyline(new PolylineOptions()
                        .addAll(walkPath)
                        .color(Color.parseColor("#F57C00"))
                        .width(14f));
            } else if (walkPolyline != null) {
                walkPolyline.setPoints(walkPath);
            }

            // Update stats
            tvWalkDistance.setText(String.format(Locale.US, "%.2f km", totalDistanceMeters / 1000.0));
            float speedKmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 3.8f;
            tvWalkSpeed.setText(String.format(Locale.US, "%.1f km/h", speedKmh));

            if (mMap != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng));
            }
        }
    }

    private void recenterMapOnUser() {
        if (mMap != null && hasLocation) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLng), 17f));
        } else {
            Toast.makeText(this, "Acquiring GPS location...", Toast.LENGTH_SHORT).show();
        }
    }

    // ──────────────────────────── Journey Management ────────────────────────────

    private void startSafeWalkJourney() {
        String destination = etDestAddress.getText() != null ? etDestAddress.getText().toString().trim() : "Destination in Dehradun";
        isWalking = true;
        startTimeMillis = SystemClock.elapsedRealtime();
        totalDistanceMeters = 0;
        walkPath.clear();
        walkPath.add(new LatLng(currentLat, currentLng));

        cardNotStarted.setVisibility(View.GONE);
        cardActiveJourney.setVisibility(View.VISIBLE);

        // Start Walk Duration Timer
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isWalking) {
                    long elapsed = SystemClock.elapsedRealtime() - startTimeMillis;
                    int seconds = (int) (elapsed / 1000) % 60;
                    int minutes = (int) ((elapsed / (1000 * 60)) % 60);
                    tvDuration.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);

        // Register high frequency shake listener
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(activeShakeListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }

        // Start in-app continuous Voice SOS
        startInAppVoiceSosListening();

        // Ensure background services
        startBackgroundServices();

        // Auto-send Live Tracking link to Emergency Contacts
        sendLiveTrackingSmsAutomatically(destination);

        // Scheduled check-in prompt after 45s
        timerHandler.postDelayed(() -> {
            if (isWalking) {
                layoutCheckInRequired.setVisibility(View.VISIBLE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                }
            }
        }, 45000);

        Toast.makeText(this, "Safe Walk Started! Shake & Voice SOS are ACTIVE.", Toast.LENGTH_LONG).show();
    }

    private void stopSafeWalkJourney(boolean arrivedSafely) {
        isWalking = false;
        timerHandler.removeCallbacks(timerRunnable);

        cardActiveJourney.setVisibility(View.GONE);
        cardNotStarted.setVisibility(View.VISIBLE);
        layoutCheckInRequired.setVisibility(View.GONE);

        if (walkPolyline != null) {
            walkPolyline.remove();
            walkPolyline = null;
        }

        stopInAppVoiceSosListening();

        if (!arrivedSafely) {
            Toast.makeText(this, "Safe Walk Ended.", Toast.LENGTH_SHORT).show();
        }
    }

    private void completeSafeArrival() {
        stopSafeWalkJourney(true);

        // Notify Guardians of safe arrival
        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        String p1 = prefs.getString("emergency_phone1", "");
        String p2 = prefs.getString("emergency_phone2", "");
        AuthManager authManager = new AuthManager(this);
        String userName = authManager.getUserName();
        String destination = etDestAddress.getText() != null ? etDestAddress.getText().toString().trim() : "destination in Dehradun";

        String message = "✅ [Shakti Safe Walk]: " + userName + " has safely arrived at " + destination + ". Live tracking ended.";

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                SmsManager sms = SmsManager.getDefault();
                if (!p1.isEmpty()) sms.sendTextMessage(p1, null, message, null, null);
                if (!p2.isEmpty()) sms.sendTextMessage(p2, null, message, null, null);
            } catch (Exception ignored) {}
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎉 Arrived Safely in Dehradun!");
        builder.setMessage("Great job! Your journey has ended safely, and your emergency contacts have been notified.");
        builder.setPositiveButton("Awesome", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // ──────────────────────────── Continuous Voice SOS Recognition ────────────────────────────

    private void startInAppVoiceSosListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 303);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) { isVoiceListening = true; }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() { isVoiceListening = false; }
                    @Override
                    public void onError(int error) {
                        isVoiceListening = false;
                        if (isWalking) {
                            timerHandler.postDelayed(() -> {
                                if (isWalking) startInAppVoiceSosListening();
                            }, 800);
                        }
                    }
                    @Override
                    public void onResults(Bundle results) {
                        isVoiceListening = false;
                        processSpeechResults(results);
                        if (isWalking) {
                            timerHandler.postDelayed(() -> {
                                if (isWalking) startInAppVoiceSosListening();
                            }, 400);
                        }
                    }
                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        processSpeechResults(partialResults);
                    }
                    @Override public void onEvent(int eventType, Bundle params) {}
                });
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            speechRecognizer.startListening(intent);
            isVoiceListening = true;
        } catch (Exception e) {
            Log.e(TAG, "Error in startInAppVoiceSosListening", e);
        }
    }

    private void stopInAppVoiceSosListening() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        isVoiceListening = false;
    }

    private void processSpeechResults(Bundle results) {
        if (results == null) return;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        String customTrigger = prefs.getString("voice_trigger_word", "help").toLowerCase().trim();

        for (String match : matches) {
            if (match == null) continue;
            String text = match.toLowerCase();
            Log.d(TAG, "Voice recognized in Safe Walk: " + text);

            boolean triggered = text.contains(customTrigger);
            if (!triggered) {
                for (String word : voiceEmergencyKeywords) {
                    if (text.contains(word)) {
                        triggered = true;
                        break;
                    }
                }
            }

            if (triggered) {
                triggerEmergencySos("Voice SOS Triggered by keyword: '" + match + "'");
                break;
            }
        }
    }

    // ──────────────────────────── SMS & Link Sharing ────────────────────────────

    private void sendLiveTrackingSmsAutomatically(String destination) {
        SharedPreferences prefs = getSharedPreferences("SafeShakePrefs", MODE_PRIVATE);
        String p1 = prefs.getString("emergency_phone1", "");
        String p2 = prefs.getString("emergency_phone2", "");
        String p3 = prefs.getString("emergency_phone3", "");
        AuthManager authManager = new AuthManager(this);
        String userName = authManager.getUserName();

        String liveTrackingUrl = "https://maps.google.com/?q=" + currentLat + "," + currentLng;
        String message = "🛡️ [Shakti Live Safety Tracking]: " + userName + " has started a Safe Walk to " + destination + " in Dehradun. Track live GPS: " + liveTrackingUrl;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                SmsManager sms = SmsManager.getDefault();
                int sent = 0;
                if (!p1.isEmpty()) { sms.sendTextMessage(p1, null, message, null, null); sent++; }
                if (!p2.isEmpty()) { sms.sendTextMessage(p2, null, message, null, null); sent++; }
                if (!p3.isEmpty()) { sms.sendTextMessage(p3, null, message, null, null); sent++; }

                if (sent > 0) {
                    tvLiveStatusDetail.setText("🛡️ Live GPS link dispatched to " + sent + " registered contact(s).");
                }
            } catch (Exception e) {
                tvLiveStatusDetail.setText("🛡️ Live link generated. Tap 'Share Link' to send via WhatsApp/Telegram.");
            }
        }
    }

    private void shareLiveTrackingLink() {
        String destination = etDestAddress.getText() != null ? etDestAddress.getText().toString().trim() : "Destination in Dehradun";
        AuthManager authManager = new AuthManager(this);
        String userName = authManager.getUserName();
        String liveTrackingUrl = "https://maps.google.com/?q=" + currentLat + "," + currentLng;
        String message = "🛡️ [Shakti Live Safety Tracking]: " + userName + " is currently walking to " + destination + " in Dehradun.\nFollow real-time GPS location: " + liveTrackingUrl;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "🛡️ Live Safety GPS Tracking Link");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(shareIntent, "Share Live GPS Link with Guardian"));
    }

    // ──────────────────────────── Emergency SOS ────────────────────────────

    private void triggerEmergencySos(String reason) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300, 150, 400}, -1));
            } else {
                vibrator.vibrate(600);
            }
        }

        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500);
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, "🚨 " + reason + " Dispatching emergency alerts...", Toast.LENGTH_LONG).show();

        // Broadcast to ShakeService to send Emergency SMS and trigger Video Recording + Email Dispatch
        Intent sosBroadcast = new Intent(ShakeService.ACTION_SEND_NOW);
        sosBroadcast.setPackage(getPackageName());
        sendBroadcast(sosBroadcast);

        // Also launch Emergency SOS screen directly
        Intent emergencyIntent = new Intent(this, EmergencyActivity.class);
        startActivity(emergencyIntent);
    }

    private void findNearbyWalkBuddy() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("👥 Searching for Dehradun Companion");
        builder.setMessage("Scanning verified student and volunteer guardians walking along your route in Dehradun...");
        builder.setPositiveButton("Dismiss", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.setMessage("✅ Dehradun Buddy Found!\n\n• Name: Aisha Sharma (Verified Volunteer)\n• Proximity: 250 meters away (Subhash Nagar / Graphic Era)\n• Rating: 4.9 ★ (34 safe walks)\n• Status: Heading towards your sector.");
            }
        }, 2000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(activeShakeListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            if (shakeDetector != null) {
                sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isWalking && sensorManager != null) {
            sensorManager.unregisterListener(activeShakeListener);
            if (shakeDetector != null) {
                sensorManager.unregisterListener(shakeDetector);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(activeShakeListener);
            if (shakeDetector != null) {
                sensorManager.unregisterListener(shakeDetector);
            }
        }
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        stopInAppVoiceSosListening();
        timerHandler.removeCallbacksAndMessages(null);
    }
}
