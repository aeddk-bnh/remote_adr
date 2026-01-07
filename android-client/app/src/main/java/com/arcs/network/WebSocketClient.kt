package com.arcs.client.network

import okhttp3.*
import okio.ByteString
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for server communication
 * Handles connection, reconnection, and message routing
 */
class WebSocketClient(
    private val serverUrl: String,
    private val onMessage: (String) -> Unit,
    private val onBinaryMessage: (ByteArray) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: (code: Int, reason: String) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    
    private var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private var isConnecting = false
    
    init {
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)  // No timeout for persistent connection
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)  // Heartbeat
            .build()
    }
    
    /**
     * Connect to server
     */
    fun connect(jwtToken: String? = null) {
        if (isConnecting || webSocket != null) {
            Timber.w("Already connected or connecting")
            return
        }
        
        isConnecting = true
        
        val requestBuilder = Request.Builder()
            .url(serverUrl)
            .addHeader("Sec-WebSocket-Protocol", "arcs-v1")
        
        // Add JWT token if available
        if (jwtToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer $jwtToken")
        }
        
        val request = requestBuilder.build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                Timber.i("WebSocket connected: ${response.code}")
                onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("Received text message: ${text.take(100)}")
                onMessage(text)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Timber.d("Received binary message: ${bytes.size} bytes")
                onBinaryMessage(bytes.toByteArray())
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.w("WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnecting = false
                this@WebSocketClient.webSocket = null
                Timber.i("WebSocket closed: $code $reason")
                onDisconnected(code, reason)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                this@WebSocketClient.webSocket = null
                Timber.e(t, "WebSocket error: ${response?.code}")
                onError(t)
            }
        })
    }
    
    /**
     * Send text message
     */
    fun sendText(message: String): Boolean {
        return webSocket?.send(message) ?: false.also {
            Timber.e("Cannot send message: not connected")
        }
    }
    
    /**
     * Send binary message
     */
    fun sendBinary(data: ByteArray): Boolean {
        return webSocket?.send(ByteString.of(*data)) ?: false.also {
            Timber.e("Cannot send binary: not connected")
        }
    }
    
    /**
     * Send ping
     */
    fun ping(): Boolean {
        return webSocket?.send(ByteString.of()) ?: false
    }
    
    /**
     * Close connection
     */
    fun disconnect(code: Int = 1000, reason: String = "Normal closure") {
        webSocket?.close(code, reason)
        webSocket = null
        isConnecting = false
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean {
        return webSocket != null && !isConnecting
    }
    
    /**
     * Shutdown client
     */
    fun shutdown() {
        disconnect()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
