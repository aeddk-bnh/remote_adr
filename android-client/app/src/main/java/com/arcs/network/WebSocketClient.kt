package com.arcs.client.network

import android.util.Base64
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

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
    private val onError: (Throwable) -> Unit,
    private val secureChannel: SecureChannel? = null,
    private val sessionKey: SecretKey? = null
) {
    companion object {
        private const val TAG = "WebSocketClient"
    }
    
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
            Log.w(TAG, "Already connected or connecting")
            return
        }
        
        isConnecting = true
        Log.i(TAG, "Connecting to $serverUrl jwt=${jwtToken != null}")
        Timber.i("Connecting to %s jwt=%s", serverUrl, jwtToken != null)
        
        val requestBuilder = Request.Builder()
            .url(serverUrl)
            .addHeader("Sec-WebSocket-Protocol", "arcs-v1")
        
        // Add JWT token if available
        if (jwtToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer $jwtToken")
        }
        
        val request = requestBuilder.build()
        
        try {
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                    Timber.i("WebSocket connected: ${response.code}")
                    Log.i(TAG, "WebSocket connected: ${response.code}")
                    onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("Received text message: ${text.take(100)}")
                
                // Decrypt if encrypted
                val decryptedText = try {
                    if (secureChannel != null && sessionKey != null) {
                        val json = JSONObject(text)
                        if (json.optBoolean("encrypted", false)) {
                            val encryptedPayload = json.getString("payload")
                            val encryptedBytes = Base64.decode(encryptedPayload, Base64.NO_WRAP)
                            val decrypted = secureChannel.decrypt(encryptedBytes, sessionKey)
                            String(decrypted)
                        } else {
                            text
                        }
                    } else {
                        text
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decrypt message")
                    text
                }
                
                onMessage(decryptedText)
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
                Log.i(TAG, "WebSocket closed: $code $reason")
                onDisconnected(code, reason)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                this@WebSocketClient.webSocket = null
                Timber.e(t, "WebSocket error: ${response?.code}")
                Log.e(TAG, "WebSocket error: ${response?.code} -> ${t.message}")
                onError(t)
            }
        })
        } catch (t: Throwable) {
            isConnecting = false
            Timber.e(t, "Failed to start WebSocket")
            Log.e(TAG, "Failed to start WebSocket: ${t.message}")
            onError(t)
        }
    }
    
    /**
     * Send text message (with optional encryption)
     */
    fun sendText(message: String): Boolean {
        val payload = try {
            if (secureChannel != null && sessionKey != null) {
                // Encrypt message
                val encrypted = secureChannel.encrypt(message.toByteArray(), sessionKey)
                val encryptedB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                
                // Wrap in JSON with encrypted flag
                JSONObject().apply {
                    put("encrypted", true)
                    put("payload", encryptedB64)
                }.toString()
            } else {
                message
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt message")
            return false
        }
        
        val ok = webSocket?.send(payload) ?: false
        if (!ok) {
            Timber.e("Cannot send message: not connected")
            Log.w(TAG, "Cannot send message: not connected")
        } else {
            Timber.d("Sent message (encrypted=${secureChannel != null})")
        }
        return ok
    }
    
    /**
     * Send binary message
     */
    fun sendBinary(data: ByteArray): Boolean {
        val ok = webSocket?.send(ByteString.of(*data)) ?: false
        if (!ok) {
            Timber.e("Cannot send binary: not connected")
            Log.w(TAG, "Cannot send binary: not connected")
        }
        return ok
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
