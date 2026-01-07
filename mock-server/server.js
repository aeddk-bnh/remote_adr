#!/usr/bin/env node

/**
 * ARCS Mock Server - Node.js Implementation
 * For testing when C++ server cannot be built
 */

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const crypto = require('crypto');

const PORT = 8080;
const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// Session storage
const sessions = new Map(); // sessionId -> { device, controllers: [] }
const devices = new Map();   // ws -> { sessionId, deviceInfo }
const controllers = new Map(); // ws -> { sessionId }

// Express middleware
app.use(express.json());

// Health check
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        sessions: sessions.size,
        timestamp: new Date().toISOString()
    });
});

// Device registration (simplified - no auth for testing)
app.post('/api/devices/register', (req, res) => {
    const deviceId = crypto.randomUUID();
    res.json({
        success: true,
        deviceId: deviceId,
        token: 'mock-token-' + deviceId.substring(0, 8)
    });
});

// WebSocket handling
wss.on('connection', (ws, req) => {
    console.log('[WebSocket] New connection from', req.socket.remoteAddress);

    ws.on('message', (data) => {
        try {
            // Try to parse as JSON (control messages)
            const message = JSON.parse(data.toString());
            handleJsonMessage(ws, message);
        } catch (e) {
            // Binary data (video frames)
            handleBinaryMessage(ws, data);
        }
    });

    ws.on('close', () => {
        handleDisconnect(ws);
    });

    ws.on('error', (error) => {
        console.error('[WebSocket] Error:', error.message);
    });
});

function handleJsonMessage(ws, message) {
    console.log('[JSON Message]', message.type, 'from', message.sender);

    switch (message.type) {
        case 'device_hello':
            handleDeviceHello(ws, message);
            break;

        case 'join_session':
            handleJoinSession(ws, message);
            break;

        case 'touch':
        case 'key':
        case 'system':
        case 'app_launch':
        case 'macro_execute':
        case 'ai_click_text':
            // Forward command from controller to device
            forwardToDevice(ws, message);
            break;

        case 'pong':
            // Heartbeat response
            break;

        default:
            console.warn('[Unknown message type]', message.type);
    }
}

function handleBinaryMessage(ws, data) {
    // Video frame from device - forward to all controllers in same session
    const deviceInfo = devices.get(ws);
    if (!deviceInfo) return;

    const session = sessions.get(deviceInfo.sessionId);
    if (!session) return;

    // Forward to all controllers
    session.controllers.forEach(controllerWs => {
        if (controllerWs.readyState === WebSocket.OPEN) {
            controllerWs.send(data);
        }
    });
}

function handleDeviceHello(ws, message) {
    const sessionId = crypto.randomUUID().substring(0, 8).toUpperCase();

    // Store device
    devices.set(ws, {
        sessionId,
        deviceInfo: message.device_info
    });

    // Create session
    sessions.set(sessionId, {
        device: ws,
        controllers: [],
        deviceInfo: message.device_info,
        createdAt: Date.now()
    });

    console.log(`[Device] Registered with session ID: ${sessionId}`);

    // Send response
    ws.send(JSON.stringify({
        type: 'session_created',
        session_id: sessionId,
        server_time: Date.now()
    }));

    // Start heartbeat
    const interval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'ping', timestamp: Date.now() }));
        } else {
            clearInterval(interval);
        }
    }, 30000);
}

function handleJoinSession(ws, message) {
    const sessionId = message.session_id;
    const session = sessions.get(sessionId);

    if (!session) {
        ws.send(JSON.stringify({
            type: 'error',
            code: 'SESSION_NOT_FOUND',
            message: 'Invalid session ID'
        }));
        return;
    }

    // Add controller to session
    controllers.set(ws, { sessionId });
    session.controllers.push(ws);

    console.log(`[Controller] Joined session: ${sessionId}`);

    // Send success response
    ws.send(JSON.stringify({
        type: 'session_joined',
        session_id: sessionId,
        device_info: session.deviceInfo
    }));

    // Notify device
    if (session.device.readyState === WebSocket.OPEN) {
        session.device.send(JSON.stringify({
            type: 'controller_connected',
            timestamp: Date.now()
        }));
    }
}

function forwardToDevice(controllerWs, message) {
    const controllerInfo = controllers.get(controllerWs);
    if (!controllerInfo) return;

    const session = sessions.get(controllerInfo.sessionId);
    if (!session || !session.device) return;

    // Forward to device
    if (session.device.readyState === WebSocket.OPEN) {
        session.device.send(JSON.stringify(message));
        console.log(`[Forward] ${message.type} -> device`);
    }
}

function handleDisconnect(ws) {
    // Check if device
    const deviceInfo = devices.get(ws);
    if (deviceInfo) {
        console.log(`[Device] Disconnected from session: ${deviceInfo.sessionId}`);
        
        // Notify all controllers
        const session = sessions.get(deviceInfo.sessionId);
        if (session) {
            session.controllers.forEach(controllerWs => {
                if (controllerWs.readyState === WebSocket.OPEN) {
                    controllerWs.send(JSON.stringify({
                        type: 'device_disconnected'
                    }));
                }
            });
            sessions.delete(deviceInfo.sessionId);
        }
        devices.delete(ws);
        return;
    }

    // Check if controller
    const controllerInfo = controllers.get(ws);
    if (controllerInfo) {
        console.log(`[Controller] Disconnected from session: ${controllerInfo.sessionId}`);
        
        const session = sessions.get(controllerInfo.sessionId);
        if (session) {
            session.controllers = session.controllers.filter(c => c !== ws);
            
            // Notify device
            if (session.device.readyState === WebSocket.OPEN) {
                session.device.send(JSON.stringify({
                    type: 'controller_disconnected'
                }));
            }
        }
        controllers.delete(ws);
    }
}

// Start server
server.listen(PORT, '0.0.0.0', () => {
    console.log('='.repeat(70));
    console.log('ARCS Mock Server (Node.js)');
    console.log('='.repeat(70));
    console.log(`HTTP:      http://0.0.0.0:${PORT}`);
    console.log(`WebSocket: ws://0.0.0.0:${PORT}`);
    console.log(`Health:    http://localhost:${PORT}/health`);
    console.log('='.repeat(70));
    console.log('Ready for connections...\n');
});
