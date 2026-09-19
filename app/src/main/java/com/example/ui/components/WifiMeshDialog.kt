package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutoWifiState
import com.example.model.DiscoveredWifiNetwork
import com.example.model.WifiHotspotState
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalDarkBg
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceHighlight
import com.example.ui.theme.TacticalSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransmitRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiMeshDialog(
    hotspotState: WifiHotspotState,
    autoWifiState: AutoWifiState,
    discoveredNetworks: List<DiscoveredWifiNetwork>,
    hasHotspotPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onCreateHotspot: () -> Unit,
    onStopHotspot: () -> Unit,
    onToggleAutoConnect: (Boolean) -> Unit,
    onConnectToNetwork: (DiscoveredWifiNetwork) -> Unit,
    onDisconnectNetwork: () -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("wifi_mesh_dialog")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            color = TacticalDarkBg,
            border = BorderStroke(1.dp, TacticalSurfaceHighlight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hotspotState.isHosting) SignalGreen.copy(alpha = 0.2f)
                                    else TacticalAmber.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hotspotState.isHosting) Icons.Default.WifiTethering else Icons.Default.Wifi,
                                contentDescription = "Wi-Fi Mesh",
                                tint = if (hotspotState.isHosting) SignalGreen else TacticalAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "2.4GHz WI-FI MESH",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (hotspotState.isHosting) "HOSTING 2.4GHz HOTSPOT"
                                else if (autoWifiState.connectedSsid != null) "CONNECTED TO PEER MESH"
                                else "AUTO-CONNECT READY",
                                color = if (hotspotState.isHosting) SignalGreen else RadioCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("close_mesh_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 1: CREATE 2.4GHz WI-FI SSID (HOTSPOT)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    color = TacticalSurface,
                    border = BorderStroke(
                        1.dp,
                        if (hotspotState.isHosting) SignalGreen.copy(alpha = 0.6f) else TacticalSurfaceHighlight
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = null,
                                    tint = if (hotspotState.isHosting) SignalGreen else TacticalAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CREATE 2.4GHz SSID",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TacticalAmber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "2.4 GHz ONLY",
                                    color = TacticalAmber,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Broadcasts an ad-hoc 2.4 GHz Wi-Fi network directly from this phone so nearby devices running this walkie talkie can auto-connect.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (hotspotState.isHosting) {
                            // Active Hotspot Details
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = Color(0xFF0C2417),
                                border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(SignalGreen.copy(alpha = pulseAlpha))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "SSID: ${hotspotState.ssid}",
                                                color = SignalGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                copyToClipboard(context, "SSID", hotspotState.ssid)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy SSID",
                                                tint = SignalGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (hotspotState.password.isEmpty()) SignalGreen.copy(alpha = 0.2f) else RadioCyan.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = if (hotspotState.password.isEmpty()) "OPEN / NO PASSWORD" else "AUTO-SECURED MESH",
                                                    color = if (hotspotState.password.isEmpty()) SignalGreen else RadioCyan,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (hotspotState.password.isEmpty()) "Direct Peer Mesh" else "Auto-Connect Enabled",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (hotspotState.password.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "PWD: ${hotspotState.password}",
                                                    color = TacticalAmber,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = {
                                                        copyToClipboard(context, "Password", hotspotState.password)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = "Copy Password",
                                                        tint = TacticalAmber,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "BAND: 2.4 GHz | IP: ${hotspotState.ipAddress}",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = onStopHotspot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("stop_hotspot_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TransmitRed.copy(alpha = 0.2f),
                                    contentColor = TransmitRed
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, TransmitRed.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "STOP 2.4GHz HOTSPOT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            if (!hasHotspotPermission) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = TacticalAmber.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, TacticalAmber.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = TacticalAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Nearby Devices permission is required by Android to host a local 2.4GHz Wi-Fi hotspot.",
                                            color = TacticalAmber,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            // Start button
                            Button(
                                onClick = {
                                    if (!hasHotspotPermission) {
                                        onRequestPermission()
                                    } else {
                                        onCreateHotspot()
                                    }
                                },
                                enabled = !hotspotState.isStarting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("start_hotspot_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TacticalAmber,
                                    contentColor = TacticalDarkBg
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (hotspotState.isStarting) {
                                    CircularProgressIndicator(
                                        color = TacticalDarkBg,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "INITIALIZING 2.4GHz HOTSPOT...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                } else if (!hasHotspotPermission) {
                                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "GRANT PERMISSION TO HOST",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                } else {
                                    Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CREATE 2.4GHz WALKIE HOTSPOT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // SECTION 2: AUTO-CONNECT TO OTHER DEVICE'S WALKIE SSID
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    color = TacticalSurface,
                    border = BorderStroke(1.dp, TacticalSurfaceHighlight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AUTO-CONNECT TO PEERS",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Automatically join peer Walkie SSIDs",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Switch(
                                checked = autoWifiState.autoConnectEnabled,
                                onCheckedChange = onToggleAutoConnect,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SignalGreen,
                                    checkedTrackColor = SignalGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = TacticalSurfaceHighlight
                                ),
                                modifier = Modifier.testTag("auto_connect_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status & Scan header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STATUS: ${autoWifiState.statusMessage}",
                                color = if (autoWifiState.connectedSsid != null) SignalGreen else RadioCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = onScan,
                                enabled = !autoWifiState.isScanning,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("scan_walkie_ssids_button")
                            ) {
                                if (autoWifiState.isScanning) {
                                    CircularProgressIndicator(
                                        color = RadioCyan,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Scan Wi-Fi",
                                        tint = RadioCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Connected Peer Network Badge (if connected)
                        if (autoWifiState.connectedSsid != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = Color(0xFF0C2417),
                                border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SignalGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = autoWifiState.connectedSsid ?: "",
                                                color = SignalGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "Connected to peer 2.4GHz mesh",
                                                color = TextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = onDisconnectNetwork,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TransmitRed.copy(alpha = 0.2f),
                                            contentColor = TransmitRed
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(
                                            "DISCONNECT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Discovered Walkie SSIDs
                        val walkieNetworks = discoveredNetworks.filter { it.isWalkieNetwork }
                        if (walkieNetworks.isEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = TacticalSurfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No other Walkie SSIDs detected in range",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Start 2.4GHz Hotspot on another phone to connect automatically",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "DISCOVERED WALKIE NETWORKS (${walkieNetworks.size})",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            walkieNetworks.forEach { network ->
                                val isConnected = network.ssid == autoWifiState.connectedSsid
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = if (isConnected) Color(0xFF0F293E) else TacticalSurfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isConnected) SignalGreen else TacticalSurfaceHighlight
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SignalWifi4Bar,
                                                contentDescription = null,
                                                tint = if (network.is24Ghz) TacticalAmber else RadioCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = network.ssid,
                                                        color = TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    if (network.is24Ghz) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(3.dp),
                                                            color = TacticalAmber.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                text = "2.4GHz",
                                                                color = TacticalAmber,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "Signal: ${network.level} dBm | ${network.frequencyMhz} MHz | ${network.securityInfo}",
                                                    color = TextMuted,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        if (isConnected) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = SignalGreen.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "CONNECTED",
                                                    color = SignalGreen,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { onConnectToNetwork(network) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = RadioCyan,
                                                    contentColor = TacticalDarkBg
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text(
                                                    "CONNECT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
