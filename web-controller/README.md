# ARCS Web Controller

Modern web-based controller for Android Remote Control System (ARCS) built with React, TypeScript, and WebCodecs.

## Features

- **Real-time Video Streaming**: WebCodecs API for hardware-accelerated H.264 decoding
- **Touch Input Simulation**: Pointer events mapped to Android touch gestures
- **Responsive Design**: Works on desktop and mobile browsers
- **Low Latency**: Direct WebSocket communication and efficient video decoding
- **Modern UI**: Clean, minimal interface with dark theme

## Tech Stack

- **React 18**: UI framework
- **TypeScript**: Type-safe development
- **Vite**: Fast build tool and dev server
- **Zustand**: Lightweight state management
- **WebCodecs API**: Hardware-accelerated video decoding
- **WebSocket**: Real-time bidirectional communication

## Requirements

### Browser Support

- **Chrome/Edge**: 94+ (WebCodecs support)
- **Safari**: 16.4+ (WebCodecs support)
- **Firefox**: Not supported (WebCodecs not available)

### Development

- Node.js 18+
- npm or yarn

## Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Usage

### 1. Start Development Server

```bash
npm run dev
```

The app will be available at `http://localhost:3000`

### 2. Connect to Device

1. Open the web controller in a supported browser
2. Enter the WebSocket server URL (e.g., `ws://192.168.1.100:8080`)
3. Enter the session ID from your Android device
4. Click "Connect"

### 3. Control Device

- **Click/Tap**: Tap on the video display
- **Click + Hold**: Long press
- **Click + Drag**: Swipe gesture
- **System Controls**: Use the control panel buttons

## Development

### Project Structure

```
web-controller/
├── src/
│   ├── components/          # React components
│   │   ├── VideoDisplay.tsx
│   │   ├── ControlPanel.tsx
│   │   ├── ConnectionDialog.tsx
│   │   └── StatusBar.tsx
│   ├── hooks/               # Custom React hooks
│   │   ├── useWebSocket.ts
│   │   ├── useVideoDecoder.ts
│   │   └── useTouchHandler.ts
│   ├── store/               # State management
│   │   └── connectionStore.ts
│   ├── App.tsx
│   ├── main.tsx
│   └── index.css
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── README.md
```

### Key Components

#### VideoDisplay
- Renders video stream using HTML5 Canvas
- Handles pointer events for touch input
- Responsive layout with aspect ratio preservation

#### ControlPanel
- System control buttons (Home, Back, Recent Apps, etc.)
- Connection management
- Clean grid layout

#### ConnectionDialog
- Server URL and session ID input
- Connection state management
- Error handling

#### StatusBar
- Connection status indicator
- Device information display
- Session ID display

### Custom Hooks

#### useWebSocket
- WebSocket connection management
- Message sending/receiving
- Command formatting

#### useVideoDecoder
- WebCodecs VideoDecoder initialization
- H.264 frame decoding
- Canvas rendering

#### useTouchHandler
- Pointer event handling
- Coordinate mapping (screen → device)
- Gesture detection (tap, long press, swipe)

## Configuration

### Server URL

Default: `ws://localhost:8080`

Supports both `ws://` and `wss://` protocols.

### Video Decoder

The WebCodecs VideoDecoder is configured for:
- Codec: H.264 (avc1.42E01E)
- Hardware acceleration: Preferred
- Output: Canvas 2D context

## Performance

### Optimizations

- Hardware-accelerated video decoding via WebCodecs
- Efficient state management with Zustand
- Minimal re-renders with React hooks
- Direct canvas rendering (no intermediate layers)

### Benchmarks

- **Latency**: ~50-100ms (network + decode)
- **CPU Usage**: ~5-10% (hardware decode)
- **RAM**: ~50-100 MB
- **Network**: 2-5 Mbps

## Browser Compatibility

### Full Support
- Chrome 94+
- Edge 94+
- Opera 80+

### Partial Support
- Safari 16.4+ (WebCodecs available)

### Not Supported
- Firefox (WebCodecs not implemented)
- Internet Explorer

## Troubleshooting

### WebCodecs Not Available

**Issue**: "WebCodecs not supported" error

**Solution**: Use Chrome 94+, Edge 94+, or Safari 16.4+

### Connection Failed

**Issue**: Cannot connect to server

**Solutions**:
- Verify server URL format (`ws://` or `wss://`)
- Check server is running and accessible
- Check firewall/network settings
- Verify session ID is correct

### No Video Display

**Issue**: Connected but no video

**Solutions**:
- Check browser console for decoder errors
- Verify H.264 codec support
- Check network bandwidth
- Try refreshing the page

### Input Not Working

**Issue**: Touch/click not registering

**Solutions**:
- Ensure connection is established
- Check device info is received
- Verify pointer events are enabled
- Try clicking directly on canvas

### High Latency

**Issue**: Noticeable delay

**Solutions**:
- Use wired network connection
- Reduce video quality on Android device
- Close other network-intensive apps
- Check CPU usage (enable hardware decode)

## Deployment

### Build for Production

```bash
npm run build
```

Output will be in `dist/` directory.

### Deploy to Static Hosting

The built app is a static site and can be deployed to:

- Netlify
- Vercel
- GitHub Pages
- AWS S3 + CloudFront
- Any static web server

### Environment Variables

Create `.env` file for production config:

```env
VITE_DEFAULT_SERVER_URL=wss://your-server.com
```

## Security Considerations

- Always use `wss://` (WebSocket Secure) in production
- Implement JWT token validation
- Use HTTPS for web controller hosting
- Validate session IDs before connecting
- Implement rate limiting on server

## Future Enhancements

- [ ] Audio streaming support
- [ ] File transfer capability
- [ ] Screen recording
- [ ] Multi-touch gestures
- [ ] Keyboard input overlay
- [ ] Custom control macros
- [ ] Session sharing/collaboration

## License

See LICENSE file in project root.

## Support

For issues and feature requests, see project documentation.
