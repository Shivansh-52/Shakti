# 🛡️ Shakti (Suraksha Setu) — Intelligent Women & Citizen Safety Platform

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![Google Maps](https://img.shields.io/badge/Maps-Google%20Maps%20Platform-4285F4?style=for-the-badge&logo=google-maps&logoColor=white)](https://developers.google.com/maps)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

> **Empowering women and citizens with real-time proactive safety, intelligent safe route navigation, automated guardian live tracking, silent video evidence recording, and multi-sensor emergency SOS dispatch.**

---

## 📌 Table of Contents
- [✨ Key Features](#-key-features)
  - [1. 📳 Shake-to-SOS & Hardware Triggering](#1--shake-to-sos--hardware-triggering)
  - [2. 🎙️ Continuous Voice SOS Detection](#2-️-continuous-voice-sos-detection)
  - [3. 🚶‍♀️ Safe Walk & Moving Live Tracking](#3-️-safe-walk--moving-live-tracking)
  - [4. 🗺️ AI-Scored Safe Route Navigation (Dehradun Hub)](#4-️-ai-scored-safe-route-navigation-dehradun-hub)
  - [5. 📹 Automated Video Evidence & Email Dispatch](#5--automated-video-evidence--email-dispatch)
  - [6. 👥 Rapid Responders & Volunteer Network](#6--rapid-responders--volunteer-network)
  - [7. 🚨 Authority Escalation (112 & Women Helpline)](#7--authority-escalation-112--women-helpline)
- [🏗️ System Architecture](#️-system-architecture)
- [💻 Tech Stack](#-tech-stack)
- [⚙️ Getting Started & Installation](#️-getting-started--installation)
  - [Android Client Setup](#android-client-setup)
  - [Backend Server Setup](#backend-server-setup)
- [🔑 Environment & API Configuration](#-environment--api-configuration)
- [📱 Core Permissions Explained](#-core-permissions-explained)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)

---

## ✨ Key Features

### 1. 📳 Shake-to-SOS & Hardware Triggering
- **Real-Time Accelerometer Engine:** Uses sub-millisecond polling (`SENSOR_DELAY_GAME`) with normalized G-Force and delta acceleration thresholding (`> 1.55G`).
- **Background Foreground Service:** Runs persistently even when the screen is off or the phone is locked (`ShakeService`).
- **Bluetooth Disconnect Panic Mode:** Auto-triggers a 5-second countdown SOS if paired safety wearables or earphones are forcefully pulled away.
- **Hardware Button Triggers:** Supports triple volume key presses and media headset SOS hooks.

---

### 2. 🎙️ Continuous Voice SOS Detection
- **Multi-Keyword Speech Recognition:** Continuously monitors voice streams for distress triggers:
  - `"Help"`, `"SOS"`, `"Save me"`, `"Bachao"`, `"Police"`, `"Emergency"`, `"Madad"`, `"Shakti"`, `"Attack"`.
- **Custom Keyword Configuration:** Users can register custom secret safe words from their Citizen Profile.
- **In-Activity & Background Listening:** Active both as a persistent background daemon (`VoiceRecognitionService`) and during live in-app navigation.

---

### 3. 🚶‍♀️ Safe Walk & Moving Live Tracking
- **Real-Time GPS Movement:** Powered by `FusedLocationProviderClient` (`PRIORITY_HIGH_ACCURACY`, 1-second intervals, 1-meter min distance filter).
- **Auto-Dispatched SMS Link:** When journey begins, an SMS containing a direct Google Maps link (`https://maps.google.com/?q=<lat>,<lng>`) is automatically sent to all registered emergency contacts.
- **Live Movement Polyline:** Dynamically draws the travelled walking corridor on Google Maps.
- **Periodic Safety Check-Ins:** Automated prompt asking if the user is safe with countdown vibration alert.
- **"I Have Arrived Safely":** One-tap journey completion notifying guardians that the user reached safely.

---

### 4. 🗺️ AI-Scored Safe Route Navigation (Dehradun Hub)
- **Source ➔ Destination Routing:** Auto-detects current reverse-geocoded location and links with destination landmarks.
- **Dehradun Regional Intelligence:** Pre-configured destination chips:
  - 🏛️ *Clock Tower (Ghanta Ghar)*
  - 🛍️ *Pacific Mall (Rajpur Road)*
  - 🎓 *Graphic Era University (Subhash Nagar)*
  - 🌲 *Forest Research Institute (FRI) / Prem Nagar*
- **Safety Corridor Scoring:**
  - 🟢 **★ Safest Route (94-96/100):** High street lighting, open commercial areas, active police PCR routes.
  - 🟠 **Balanced Route (78-81/100):** Moderate lighting, primary link roads.
  - 🔴 **Fastest Route (62-64/100):** Direct shortcuts with caution flags.
- **Live Navigation HUD:** Shows speed (km/h), distance travelled, safety score, active Shake/Voice status pills, and pinned Floating Emergency SOS.

---

### 5. 📹 Automated Video Evidence & Email Dispatch
- **Silent Multi-Camera Recording:** Automatically captures high-resolution video and audio evidence in the background upon SOS activation (`VideoRecordingService`).
- **Automated SMTP Email Transmission:** Immediately dispatches the recorded video, location coordinates, timestamp, and user credentials directly to registered emergency guardian email addresses.

---

### 6. 👥 Rapid Responders & Volunteer Network
- **Nearby Community Guardian Alerts:** Scans and broadcasts emergency requests to verified student volunteers and civilian guardians within a 1km radius.
- **Safe Walk Buddy Finder:** Pairs night travelers with verified community companions walking along the same route.

---

### 7. 🚨 Authority Escalation (112 & Women Helpline)
- **Direct Dispatch Integration:** Instant 1-tap direct emergency dialing to **112 (National Emergency)**, **1090 (Women Helpline)**, and **1091 (Police Helpline)**.
- **Live Incident Reporting:** Allows users to submit safety hazard reports (e.g., broken streetlights, suspicious gatherings) with GPS geotagging.

---

## 🏗️ System Architecture

```
                               ┌────────────────────────────────┐
                               │     Shakti Android App         │
                               │  (Java Native + Material 3)    │
                               └───────────────┬────────────────┘
                                               │
             ┌─────────────────────────────────┼─────────────────────────────────┐
             │                                 │                                 │
             ▼                                 ▼                                 ▼
   ┌────────────────────┐            ┌────────────────────┐            ┌────────────────────┐
   │ Sensor & Hardware  │            │ Live Navigation &  │            │ Real-Time SOS &    │
   │ Subsystems         │            │ Safe Routes        │            │ Evidence Engine    │
   ├────────────────────┤            ├────────────────────┤            ├────────────────────┤
   │ • Accelerometer    │            │ • Google Maps SDK  │            │ • SMS Dispatcher   │
   │ • Shake Detector   │            │ • Fused Location   │            │ • CameraX Video    │
   │ • SpeechRecognizer │            │ • Polyline Router  │            │ • SMTP Mailer      │
   │ • Bluetooth Events │            │ • Dehradun ML Model│            │ • Sound Alarm      │
   └────────────────────┘            └────────────────────┘            └────────────────────┘
                                               │
                                               ▼
                               ┌────────────────────────────────┐
                               │      FastAPI Python API        │
                               │  • JWT Authentication          │
                               │  • ML Safety Scorer            │
                               │  • WebSocket Buddy Matcher     │
                               │  • Media Evidence Storage      │
                               └────────────────────────────────┘
```

---

## 💻 Tech Stack

| Layer | Technologies |
|---|---|
| **Mobile App (Android)** | Java 17, Android SDK 34, AndroidX, Material Design 3, Google Maps SDK, Play Services Location |
| **Networking & API** | Retrofit 2, OkHttp 3, Gson, WebSockets |
| **Sensors & Media** | Camera2 / CameraX, SpeechRecognizer, SensorManager (Accelerometer), MediaSession |
| **Backend Framework** | Python 3.11+, FastAPI, Uvicorn, Pydantic v2 |
| **Database** | MongoDB / Motor Async Driver |
| **Machine Learning** | Scikit-learn, Pandas, NumPy, Random Forest Route Risk Classifier |

---

## ⚙️ Getting Started & Installation

### Android Client Setup
1. **Clone the repository:**
   ```bash
   git clone https://github.com/Shivansh-52/Shakti.git
   cd Shakti
   ```
2. **Open in Android Studio:** Open the root directory in Android Studio (Giraffe / Hedgehog / Iguana / Jellyfish).
3. **Configure Google Maps API Key:**
   Add your Google Maps API Key in `app/src/main/AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_GOOGLE_MAPS_API_KEY" />
   ```
4. **Build the APK:**
   ```bash
   ./gradlew assembleRelease
   ```
5. **Install on connected device:**
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

---

### Backend Server Setup
1. **Navigate to the backend directory:**
   ```bash
   cd backend
   python -m venv venv
   source venv/bin/activate  # On Windows: .\venv\Scripts\activate
   ```
2. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```
3. **Start the FastAPI server:**
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```

---

## 🔑 Environment & API Configuration

Create a `.env` file in the `backend/` root directory:

```env
# Security & JWT
SECRET_KEY="your-super-secret-jwt-key"
ALGORITHM="HS256"
ACCESS_TOKEN_EXPIRE_MINUTES=1440

# MongoDB Configuration
MONGODB_URL="mongodb://localhost:27017"
DATABASE_NAME="suraksha_setu"

# SMTP Email Evidence Configuration
MAIL_USERNAME="your-email@gmail.com"
MAIL_PASSWORD="your-gmail-app-password"
MAIL_FROM="your-email@gmail.com"
MAIL_PORT=465
MAIL_SERVER="smtp.gmail.com"
MAIL_FROM_NAME="Shakti Emergency Response"
```

---

## 📱 Core Permissions Explained

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | Required for continuous moving GPS tracking, safe walk polylines, and emergency location dispatch. |
| `RECORD_AUDIO` | Enables continuous speech recognition for emergency voice keywords (`"Help"`, `"SOS"`, `"Bachao"`). |
| `CAMERA` | Allows automated silent video evidence capture during emergency SOS. |
| `SEND_SMS` | Automatically dispatches live Google Maps tracking links to registered emergency guardians. |
| `FOREGROUND_SERVICE` | Keeps location tracking, shake detection, and voice monitoring alive when the phone is locked. |

---

## 🤝 Contributing
Contributions, issues, and feature requests are welcome!
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License
Distributed under the **MIT License**. See `LICENSE` for more information.

---

<div align="center">
  <sub>Built with ❤️ for Citizen & Women Safety • Project <b>Shakti</b></sub>
</div>
