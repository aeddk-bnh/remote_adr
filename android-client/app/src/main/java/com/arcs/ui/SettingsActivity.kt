package com.arcs.client.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import timber.log.Timber

/**
 * Settings Activity for ARCS Android Client
 * Provides configuration UI for:
 * - Server connection settings
 * - Video quality preferences
 * - Accessibility/IME setup shortcuts
 * - Feature toggles
 * - About information
 */
class SettingsActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ARCSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf("wss://192.168.1.100:8080") }
    var videoQuality by remember { mutableStateOf(2f) } // 0=Low, 1=Medium, 2=High, 3=Ultra
    var enableAudio by remember { mutableStateOf(false) }
    var enableAutomation by remember { mutableStateOf(true) }
    var enableAI by remember { mutableStateOf(true) }
    var showAdvanced by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back"
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Connection Settings
            SettingsSection(title = "Connection") {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("wss://server:port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    text = "Enter the WebSocket server URL (wss:// for secure, ws:// for insecure)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Video Settings
            SettingsSection(title = "Video Quality") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (videoQuality.toInt()) {
                                0 -> "Low (500 kbps)"
                                1 -> "Medium (1.5 Mbps)"
                                2 -> "High (3 Mbps)"
                                3 -> "Ultra (5 Mbps)"
                                else -> "Medium"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    Slider(
                        value = videoQuality,
                        onValueChange = { videoQuality = it },
                        valueRange = 0f..3f,
                        steps = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "Higher quality requires more bandwidth and battery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Feature Toggles
            SettingsSection(title = "Features") {
                SwitchPreference(
                    title = "Audio Streaming",
                    subtitle = "Stream device audio (experimental)",
                    checked = enableAudio,
                    onCheckedChange = { enableAudio = it }
                )
                
                SwitchPreference(
                    title = "Macro Automation",
                    subtitle = "Enable macro recording and playback",
                    checked = enableAutomation,
                    onCheckedChange = { enableAutomation = it }
                )
                
                SwitchPreference(
                    title = "AI Assistance",
                    subtitle = "Enable OCR and UI detection features",
                    checked = enableAI,
                    onCheckedChange = { enableAI = it }
                )
            }
            
            // System Integration
            SettingsSection(title = "System Integration") {
                ClickablePreference(
                    title = "Accessibility Service",
                    subtitle = "Required for touch injection",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open accessibility settings")
                        }
                    }
                )
                
                ClickablePreference(
                    title = "Input Method",
                    subtitle = "Required for keyboard input",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open IME settings")
                        }
                    }
                )
                
                ClickablePreference(
                    title = "App Permissions",
                    subtitle = "Manage app permissions",
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open app settings")
                        }
                    }
                )
            }
            
            // Advanced Settings
            SettingsSection(title = "Advanced") {
                SwitchPreference(
                    title = "Show Advanced Options",
                    subtitle = "Display developer settings",
                    checked = showAdvanced,
                    onCheckedChange = { showAdvanced = it }
                )
                
                if (showAdvanced) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Developer Options",
                                style = MaterialTheme.typography.titleSmall
                            )
                            
                            Text(
                                text = "• Enable debug logging\n• Network diagnostics\n• Performance monitoring\n• Protocol inspection",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // About Section
            SettingsSection(title = "About") {
                InfoRow(label = "Version", value = "1.0.0")
                InfoRow(label = "Build", value = "2026.01.07")
                InfoRow(label = "Protocol", value = "ARCS v1.0")
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "Android Remote Control System (ARCS)\nNon-root remote control solution with real-time video streaming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ClickablePreference(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painter = painterResource(android.R.drawable.ic_menu_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
