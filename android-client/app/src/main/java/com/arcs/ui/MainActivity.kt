package com.arcs.client.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arcs.client.core.PermissionManager
import com.arcs.client.service.RemoteControlService
import timber.log.Timber

/**
 * Main Activity for ARCS Android Client
 * Provides UI for:
 * - Connection status display
 * - Session management
 * - Permission requests
 * - Settings access
 * - Service control
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var permissionManager: PermissionManager
    private val isServiceRunningState = mutableStateOf(false)
    private val sessionIdState = mutableStateOf("")
    private val deviceConnectedState = mutableStateOf(false)

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                RemoteControlService.ACTION_SESSION_CREATED -> {
                    val id = intent.getStringExtra(RemoteControlService.EXTRA_SESSION_ID) ?: ""
                    Timber.i("Received session_created broadcast: %s", id)
                    sessionIdState.value = id
                    try {
                        Toast.makeText(this@MainActivity, "Session ID: $id", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to show session toast")
                    }
                }
                RemoteControlService.ACTION_CONTROLLER_CONNECTED -> {
                    Timber.i("Received controller_connected broadcast")
                    deviceConnectedState.value = true
                }
                RemoteControlService.ACTION_REQUEST_PROJECTION -> {
                    Timber.i("Received request to re-acquire MediaProjection from service")
                    // Trigger the MediaProjection request flow in the Activity
                    requestMediaProjection()
                }
            }
        }
    }
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Timber.i("MediaProjection granted, starting service")
            startServiceWithProjection(result.resultCode, result.data!!)
        } else {
            Timber.w("MediaProjection denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        permissionManager = PermissionManager(this)
        
        setContent {
            ARCSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        permissionManager = permissionManager,
                        onNavigateToSettings = { navigateToSettings() },
                        isServiceRunningState = isServiceRunningState,
                        sessionIdState = sessionIdState,
                        deviceConnectedState = deviceConnectedState
                    )
                }
            }
        }
        
        // Check permissions on startup
        checkAndRequestPermissions()
        // Register receiver for session updates
        registerReceiver(
            sessionReceiver,
            IntentFilter(RemoteControlService.ACTION_SESSION_CREATED).apply {
                addAction(RemoteControlService.ACTION_CONTROLLER_CONNECTED)
            }
        )
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(sessionReceiver)
        } catch (e: Exception) {
            Timber.w(e, "Error unregistering sessionReceiver")
        }
        super.onDestroy()
    }
    
    private fun checkAndRequestPermissions() {
        if (!permissionManager.hasAllRequiredPermissions()) {
            Timber.w("Missing required permissions")
            // Permissions will be requested through UI
        }
    }
    
    private fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
    
    internal fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }
    
    private fun startServiceWithProjection(resultCode: Int, data: Intent) {
            val intent = Intent(this, RemoteControlService::class.java).apply {
            action = RemoteControlService.ACTION_START
            putExtra(RemoteControlService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RemoteControlService.EXTRA_RESULT_DATA, data)
            // Use localhost so `adb reverse tcp:8080 tcp:8080` works during USB testing
            putExtra(RemoteControlService.EXTRA_SERVER_URL, "ws://127.0.0.1:8080") // TODO: get from settings
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handlePermissionResult(requestCode, grantResults)
    }
}

@Composable
fun MainScreen(
    permissionManager: PermissionManager,
    onNavigateToSettings: () -> Unit,
    isServiceRunningState: androidx.compose.runtime.MutableState<Boolean>,
    sessionIdState: androidx.compose.runtime.MutableState<String>,
    deviceConnectedState: androidx.compose.runtime.MutableState<Boolean>
) {
    val context = LocalContext.current
    var isServiceRunning by isServiceRunningState
    var sessionId by sessionIdState
    var deviceConnected by deviceConnectedState
    var hasPermissions by remember { mutableStateOf(permissionManager.hasAllRequiredPermissions()) }

    // Refresh permission state when the activity resumes (e.g. after user enabled Accessibility/IME in Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermissions = permissionManager.hasAllRequiredPermissions()
                Timber.d("MainScreen.onResume: hasPermissions=%s, missing=%s",
                    hasPermissions,
                    permissionManager.checkAllPermissions().getMissingPermissions().joinToString(", ")
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ARCS Remote Control") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_preferences),
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isServiceRunning) "Running" else "Stopped",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Icon(
                            painter = painterResource(
                                if (isServiceRunning) android.R.drawable.presence_online
                                else android.R.drawable.presence_offline
                            ),
                            contentDescription = null,
                            tint = if (isServiceRunning) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    
                    if (sessionId.isNotEmpty()) {
                        Divider()
                        Text(
                            text = "Session ID:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = sessionId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    
                    if (deviceConnected) {
                        Divider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.stat_sys_data_bluetooth),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Controller Connected",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // Permissions Status Card
            if (!hasPermissions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠️ Permissions Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Text(
                            text = "Some permissions are missing. Please grant all required permissions to enable remote control.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Button(
                            onClick = {
                                permissionManager.requestAllPermissions(context as ComponentActivity)
                                hasPermissions = permissionManager.hasAllRequiredPermissions()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Control Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isServiceRunning) {
                            RemoteControlService.stop(context)
                            isServiceRunning = false
                            sessionId = ""
                            deviceConnected = false
                        } else {
                            if (hasPermissions) {
                                // Request MediaProjection permission first
                                (context as? MainActivity)?.requestMediaProjection()
                                isServiceRunning = true
                                // sessionId will be populated when the server replies with session_created
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        painter = painterResource(
                            if (isServiceRunning) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
                        ),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isServiceRunning) "Stop Service" else "Start Service")
                }
                
                OutlinedButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_preferences),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Settings")
                }
            }
            
            // Info Text
            Text(
                text = "Start the service to enable remote control. Share the session ID with your controller application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(onClick = {
                                // Manual refresh in case lifecycle observer didn't update
                                hasPermissions = permissionManager.hasAllRequiredPermissions()
                            }) {
                                Text("Refresh Permissions")
                            }

                            OutlinedButton(onClick = {
                                // Guide user to settings again
                                permissionManager.requestAllPermissions(context as ComponentActivity)
                            }) {
                                Text("Open Settings")
                            }
                        }
        }
    }
}

@Composable
fun ARCSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
            background = androidx.compose.ui.graphics.Color(0xFF121212),
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
        ),
        content = content
    )
}
