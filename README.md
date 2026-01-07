# ARCS — Android Remote Control System

This repository contains the ARCS project (Android client, mock server, web controller, and helper scripts) for local end-to-end testing and deployment.

Quick overview
- Android client: `android-client`
- Mock server: `mock-server` (Node.js websocket + REST stubs)
- Web controller: `web-controller` (Vite + React)
- Scripts: `run-test.ps1`, platform helpers

Quick start (local testing)

1) Build Android debug APK

```powershell
cd android-client
#$env:PATH update may be needed to include your JDK and Gradle
#Use the included Gradle or your system Gradle
gradle assembleDebug
# APK will be at: android-client/app/build/outputs/apk/debug/app-debug.apk
```

2) Host APK for wireless install (no USB)

```powershell
cd android-client/app/build/outputs/apk/debug
python -m http.server 8000
# On phone open: http://<PC_IP>:8000/app-debug.apk and install (allow unknown sources)
```

3) Start mock server + web UI

From repo root (PowerShell):
```powershell
.\run-test.ps1
# or start individually:
cd mock-server
npm install
npm start
cd ../web-controller
npm install
npm run dev
```

4) Permissions & initial run on phone
- Grant notification permission (Android 13+).\
- Enable Accessibility service: Settings → Accessibility → ARCS Remote → Enable.\
- Enable IME: Settings → Languages & input → Virtual keyboard → Manage keyboards → ARCS IME.\
- When starting screen capture, grant MediaProjection dialog.

5) Wireless ADB (optional, for logs)

```powershell
# Pair (Android 11+): enable Wireless debugging on phone, then on PC:
adb pair <device-ip>:<pairing-port>
adb connect <device-ip>:5555
adb devices
adb logcat -v time > device-log.txt
```

Deployment notes
- For production builds, configure signing in `android-client/app/build.gradle` and build a release APK/AAB.\
- Provision a real server (C++ or Node) and secure WebSocket endpoints (wss://) behind HTTPS.\
- Use CI (GitHub Actions) to build, run unit tests, and upload artifacts.

Troubleshooting
- If app crashes on startup: get device logs via `adb logcat` or take a bugreport from Developer Options.\
- If phone cannot download hosted APK, ensure firewall allows port 8000.

Contact / Next steps
- If you want, I can push this repo to your GitHub remote and help configure GitHub Actions for builds.
# Android Remote Control System (ARCS)

A production-grade, non-root remote Android control platform enabling real-time screen streaming, input control, automation, and AI-assisted interaction.

## Architecture

```
Controller (PC/Web) ←→ Remote Server ←→ Android Client
```

## Components

### 1. Android Client (`android-client/`)
- **Language**: Kotlin
- **Features**: Screen capture, input injection, automation, AI modules
- **No root required**

### 2. Remote Server (`server/`)
- **Language**: C++ (Pistache) / Go
- **Features**: Authentication, session management, stream relay, security

### 3. PC Controller (`controller-pc/`)
- **Language**: C++ (Qt/ImGui)
- **Features**: Video playback, input control, macro management

### 4. Web Controller (`controller-web/`)
- **Language**: TypeScript (React)
- **Features**: Browser-based control interface

## Quick Start

See individual component READMEs:
- [Android Client](android-client/README.md)
- [Server](server/README.md)
- [PC Controller](controller-pc/README.md)
- [Web Controller](controller-web/README.md)

## Documentation

- [Architecture Guide](docs/architecture.md)
- [Protocol Specification](docs/protocol.md)
- [Security Design](docs/security.md)
- [API Reference](docs/api.md)

## Technology Stack

| Component | Language | Key Technologies |
|-----------|----------|------------------|
| Android Client | Kotlin | MediaProjection, AccessibilityService, H.264 |
| Server | C++/Go | WebSocket, JWT, AES-256 |
| PC Controller | C++ | Qt, FFmpeg |
| Web Controller | TypeScript | React, WebCodecs |

## License

MIT
