# ARCS PC Controller

Desktop controller application for Android Remote Control System (ARCS).

## Features

- **Real-time Video Streaming**: H.264 video decoding via FFmpeg
- **Touch Input Simulation**: Mouse clicks and gestures mapped to Android touch events
- **Keyboard Input**: PC keyboard mapped to Android key events
- **System Controls**: Home, Back, Recent Apps buttons
- **Low Latency**: Optimized video decoding and rendering pipeline

## Requirements

### Build Dependencies

- Qt 6.x (Core, Widgets, Network, Multimedia)
- FFmpeg 4.x+ (libavcodec, libavformat, libavutil, libswscale)
- WebSocket++ 0.8+
- Boost 1.70+
- OpenSSL 1.1+
- CMake 3.16+

### Runtime Dependencies

- Qt 6 runtime libraries
- FFmpeg shared libraries

## Building

```bash
mkdir build
cd build
cmake ..
make
```

## Installation

### Linux

```bash
sudo make install
```

### Windows

1. Build the project
2. Copy executable and DLLs to desired location
3. Run `arcs-pc-controller.exe`

### macOS

```bash
make install
open /Applications/ARCS\ PC\ Controller.app
```

## Usage

1. **Launch Application**: Start the ARCS PC Controller
2. **Configure Server**: Enter WebSocket server URL (e.g., `ws://192.168.1.100:8080`)
3. **Enter Session ID**: Input the session ID from the Android device
4. **Connect**: Click "Connect" button
5. **Control Device**:
   - Click on video to simulate touch
   - Drag to simulate swipe
   - Long click for long press
   - Use keyboard for text input
   - Use system control buttons

## Controls

### Mouse Input

- **Left Click**: Tap at cursor position
- **Left Click + Hold**: Long press
- **Left Click + Drag**: Swipe gesture

### Keyboard Input

- **Text Keys**: Send as text input
- **Backspace**: Android DEL key
- **Enter**: Android ENTER key
- **Home**: Android HOME button
- **Escape**: Android BACK button

### System Controls

- **Home Button**: Navigate to home screen
- **Back Button**: Navigate back
- **Recent Apps**: Show recent applications

## Architecture

```
┌─────────────────┐
│   Main Window   │
├─────────────────┤
│  Video Widget   │  ← Display decoded frames
├─────────────────┤
│ Control Panel   │  ← Connection & system controls
└─────────────────┘
        ↓
┌─────────────────┐
│ WebSocket Client│  ← Server communication
└─────────────────┘
        ↓
┌─────────────────┐
│ Video Decoder   │  ← FFmpeg H.264 decoding
└─────────────────┘
```

## Configuration

### Server URL

Default: `ws://localhost:8080`

Format: `ws://host:port` or `wss://host:port` (for TLS)

### Video Settings

- Codec: H.264
- Resolution: Auto-detected from device
- Frame Rate: 30 FPS (target)

## Performance Optimization

### Low Latency Mode

- Hardware-accelerated decoding (if available)
- Frame queue management to prevent buffering
- Adaptive quality based on network conditions

### Resource Usage

- CPU: ~5-10% (Intel i5, 1080p stream)
- RAM: ~100 MB
- Network: 2-5 Mbps (depends on video quality)

## Troubleshooting

### Connection Issues

- **Cannot connect**: Check server URL and firewall settings
- **Session not found**: Verify session ID from Android device
- **Connection drops**: Check network stability

### Video Issues

- **No video**: Verify H.264 codec support in FFmpeg
- **Choppy video**: Check network bandwidth and CPU usage
- **Lag**: Reduce video quality on Android device

### Input Issues

- **Touch not working**: Verify session is connected
- **Wrong coordinates**: Check aspect ratio settings
- **Keyboard not responding**: Ensure video widget has focus

## Development

### Project Structure

```
pc-controller/
├── src/
│   ├── ui/              # Qt UI components
│   ├── network/         # WebSocket client
│   ├── decoder/         # Video decoder
│   ├── input/           # Input translation
│   └── main.cpp
├── CMakeLists.txt
└── README.md
```

### Adding Features

1. **New UI Component**: Add to `src/ui/`
2. **Network Protocol**: Modify `websocket_client.cpp`
3. **Input Mapping**: Update `input_translator.cpp`
4. **Video Processing**: Modify `video_decoder.cpp`

## License

See LICENSE file in project root.

## Support

For issues and feature requests, see project documentation.
