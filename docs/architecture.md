# ARCS Architecture Documentation

## System Overview

ARCS (Android Remote Control System) is a distributed system with three main components communicating over secure WebSocket connections.

## Component Architecture

### Android Client

```
┌─────────────────────────────────────────┐
│         Android Client (Kotlin)         │
├─────────────────────────────────────────┤
│  ┌─────────┐  ┌──────────┐  ┌────────┐ │
│  │  Core   │  │Projection│  │ Input  │ │
│  └─────────┘  └──────────┘  └────────┘ │
│  ┌─────────┐  ┌──────────┐  ┌────────┐ │
│  │Accessib.│  │Automation│  │   AI   │ │
│  └─────────┘  └──────────┘  └────────┘ │
│  ┌─────────────────────────────────┐   │
│  │       Network Layer             │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

**Modules:**
- **Core**: App context, permissions, device info
- **Projection**: Screen capture, H.264 encoding, packetization
- **Input**: Touch/key injection via Accessibility & IME
- **Accessibility**: RemoteAccessibilityService
- **Automation**: Macro recording & execution
- **AI**: OCR, UI detection
- **Network**: WebSocket client, encryption, command dispatcher

### Remote Server

```
┌─────────────────────────────────────────┐
│        Remote Server (C++/Go)           │
├─────────────────────────────────────────┤
│  ┌──────────┐  ┌───────────────────┐   │
│  │   Auth   │  │  Session Manager  │   │
│  └──────────┘  └───────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │    WebSocket Handler            │   │
│  └─────────────────────────────────┘   │
│  ┌──────────┐  ┌───────────────────┐   │
│  │ Router   │  │    Security       │   │
│  └──────────┘  └───────────────────┘   │
└─────────────────────────────────────────┘
```

**Modules:**
- **Auth**: JWT management, device registry
- **WebSocket**: Connection handling, message parsing
- **Router**: Command routing, stream relay
- **Security**: Encryption, rate limiting
- **Storage**: Session persistence (SQLite/Redis)

### Controllers

```
┌──────────────┐       ┌──────────────┐
│ PC (Qt/ImGui)│       │  Web (React) │
├──────────────┤       ├──────────────┤
│ UI Layer     │       │ Components   │
│ Video Decode │       │ Services     │
│ Input Trans. │       │ WebCodecs    │
│ WebSocket    │       │ WebSocket    │
└──────────────┘       └──────────────┘
```

## Data Flow

### 1. Screen Streaming (Android → Controller)

```
MediaProjection → ScreenCapturer → H.264 Encoder →
Packetizer → Encrypt → WebSocket → Server →
Controller → Decrypt → Decode → Render
```

### 2. Input Control (Controller → Android)

```
User Input → InputTranslator → JSON Command →
WebSocket → Server → Route → Android →
CommandDispatcher → TouchInjector/KeyInjector →
AccessibilityService → System
```

## Communication Protocol

### WebSocket Message Types

1. **Authentication**
   - `auth_request`: Client → Server
   - `auth_response`: Server → Client

2. **Control**
   - `touch`: Tap, swipe, gesture
   - `key`: Keyboard input
   - `app_control`: Launch, stop apps

3. **Streaming**
   - `video_frame`: Encoded video data
   - `video_config`: Codec parameters

4. **Automation**
   - `macro_start`, `macro_stop`, `macro_execute`

5. **AI**
   - `ocr_request`, `ui_detect_request`

## Security Architecture

### Layers

1. **Transport**: TLS 1.3
2. **Authentication**: JWT tokens
3. **Payload Encryption**: AES-256-GCM
4. **Authorization**: Device whitelisting

### Session Flow

```
1. Android → Server: auth_request (device_id, secret)
2. Server → Android: auth_response (JWT)
3. Android ←→ Server: Persistent connection (JWT in header)
4. Controller → Server: join_session (JWT)
5. Controller ←→ Android: Proxied commands/streams
```

## Scalability

- Horizontal server scaling with session affinity
- Redis for distributed session management
- WebRTC for P2P mode (future)

## Non-Root Design

**MediaProjection API**: Requires user consent dialog (one-time per session)
**AccessibilityService**: User enables in settings (one-time)
**InputMethodService**: For keyboard injection

No shell commands, no ADB, no system modifications required.

## Performance Targets

- Latency: <100ms (local network)
- Frame rate: 30-60 FPS
- Resolution: Up to 1080p
- Bitrate: Adaptive (1-10 Mbps)

## Future Enhancements

- WebRTC for lower latency
- Multi-controller support
- Cloud relay mode
- AAOS compatibility
