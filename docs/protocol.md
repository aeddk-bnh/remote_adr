# ARCS Protocol Specification v1.0

## Overview

ARCS uses JSON-based messaging over WebSocket for control commands and binary framing for video streaming. All connections use TLS, and payloads are encrypted with AES-256-GCM.

## Connection Flow

### 1. Initial Handshake

**Android Client → Server**
```json
{
  "type": "auth_request",
  "version": "1.0",
  "device_id": "unique-device-identifier",
  "device_info": {
    "model": "Pixel 7",
    "android_version": "14",
    "screen_width": 1080,
    "screen_height": 2400,
    "dpi": 420
  },
  "secret": "device-secret-key",
  "timestamp": 1704672000
}
```

**Server → Android Client**
```json
{
  "type": "auth_response",
  "success": true,
  "session_id": "uuid-v4",
  "jwt_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_at": 1704758400,
  "server_time": 1704672000
}
```

### 2. Controller Join

**Controller → Server**
```json
{
  "type": "join_session",
  "session_id": "uuid-v4",
  "jwt_token": "eyJhbGciOiJIUzI1NiIs...",
  "controller_type": "pc",  // or "web"
  "capabilities": {
    "video_codecs": ["h264", "h265"],
    "max_resolution": "1080p",
    "input_methods": ["touch", "keyboard"]
  }
}
```

**Server → Controller**
```json
{
  "type": "join_response",
  "success": true,
  "device_info": {
    "model": "Pixel 7",
    "screen_width": 1080,
    "screen_height": 2400
  },
  "video_config": {
    "codec": "h264",
    "width": 1080,
    "height": 2400,
    "fps": 30,
    "bitrate": 4000000
  }
}
```

## Message Types

### Control Commands

#### 1. Touch Events

**Tap**
```json
{
  "type": "touch",
  "action": "tap",
  "x": 540,
  "y": 1200,
  "timestamp": 1704672100
}
```

**Swipe**
```json
{
  "type": "touch",
  "action": "swipe",
  "start_x": 540,
  "start_y": 1800,
  "end_x": 540,
  "end_y": 600,
  "duration": 300,
  "timestamp": 1704672100
}
```

**Long Press**
```json
{
  "type": "touch",
  "action": "long_press",
  "x": 540,
  "y": 1200,
  "duration": 1000,
  "timestamp": 1704672100
}
```

**Pinch/Zoom**
```json
{
  "type": "touch",
  "action": "pinch",
  "center_x": 540,
  "center_y": 1200,
  "start_distance": 100,
  "end_distance": 300,
  "duration": 500,
  "timestamp": 1704672100
}
```

#### 2. Keyboard Events

**Text Input**
```json
{
  "type": "key",
  "action": "text",
  "text": "Hello World",
  "timestamp": 1704672100
}
```

**Key Press**
```json
{
  "type": "key",
  "action": "press",
  "keycode": "KEYCODE_BACK",  // Android KeyEvent constants
  "timestamp": 1704672100
}
```

**Key Combination**
```json
{
  "type": "key",
  "action": "combination",
  "keys": ["KEYCODE_CTRL", "KEYCODE_C"],
  "timestamp": 1704672100
}
```

#### 3. Application Control

**Launch App**
```json
{
  "type": "app_control",
  "action": "launch",
  "package_name": "com.android.chrome",
  "activity": "com.google.android.apps.chrome.Main"
}
```

**Stop App**
```json
{
  "type": "app_control",
  "action": "stop",
  "package_name": "com.android.chrome"
}
```

**Get App List**
```json
{
  "type": "app_control",
  "action": "list_apps"
}
```

**Response**
```json
{
  "type": "app_list",
  "apps": [
    {
      "package_name": "com.android.chrome",
      "app_name": "Chrome",
      "version": "120.0.6099.144",
      "icon_base64": "data:image/png;base64,..."
    }
  ]
}
```

#### 4. System Control

**Home**
```json
{
  "type": "system",
  "action": "home"
}
```

**Back**
```json
{
  "type": "system",
  "action": "back"
}
```

**Recent Apps**
```json
{
  "type": "system",
  "action": "recents"
}
```

**Notifications**
```json
{
  "type": "system",
  "action": "notifications"
}
```

**Quick Settings**
```json
{
  "type": "system",
  "action": "quick_settings"
}
```

**Lock Screen**
```json
{
  "type": "system",
  "action": "lock"
}
```

**Screenshot**
```json
{
  "type": "system",
  "action": "screenshot"
}
```

**Response**
```json
{
  "type": "screenshot_response",
  "success": true,
  "image_base64": "data:image/png;base64,..."
}
```

### Automation Commands

#### Macro Recording

**Start Recording**
```json
{
  "type": "macro",
  "action": "start_recording",
  "macro_name": "login_flow"
}
```

**Stop Recording**
```json
{
  "type": "macro",
  "action": "stop_recording"
}
```

**Response**
```json
{
  "type": "macro_recorded",
  "macro_name": "login_flow",
  "steps": [
    {
      "type": "touch",
      "action": "tap",
      "x": 540,
      "y": 800,
      "delay": 0
    },
    {
      "type": "key",
      "action": "text",
      "text": "username",
      "delay": 500
    }
  ],
  "total_duration": 5000
}
```

#### Macro Execution

**Execute Macro**
```json
{
  "type": "macro",
  "action": "execute",
  "macro_name": "login_flow",
  "loop": false,
  "speed": 1.0  // playback speed multiplier
}
```

**Execute with Conditions**
```json
{
  "type": "macro",
  "action": "execute_conditional",
  "macro_name": "login_flow",
  "conditions": [
    {
      "type": "ocr_match",
      "text": "Login",
      "action": "continue"
    },
    {
      "type": "ui_element",
      "id": "login_button",
      "visible": true
    }
  ]
}
```

### AI Commands

#### OCR Request

```json
{
  "type": "ai",
  "action": "ocr",
  "region": {
    "x": 0,
    "y": 0,
    "width": 1080,
    "height": 2400
  }
}
```

**Response**
```json
{
  "type": "ocr_response",
  "text_blocks": [
    {
      "text": "Login",
      "confidence": 0.98,
      "bounds": {
        "x": 400,
        "y": 1000,
        "width": 280,
        "height": 120
      }
    }
  ]
}
```

#### UI Detection

```json
{
  "type": "ai",
  "action": "detect_ui",
  "elements": ["button", "textfield", "checkbox"]
}
```

**Response**
```json
{
  "type": "ui_detection_response",
  "elements": [
    {
      "type": "button",
      "confidence": 0.95,
      "bounds": {
        "x": 400,
        "y": 1000,
        "width": 280,
        "height": 120
      },
      "text": "Login"
    }
  ]
}
```

#### Click by Text

```json
{
  "type": "ai",
  "action": "click_text",
  "text": "Login",
  "match_type": "exact"  // or "contains", "regex"
}
```

### Video Streaming

#### Video Configuration

**Server → Controller**
```json
{
  "type": "video_config",
  "codec": "h264",
  "profile": "baseline",
  "level": "4.1",
  "width": 1080,
  "height": 2400,
  "fps": 30,
  "bitrate": 4000000,
  "keyframe_interval": 30
}
```

#### Video Frame Format

Binary message structure:

```
[Magic: 4 bytes]["ARCS"]
[Version: 1 byte][0x01]
[Message Type: 1 byte][0x02 = video frame]
[Frame Number: 4 bytes][uint32_t big-endian]
[Timestamp: 8 bytes][uint64_t microseconds]
[Flags: 1 byte][bit 0: keyframe, bit 1: encrypted]
[Payload Length: 4 bytes][uint32_t]
[Payload: N bytes][H.264 NAL units]
[Checksum: 4 bytes][CRC32]
```

**Frame Flags:**
- Bit 0: Keyframe (I-frame)
- Bit 1: Encrypted
- Bit 2: Fragment (partial frame)
- Bit 3-7: Reserved

### Status & Monitoring

#### Heartbeat

```json
{
  "type": "ping",
  "timestamp": 1704672100
}
```

**Response**
```json
{
  "type": "pong",
  "timestamp": 1704672100
}
```

#### Status Report

**Android → Server (periodic)**
```json
{
  "type": "status",
  "battery_level": 85,
  "battery_status": "charging",
  "cpu_usage": 45.2,
  "memory_usage": 3200,
  "network_type": "wifi",
  "fps": 30,
  "bitrate": 4000000,
  "frame_drops": 2
}
```

#### Error Messages

```json
{
  "type": "error",
  "code": "ERR_PERMISSION_DENIED",
  "message": "Screen capture permission not granted",
  "details": {
    "required_permission": "SCREEN_CAPTURE"
  }
}
```

**Error Codes:**
- `ERR_AUTH_FAILED`: Authentication failure
- `ERR_PERMISSION_DENIED`: Permission not granted
- `ERR_DEVICE_BUSY`: Device in use by another session
- `ERR_UNSUPPORTED_OPERATION`: Operation not supported
- `ERR_INVALID_COMMAND`: Malformed command
- `ERR_RATE_LIMIT`: Too many requests
- `ERR_INTERNAL`: Server error

## Encryption

### AES-256-GCM Payload Format

```
[IV: 12 bytes][Encrypted Data: N bytes][Auth Tag: 16 bytes]
```

- IV: Random initialization vector (96 bits)
- Auth Tag: GCM authentication tag (128 bits)
- Key: Derived from session JWT

### Key Derivation

```
session_key = PBKDF2-HMAC-SHA256(
  password = jwt_token,
  salt = device_id,
  iterations = 100000,
  key_length = 32
)
```

## Rate Limiting

- Touch events: Max 100/second
- Text input: Max 10/second
- Macro execution: Max 1/second
- OCR requests: Max 5/second

## Versioning

Protocol version in all messages:
```json
{
  "version": "1.0",
  ...
}
```

Server must support backward compatibility for minor versions.

## WebSocket Subprotocol

```
Sec-WebSocket-Protocol: arcs-v1
```

## Connection Lifecycle

1. **Connect**: TLS handshake
2. **Authenticate**: auth_request/response
3. **Active**: Command/stream exchange
4. **Heartbeat**: Every 30 seconds
5. **Disconnect**: Graceful close or timeout (90s)

## Future Extensions

- Protocol v2: WebRTC data channels
- Audio streaming
- File transfer
- Clipboard sync
