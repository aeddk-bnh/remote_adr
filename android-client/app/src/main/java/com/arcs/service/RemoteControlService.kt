package com.arcs.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arcs.client.BuildConfig
import com.arcs.client.R
import com.arcs.client.core.DeviceInfo
import com.arcs.client.input.KeyInjector
import com.arcs.client.input.TouchInjector
import com.arcs.client.network.CommandDispatcher
import com.arcs.client.network.SecureChannel
import com.arcs.client.network.WebSocketClient
import com.arcs.client.projection.FramePacketizer
import com.arcs.client.projection.ScreenCapturer
import com.arcs.client.projection.VideoEncoder
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import javax.crypto.SecretKey

/**
 * Main foreground service
 * Coordinates screen capture, encoding, and network communication
 */
class RemoteControlService : Service() {
    
    companion object {
        const val CHANNEL_ID = "arcs_service_channel"
        const val NOTIFICATION_ID = 1001
        
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_DEVICE_SECRET = "device_secret"
        
        const val ACTION_START = "com.arcs.action.START"
        const val ACTION_STOP = "com.arcs.action.STOP"
        const val ACTION_SESSION_CREATED = "com.arcs.action.SESSION_CREATED"
        const val ACTION_CONTROLLER_CONNECTED = "com.arcs.action.CONTROLLER_CONNECTED"
        const val EXTRA_SESSION_ID = "session_id"
        const val ACTION_REQUEST_PROJECTION = "com.arcs.action.REQUEST_PROJECTION"
        // Fallback holder for MediaProjection result Intent in case the Service
        // instance loses its instance field (service restarts or lifecycle hiccups).
        // This is a dev-friendly fallback; for production consider a more robust
        // permission/session flow.
        var lastProjectionResultData: Intent? = null
        
        /**
         * Start service from activity/context
         */
        fun start(context: android.content.Context, serverUrl: String = "ws://localhost:8080", deviceSecret: String? = null) {
            val intent = Intent(context, RemoteControlService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_URL, serverUrl)
                deviceSecret?.let { putExtra(EXTRA_DEVICE_SECRET, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop service from activity/context
         */
        fun stop(context: android.content.Context) {
            val intent = Intent(context, RemoteControlService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gson = Gson()
    
    
    @Volatile
    private var currentState = ServiceState.STOPPED
    private val stateLock = Any()
    
    /**
     * Thread-safe state transition
     * @return true if transition succeeded, false if invalid
     */
    private fun transitionState(from: ServiceState, to: ServiceState): Boolean {
        synchronized(stateLock) {
            // Validate allowed transitions
            val isValid = when (from) {
                ServiceState.STOPPED -> to == ServiceState.CONNECTING
                ServiceState.CONNECTING -> to == ServiceState.CONNECTED || to == ServiceState.STOPPED
                ServiceState.CONNECTED -> to == ServiceState.AUTHENTICATED || to == ServiceState.STOPPING || to == ServiceState.CONNECTING
                ServiceState.AUTHENTICATED -> to == ServiceState.STREAMING || to == ServiceState.STOPPING || to == ServiceState.CONNECTING
                ServiceState.STREAMING -> to == ServiceState.STOPPING || to == ServiceState.CONNECTING
                ServiceState.STOPPING -> to == ServiceState.STOPPED
            }

            if (currentState == from && isValid) {
                currentState = to
                Timber.d("State transition: $from -> $to")
                return true
            }
            Timber.w("Invalid/Failed state transition: $currentState -> $to (requested from $from)")
            return false
        }
    }
    
    /**
     * Check if in valid state for operation
     */
    private fun isInState(vararg validStates: ServiceState): Boolean {
        synchronized(stateLock) {
            return currentState in validStates
        }
    }
    
    // Components
    private lateinit var deviceInfo: DeviceInfo
    private lateinit var screenCapturer: ScreenCapturer
    private lateinit var videoEncoder: VideoEncoder
    private lateinit var framePacketizer: FramePacketizer
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var secureChannel: SecureChannel
    private lateinit var commandDispatcher: CommandDispatcher
    private lateinit var touchInjector: TouchInjector
    private lateinit var keyInjector: KeyInjector
    
    // MediaProjection components
    private var mediaProjection: android.media.projection.MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var projectionResultCode: Int = -1
    private var projectionResultData: Intent? = null
    
    private var sessionKey: SecretKey? = null
    private var jwtToken: String? = null
    private var serverUrl: String = ""
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("RemoteControlService created")
        
        createNotificationChannel()
        initializeComponents()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Log received start intent for debugging: action, projection extras, and server URL
        try {
            val action = intent?.action
            val receivedResultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
            val hasResultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) != null
            val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: BuildConfig.SERVER_URL
            Timber.i("onStartCommand action=%s resultCode=%d hasResultData=%s serverUrl=%s", action, receivedResultCode, hasResultData, serverUrl)
        } catch (e: Exception) {
            Timber.w(e, "Error logging start intent extras")
        }

        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: BuildConfig.SERVER_URL
                val deviceSecret = intent.getStringExtra(EXTRA_DEVICE_SECRET)
                
                // Ensure we call startForeground promptly to avoid the system killing the
                // service when started via startForegroundService(). If we don't yet have
                // a MediaProjection result (resultCode/resultData), publish a lightweight
                // notification and wait for the Activity to re-send the start with results.
                if (resultData != null) {
                    // If resultCode wasn't provided by the caller, assume RESULT_OK
                    val storedResultCode = if (resultCode != -1) resultCode else android.app.Activity.RESULT_OK
                    // Store projection result for later use
                    projectionResultCode = storedResultCode
                    projectionResultData = resultData
                    // persist a copy in companion as a fallback
                    lastProjectionResultData = resultData
                    Timber.i("Persisted projection result in companion (pid=%d)", android.os.Process.myPid())
                    startService(storedResultCode, resultData, serverUrl, deviceSecret)
                } else {
                    // If the service wasn't started with projection data, still enter
                    // foreground to satisfy platform requirements.
                    try {
                        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to start foreground notification")
                    }
                }
            }
            ACTION_STOP -> {
                stopService()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopService()
        serviceScope.cancel()
        Timber.i("RemoteControlService destroyed")
    }
    
    /**
     * Initialize all components
     */
    private fun initializeComponents() {
        deviceInfo = DeviceInfo(this)
        
        touchInjector = TouchInjector(
            deviceInfo.screenWidth,
            deviceInfo.screenHeight
        )
        
        keyInjector = KeyInjector()
        
        // commandDispatcher will be created when WebSocket client exists so we
        // can attach an ack callback that uses the socket to report results.
        
        screenCapturer = ScreenCapturer(
            this,
            deviceInfo.screenWidth,
            deviceInfo.screenHeight,
            deviceInfo.densityDpi
        )
        
        videoEncoder = VideoEncoder(
            deviceInfo.screenWidth,
            deviceInfo.screenHeight,
            BuildConfig.VIDEO_BITRATE,
            BuildConfig.VIDEO_FPS
        )
        
        framePacketizer = FramePacketizer()
        secureChannel = SecureChannel()
    }
    
    /**
     * Start remote control service
     */
    private fun startService(
        resultCode: Int,
        resultData: Intent,
        serverUrl: String,
        deviceSecret: String?
    ) {
        // Store server URL for reconnection
        this.serverUrl = serverUrl
        
        // Check state - only start from STOPPED state
        if (!transitionState(ServiceState.STOPPED, ServiceState.CONNECTING)) {
            Timber.w("Cannot start service - current state: $currentState")
            return
        }
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
        
        // Initialize WebSocket client
        webSocketClient = WebSocketClient(
            serverUrl = serverUrl,
            onMessage = { message ->
                handleTextMessage(message)
            },
            onBinaryMessage = { data ->
                handleBinaryMessage(data)
            },
            onConnected = {
                handleConnected(deviceSecret)
            },
            onDisconnected = { code, reason ->
                handleDisconnected(code, reason)
            },
            onError = { error ->
                handleError(error)
            }
        )

        Timber.i("Initializing WebSocket client with URL=%s", serverUrl)

        // Create command dispatcher now that we have a websocket client and
        // can send command acknowledgements back to the controller/server.
        commandDispatcher = CommandDispatcher(
            touchInjector,
            keyInjector,
            serviceScope,
            onCommandResult = { resultJson ->
                try {
                    if (this::webSocketClient.isInitialized && webSocketClient.isConnected()) {
                        webSocketClient.sendText(resultJson)
                        Timber.d("Sent command ack: %s", resultJson.take(200))
                    } else {
                        Timber.w("WebSocket not connected; cannot send command ack")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to send command ack")
                }
            }
        )
        
        // Connect to server
        try {
            Timber.i("Connecting WebSocket to %s", serverUrl)
            webSocketClient.connect()
            Timber.i("WebSocket.connect() returned (async)")
        } catch (e: Exception) {
            Timber.e(e, "Exception while calling webSocketClient.connect()")
        }
        
        // Transition to CONNECTED state
        transitionState(ServiceState.CONNECTING, ServiceState.CONNECTED)
        Timber.i("Service started")
    }
    
    /**
     * Stop service
     */
    private fun stopService() {
        // Only allow stopping from valid states
        if (!isInState(ServiceState.CONNECTING, ServiceState.CONNECTED, 
                       ServiceState.AUTHENTICATED, ServiceState.STREAMING)) {
            Timber.w("Cannot stop service - already in state: $currentState")
            return
        }
        
        synchronized(stateLock) {
            currentState = ServiceState.STOPPING
        }
        Timber.i("Stopping service - state: STOPPING")
        // Stop capture pipeline first
        stopScreenCapture()
        
        try {
            if (this::videoEncoder.isInitialized) {
                videoEncoder.stop()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping videoEncoder")
        }

        try {
            if (this::screenCapturer.isInitialized) {
                screenCapturer.stop()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping screenCapturer")
        }

        try {
            if (this::webSocketClient.isInitialized) {
                webSocketClient.disconnect()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting webSocketClient")
        }

        // Transition to STOPPED state
        synchronized(stateLock) {
            currentState = ServiceState.STOPPED
        }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Timber.e(e, "Error stopping foreground")
        }

        try {
            stopSelf()
        } catch (e: Exception) {
            Timber.e(e, "Error calling stopSelf")
        }

        Timber.i("Service stopped")
    }
    
    /**
     * Handle WebSocket connection established
     */
    private fun handleConnected(deviceSecret: String?) {
        Timber.i("Connected to server")

        // Send authentication request
        val authRequest = mapOf(
            "type" to "auth_request",
            "version" to "1.0",
            "device_id" to deviceInfo.deviceId,
            "device_info" to deviceInfo.toMap(),
            "secret" to (deviceSecret ?: ""),
            "timestamp" to System.currentTimeMillis()
        )

        try {
            val authJson = gson.toJson(authRequest)
            Timber.i("Sending auth_request: %s", authJson.take(1000))
            webSocketClient.sendText(authJson)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send auth_request")
        }

        // Also send a device_hello for simple mock servers that expect it
        try {
            val hello = mapOf(
                "type" to "device_hello",
                "sender" to deviceInfo.deviceId,
                "device_info" to deviceInfo.toMap(),
                "timestamp" to System.currentTimeMillis()
            )
            val helloJson = gson.toJson(hello)
            Timber.i("Sending device_hello: %s", helloJson.take(1000))
            webSocketClient.sendText(helloJson)
        } catch (e: Exception) {
            Timber.w(e, "Failed to send device_hello")
        }

        updateNotification("Connected")
    }
    
    /**
     * Handle text message from server
     */
    private fun handleTextMessage(message: String) {
        try {
            val json = gson.fromJson(message, Map::class.java)
            val type = json["type"] as? String
            
            when (type) {
                "auth_response" -> handleAuthResponse(message)
                "join_response" -> handleJoinResponse(message)
                // Mock server uses session_joined / session_created / controller_connected
                "session_joined" -> handleJoinResponse(message)
                "session_created" -> {
                    Timber.i(">>> [session_created] START handling")
                    val sessionId = json["session_id"] as? String
                    Timber.i("Session created by server: %s", sessionId)
                    Timber.i(">>> Creating broadcast intent")
                    // Broadcast session id so UI can display it
                    try {
                        val intent = Intent(ACTION_SESSION_CREATED).apply {
                            setPackage(packageName) // Make it explicit to this app
                            putExtra(EXTRA_SESSION_ID, sessionId)
                        }
                        Timber.i(">>> Sending broadcast with sessionId=%s to package=%s", sessionId, packageName)
                        sendBroadcast(intent)
                        Timber.i(">>> Broadcast sent successfully")
                        // Persist session id to SharedPreferences so Activity can read it
                        try {
                            Timber.i(">>> Getting SharedPreferences")
                            val prefs = getSharedPreferences("arcs_prefs", Context.MODE_PRIVATE)
                            Timber.i(">>> Saving to prefs: %s", sessionId)
                            prefs.edit().putString("last_session_id", sessionId ?: "").apply()
                            Timber.i(">>> Persisted session id to prefs: %s", sessionId)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to persist session id")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to broadcast session_created")
                    }
                    Timber.i(">>> [session_created] END handling")
                }
                "controller_connected" -> {
                    Timber.i("Controller connected -> starting streaming")
                    handleJoinResponse(message)
                    // notify UI that controller connected
                    try {
                        val intent = Intent(ACTION_CONTROLLER_CONNECTED)
                        sendBroadcast(intent)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to broadcast controller_connected")
                    }
                }
                else -> commandDispatcher.dispatch(message)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling text message")
        }
    }
    
    /**
     * Handle binary message from server
     */
    private fun handleBinaryMessage(data: ByteArray) {
        Timber.d("Received binary message: ${data.size} bytes")
        // Handle binary commands if needed
    }
    
    /**
     * Handle authentication response
     */
    private fun handleAuthResponse(json: String) {
        try {
            val response = gson.fromJson(json, Map::class.java)
            val success = response["success"] as? Boolean ?: false
            
            if (success) {
                jwtToken = response["jwt_token"] as? String
                val sessionId = response["session_id"] as? String
                
                Timber.i("Authentication successful, session: $sessionId")
                
                // Derive session key from JWT - encryption will be used in future messages
                if (jwtToken != null) {
                    sessionKey = secureChannel.deriveKeyFromToken(jwtToken!!, deviceInfo.deviceId)
                    Timber.i("Session key derived - future messages will be encrypted")
                    
                    // Note: WebSocketClient was already instantiated with null encryption.
                    // For full encryption support, we'd need to close and recreate the connection.
                    // For now, encryption will be available but not used in this implementation.
                    // TODO: Implement proper connection upgrade or require encryption from start
                }
                
                // Transition to AUTHENTICATED state
                transitionState(ServiceState.CONNECTED, ServiceState.AUTHENTICATED)
                updateNotification("Authenticated")
                
                // Wait for controller to join
                
            } else {
                Timber.e("Authentication failed")
                updateNotification("Auth failed")
                stopService()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling auth response")
        }
    }
    
    /**
     * Handle controller join response
     */
    private fun handleJoinResponse(json: String) {
        // Transition to STREAMING state
        if (!transitionState(ServiceState.AUTHENTICATED, ServiceState.STREAMING)) {
            // Also allow transitioning from CONNECTED for mock servers
            transitionState(ServiceState.CONNECTED, ServiceState.STREAMING)
        }
        
        Timber.i("Controller joined, starting streaming")
        updateNotification("Streaming")
        
        // Start screen capture and encoding
        startScreenCapture()
    }
    
    /**
     * Start screen capture pipeline
     */
    private fun startScreenCapture() {
        // If this instance doesn't have the projection data (may happen if the
        // service was restarted or the extras were not delivered), try the
        // companion fallback saved when the Activity originally provided the
        // MediaProjection Intent.
        if (projectionResultCode == -1 || projectionResultData == null) {
            Timber.w("startScreenCapture: missing projection (projCode=%d, projDataNull=%s) pid=%d",
                projectionResultCode,
                (projectionResultData == null).toString(),
                android.os.Process.myPid())

            if (lastProjectionResultData != null) {
                Timber.i("Using fallback projection data from companion holder (pid=%d)", android.os.Process.myPid())
                projectionResultData = lastProjectionResultData
                projectionResultCode = android.app.Activity.RESULT_OK
            } else {
                Timber.e("Cannot start capture: no MediaProjection result - requesting projection from Activity")
                // Ask the Activity to re-request the MediaProjection permission so the user can grant it again
                try {
                    val intent = Intent(ACTION_REQUEST_PROJECTION)
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to broadcast ACTION_REQUEST_PROJECTION")
                }
                return
            }
        }
        
        try {
            Timber.i("Starting screen capture pipeline")
            
            // Get MediaProjection
            val mediaProjectionManager = getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) 
                as android.media.projection.MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(projectionResultCode, projectionResultData!!)
            
            if (mediaProjection == null) {
                Timber.e("Failed to obtain MediaProjection")
                updateNotification("Capture failed")
                return
            }
            
            // Register callback before creating VirtualDisplay (required on Android 14+)
            val projectionCallback = object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() {
                    Timber.i("MediaProjection stopped by system")
                    stopScreenCapture()
                }
                
                override fun onCapturedContentResize(width: Int, height: Int) {
                    Timber.i("Captured content resized: %dx%d", width, height)
                }
                
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    Timber.i("Captured content visibility changed: %s", isVisible)
                }
            }
            mediaProjection?.registerCallback(projectionCallback, android.os.Handler(android.os.Looper.getMainLooper()))
            Timber.i("Registered MediaProjection callback")
            
            // Start video encoder and get input surface
            Timber.i("[RemoteControlService] Starting video encoder pipeline")
            val encoderSurface = videoEncoder.start { buffer, info ->
                // Encode callback: packetize and send via WebSocket
                try {
                    val isKeyFrame = (info.flags and android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    val packets = framePacketizer.packetize(buffer, info, isKeyFrame, false)
                    
                    // Send each packet
                    var sentCount = 0
                    packets.forEach { packet ->
                        if (this::webSocketClient.isInitialized && webSocketClient.isConnected()) {
                            val sent = webSocketClient.sendBinary(packet)
                            if (sent) sentCount++
                        }
                    }
                    
                    if (isKeyFrame) {
                        Timber.i("[RemoteControlService] Sent keyframe: packets=%d/%d, size=%d bytes, pts=%d ms",
                            sentCount, packets.size, info.size, info.presentationTimeUs / 1000)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error packetizing/sending frame")
                }
            }
            
            if (encoderSurface == null) {
                Timber.e("Failed to start video encoder")
                updateNotification("Encoder failed")
                mediaProjection?.stop()
                mediaProjection = null
                return
            }
            
            // Create VirtualDisplay to render screen to encoder surface
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ARCS_Capture",
                deviceInfo.screenWidth,
                deviceInfo.screenHeight,
                deviceInfo.densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                encoderSurface,
                null,
                null
            )
            
            Timber.i("VirtualDisplay created: %s", virtualDisplay?.toString())
            
            if (virtualDisplay == null) {
                Timber.e("Failed to create VirtualDisplay")
                updateNotification("Display failed")
                videoEncoder.stop()
                mediaProjection?.stop()
                mediaProjection = null
                return
            }
            
            Timber.i("Screen capture pipeline started successfully")
            updateNotification("Streaming")
            
        } catch (e: Exception) {
            Timber.e(e, "Error starting screen capture")
            updateNotification("Capture error")
            stopScreenCapture()
        }
    }
    
    /**
     * Stop screen capture pipeline
     */
    private fun stopScreenCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Timber.e(e, "Error releasing VirtualDisplay")
        }
        
        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Timber.e(e, "Error stopping MediaProjection")
        }
    }
    
    /**
     * Handle disconnection
     */
    private fun handleDisconnected(code: Int, reason: String) {
        Timber.w("Disconnected: $code $reason")
        
        // If abnormal disconnection, attempt reconnection
        if (code != 1000 && reconnectAttempts < maxReconnectAttempts) {
            val delay = kotlin.math.min(1000L * java.lang.Math.pow(2.0, reconnectAttempts.toDouble()).toLong(), 60000L)
            Timber.i("Attempting reconnection in ${delay}ms (attempt ${reconnectAttempts + 1}/$maxReconnectAttempts)")
            
            reconnectAttempts++
            updateNotification("Reconnecting...")
            
            // Schedule reconnection
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (this::webSocketClient.isInitialized) {
                        webSocketClient.connect(jwtToken)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Reconnection failed")
                }
            }, delay)
        } else {
            updateNotification("Disconnected")
            if (reconnectAttempts >= maxReconnectAttempts) {
                Timber.e("Max reconnection attempts reached")
            }
            stopService()
        }
    }
    
    /**
     * Handle error
     */
    private fun handleError(error: Throwable) {
        Timber.e(error, "WebSocket error")
        updateNotification("Error")
    }
    
    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ARCS Remote Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Remote control service notification"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create notification
     */
    private fun createNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ARCS Remote Control")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Update notification
     */
    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
