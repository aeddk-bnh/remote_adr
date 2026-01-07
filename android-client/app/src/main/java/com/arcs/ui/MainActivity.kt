package com.arcs.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                        onNavigateToSettings = { navigateToSettings() }
                    )
                }
            }
        }
        
        // Check permissions on startup
        checkAndRequestPermissions()
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
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf("") }
    var deviceConnected by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(permissionManager.hasAllRequiredPermissions()) }
    
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
                                com.arcs.client.service.RemoteControlService.start(context)
                                isServiceRunning = true
                                // In real implementation, get session ID from service
                                sessionId = "DEMO-SESSION-${System.currentTimeMillis() % 10000}"
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
