# Android Remote Control Client

Android application for remote control via ARCS platform.

## Features

- Screen streaming (H.264)
- Touch input injection
- Keyboard input
- System navigation
- App control
- Macro recording/playback
- AI-assisted interaction (OCR, UI detection)
- No root required

## Requirements

- Android 8.0 (API 26) or higher
- Permissions:
  - Internet access
  - MediaProjection (screen capture)
  - AccessibilityService (touch injection)
  - InputMethodService (keyboard)

## Building

```bash
cd android-client
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

## Setup

1. Install APK on Android device
2. Open app and grant permissions:
   - Allow screen capture (one-time per session)
   - Enable Accessibility Service in Settings
   - Enable ARCS Input Method in Settings
3. Configure server URL
4. Start service

## Configuration

Settings > Server Configuration:
- Server URL: `wss://your-server.com`
- Device Secret: (generated on first run)
- Video Quality: Auto, 1080p, 720p, 480p
- Frame Rate: 30, 60 FPS

## Usage

### Manual Control
1. Start service
2. Connect from controller
3. Control device remotely

### Automation
1. Record macro
2. Save macro
3. Execute macro

## Development

### Project Structure

```
android-client/
├── app/src/main/java/com/arcs/
│   ├── core/           # Core utilities
│   ├── projection/     # Screen capture
│   ├── input/          # Input injection
│   ├── accessibility/  # Accessibility service
│   ├── network/        # WebSocket, encryption
│   ├── automation/     # Macros
│   ├── ai/            # OCR, UI detection
│   └── service/        # Main service
└── build.gradle
```

### Testing

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Troubleshooting

### Screen capture not working
- Grant MediaProjection permission
- Check notification permission (Android 13+)

### Touch injection not working
- Enable Accessibility Service in Settings > Accessibility
- Verify service is running

### Connection issues
- Check server URL
- Verify network connectivity
- Check firewall settings

## License

MIT
