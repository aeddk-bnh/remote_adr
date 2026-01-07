package com.arcs.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
    
    private var sessionKey: SecretKey? = null
    private var jwtToken: String? = null
    private var isRunning = false
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("RemoteControlService created")
        
        createNotificationChannel()
        initializeComponents()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: BuildConfig.SERVER_URL
                val deviceSecret = intent.getStringExtra(EXTRA_DEVICE_SECRET)
                
                if (resultCode != -1 && resultData != null) {
                    startService(resultCode, resultData, serverUrl, deviceSecret)
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
        
        commandDispatcher = CommandDispatcher(
            touchInjector,
            keyInjector,
            serviceScope
        )
        
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
        if (isRunning) {
            Timber.w("Service already running")
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
        
        // Connect to server
        webSocketClient.connect()
        isRunning = true
        
        Timber.i("Service started")
    }
    
    /**
     * Stop service
     */
    private fun stopService() {
        if (!isRunning) return
        
        videoEncoder.stop()
        screenCapturer.stop()
        webSocketClient.disconnect()
        
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
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
        
        webSocketClient.sendText(gson.toJson(authRequest))
        
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
                
                // Derive session key
                if (jwtToken != null) {
                    sessionKey = secureChannel.deriveKeyFromToken(jwtToken!!, deviceInfo.deviceId)
                }
                
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
        Timber.i("Controller joined, starting streaming")
        updateNotification("Streaming")
        
        // Start screen capture and encoding
        startScreenCapture()
    }
    
    /**
     * Start screen capture pipeline
     */
    private fun startScreenCapture() {
        // TODO: Get MediaProjection result from Activity
        // For now, this is a placeholder
        Timber.i("Screen capture pipeline ready")
    }
    
    /**
     * Handle disconnection
     */
    private fun handleDisconnected(code: Int, reason: String) {
        Timber.w("Disconnected: $code $reason")
        updateNotification("Disconnected")
        stopService()
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
