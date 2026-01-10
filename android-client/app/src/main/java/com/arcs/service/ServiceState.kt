package com.arcs.client.service

/**
 * State machine for thread-safe lifecycle management
 */
enum class ServiceState {
    STOPPED,      // Initial state, not running
    CONNECTING,   // WebSocket connecting
    CONNECTED,    // WebSocket connected, waiting for auth
    AUTHENTICATED,// Auth successful, waiting for controller
    STREAMING,    // Screen capture active
    STOPPING      // Cleanup in progress
}
