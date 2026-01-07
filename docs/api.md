# ARCS API Reference

## Authentication

### Device Registration

**Request:**
```http
POST /api/devices/register
Content-Type: application/json

{
  "device_id": "unique-device-id",
  "device_secret": "generated-secret",
  "device_model": "Pixel 7"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Device registered successfully"
}
```

### Authentication

**WebSocket Message:**
```json
{
  "type": "auth_request",
  "device_id": "device-id",
  "secret": "device-secret",
  "timestamp": 1704672000
}
```

**Response:**
```json
{
  "type": "auth_response",
  "success": true,
  "jwt_token": "eyJhbGc...",
  "session_id": "uuid",
  "expires_at": 1704758400
}
```

## Control Commands

### Touch Commands

#### Tap
```json
{
  "type": "touch",
  "action": "tap",
  "x": 540,
  "y": 1200
}
```

#### Swipe
```json
{
  "type": "touch",
  "action": "swipe",
  "start_x": 540,
  "start_y": 1800,
  "end_x": 540,
  "end_y": 600,
  "duration": 300
}
```

### Keyboard Commands

#### Text Input
```json
{
  "type": "key",
  "action": "text",
  "text": "Hello World"
}
```

#### Key Press
```json
{
  "type": "key",
  "action": "press",
  "keycode": "KEYCODE_BACK"
}
```

### System Commands

#### Navigation
```json
{
  "type": "system",
  "action": "home|back|recents|notifications"
}
```

## Video Streaming

### Frame Format

Binary packet structure:
```
[Magic: 4B]["ARCS"]
[Version: 1B][0x01]
[Type: 1B][0x02]
[Frame#: 4B][uint32]
[Timestamp: 8B][uint64 microseconds]
[Flags: 1B][keyframe|encrypted|fragment]
[PayloadLen: 4B][uint32]
[Payload: N bytes][H.264 data]
[CRC32: 4B][checksum]
```

### Configuration
```json
{
  "type": "video_config",
  "codec": "h264",
  "width": 1080,
  "height": 2400,
  "fps": 30,
  "bitrate": 4000000
}
```

## Automation

### Macro Recording
```json
{
  "type": "macro",
  "action": "start_recording",
  "macro_name": "login_flow"
}
```

### Macro Execution
```json
{
  "type": "macro",
  "action": "execute",
  "macro_name": "login_flow"
}
```

## AI Commands

### OCR
```json
{
  "type": "ai",
  "action": "ocr",
  "region": {"x": 0, "y": 0, "width": 1080, "height": 2400}
}
```

**Response:**
```json
{
  "type": "ocr_response",
  "text_blocks": [
    {
      "text": "Login",
      "confidence": 0.98,
      "bounds": {"x": 400, "y": 1000, "width": 280, "height": 120}
    }
  ]
}
```

## Error Codes

| Code | Message | Description |
|------|---------|-------------|
| ERR_AUTH_FAILED | Authentication failure | Invalid credentials |
| ERR_PERMISSION_DENIED | Permission not granted | Missing Android permission |
| ERR_DEVICE_BUSY | Device in use | Another session active |
| ERR_INVALID_COMMAND | Malformed command | JSON parse error |
| ERR_RATE_LIMIT | Too many requests | Rate limit exceeded |

## Rate Limits

| Operation | Limit | Window |
|-----------|-------|--------|
| Touch events | 100 | per second |
| Text input | 10 | per second |
| Macro execution | 1 | per second |
| OCR requests | 5 | per second |
| Authentication | 5 | per minute |

## Security

### Encryption

All payloads encrypted with AES-256-GCM:
```
[IV: 12 bytes][Encrypted Data][Auth Tag: 16 bytes]
```

### Token Format

JWT with HS256:
```json
{
  "iss": "arcs-server",
  "sub": "device-id",
  "exp": 1704758400,
  "session_id": "uuid",
  "permissions": ["screen_capture", "input_control"]
}
```

## WebSocket Lifecycle

1. **Connect**: `wss://server/ws`
2. **Authenticate**: Send `auth_request`
3. **Active**: Exchange commands/streams
4. **Heartbeat**: Ping every 30s
5. **Disconnect**: Timeout after 300s idle
