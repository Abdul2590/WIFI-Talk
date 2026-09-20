package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.AudioOutputType
import com.example.model.PttMode
import com.example.model.TransmissionState
import com.example.notification.WalkieNotificationManager
import com.example.ui.components.AudioOutputDialog
import com.example.ui.components.AudioVisualizer
import com.example.ui.components.ChannelSelector
import com.example.ui.components.ChannelsSheet
import com.example.ui.components.ChatDialog
import com.example.ui.components.PeersDialog
import com.example.ui.components.ProfileSetupDialog
import com.example.ui.components.PttButton
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TacticalDisplay
import com.example.ui.components.WifiMeshDialog
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberLight
import com.example.ui.theme.TacticalDarkBg
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceHighlight
import com.example.ui.theme.TacticalSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransmitRed

@Composable
fun WalkieScreen(
    viewModel: WalkieViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val activeChannel by viewModel.activeChannel.collectAsState()
    val transmissionState by viewModel.transmissionState.collectAsState()
    val liveAudioLevel by viewModel.liveAudioLevel.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val peersOnCurrentChannel by viewModel.peersOnCurrentChannel.collectAsState()
    val wifiState by viewModel.wifiState.collectAsState()
    val pttMode by viewModel.pttMode.collectAsState()
    val isRogerBeepEnabled by viewModel.isRogerBeepEnabled.collectAsState()
    val isLoopbackTestEnabled by viewModel.isLoopbackTestEnabled.collectAsState()
    val callSign by viewModel.callSign.collectAsState()
    val hasPermission by viewModel.hasRecordPermission.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isMicEnabled by viewModel.isMicEnabled.collectAsState()
    val isNoiseCancellationEnabled by viewModel.isNoiseCancellationEnabled.collectAsState()
    val isEchoCancellationEnabled by viewModel.isEchoCancellationEnabled.collectAsState()
    val isAudioClarityBoostEnabled by viewModel.isAudioClarityBoostEnabled.collectAsState()
    val volumeGain by viewModel.volumeGain.collectAsState()

    val detectedAudioDevices by viewModel.detectedAudioDevices.collectAsState()
    val selectedAudioDevice by viewModel.selectedAudioDevice.collectAsState()
    val wifiHotspotState by viewModel.wifiHotspotState.collectAsState()
    val autoWifiState by viewModel.autoWifiState.collectAsState()
    val discoveredWalkieNetworks by viewModel.discoveredWalkieNetworks.collectAsState()

    // Messaging states
    val userProfile by viewModel.userProfile.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val unreadCount by viewModel.unreadMessageCount.collectAsState()

    var showChannelsSheet by rememberSaveable { mutableStateOf(false) }
    var showPeersDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioOutputDialog by rememberSaveable { mutableStateOf(false) }
    var showWifiMeshDialog by rememberSaveable { mutableStateOf(false) }
    var showChatDialog by rememberSaveable { mutableStateOf(false) }
    var showProfileSetupDialog by rememberSaveable { mutableStateOf(false) }

    // Multi-permission launcher (mic, nearby wifi, location, bluetooth, notifications)
    val permissionsToRequest = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val recordGranted = results[Manifest.permission.RECORD_AUDIO] ?: (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
        viewModel.setHasRecordPermission(recordGranted)
        viewModel.refreshAudioDevices()
        viewModel.scanForWalkieNetworks()

        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.NEARBY_WIFI_DEVICES] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (nearbyGranted && showWifiMeshDialog && !wifiHotspotState.isHosting && !wifiHotspotState.isStarting) {
            viewModel.create24GhzHotspot()
        }
    }

    val activity = context as? androidx.activity.ComponentActivity
    LaunchedEffect(Unit) {
        if (activity?.intent?.getBooleanExtra(WalkieNotificationManager.EXTRA_OPEN_CHAT, false) == true) {
            showChatDialog = true
            viewModel.markMessagesAsRead()
        }

        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setHasRecordPermission(micGranted)

        val anyMissing = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (anyMissing) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TacticalDarkBg,
                        Color(0xFF0F1622),
                        TacticalDarkBg
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("walkie_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // TOP NAVIGATION & STATUS BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio Title & Callsign
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (wifiState.isConnected) SignalGreen else TransmitRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WI-FI WALKIE-TALKIE",
                            color = TacticalAmber,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "CALLSIGN: $callSign",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Top Controls (Speaker, Wi-Fi Mesh, Mute, Peers, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Speaker Selector Button
                    val speakerIcon = when (selectedAudioDevice?.type) {
                        AudioOutputType.LOUDSPEAKER -> Icons.Default.Speaker
                        AudioOutputType.EARPIECE -> Icons.Default.PhoneAndroid
                        AudioOutputType.BLUETOOTH -> Icons.Default.Bluetooth
                        AudioOutputType.WIRED_HEADSET,
                        AudioOutputType.USB_AUDIO -> Icons.Default.Headphones
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    IconButton(
                        onClick = { showAudioOutputDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(TacticalSurface)
                            .testTag("speaker_selector_top_button")
                    ) {
                        Icon(
                            imageVector = speakerIcon,
                            contentDescription = "Speaker Selection: ${selectedAudioDevice?.name ?: "Default"}",
                            tint = TacticalAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 2.4GHz Wi-Fi Mesh / Hotspot Button
                    val isHotspotActive = wifiHotspotState.isHosting
                    val isMeshPeerConnected = autoWifiState.connectedSsid != null
                    IconButton(
                        onClick = { showWifiMeshDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isHotspotActive) SignalGreen.copy(alpha = 0.2f)
                                else if (isMeshPeerConnected) RadioCyan.copy(alpha = 0.2f)
                                else TacticalSurface
                            )
                            .testTag("wifi_mesh_top_button")
                    ) {
                        Icon(
                            imageVector = if (isHotspotActive) Icons.Default.WifiTethering else Icons.Default.Wifi,
                            contentDescription = "2.4GHz Wi-Fi Mesh",
                            tint = if (isHotspotActive) SignalGreen else if (isMeshPeerConnected) RadioCyan else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Speaker Mute Toggle
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) TransmitRed.copy(alpha = 0.2f) else TacticalSurface)
                            .testTag("mute_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Speaker" else "Mute Speaker",
                            tint = if (isMuted) TransmitRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Device Mic Enable/Disable Toggle
                    IconButton(
                        onClick = { viewModel.toggleMic() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (!isMicEnabled) TransmitRed.copy(alpha = 0.2f) else SignalGreen.copy(alpha = 0.15f))
                            .testTag("device_mic_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (isMicEnabled) "Disable Device Mic" else "Enable Device Mic",
                            tint = if (isMicEnabled) SignalGreen else TransmitRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Discovered Peers Badge Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showPeersDialog = true }
                            .testTag("open_peers_button"),
                        color = if (peers.isNotEmpty()) Color(0xFF0C2433) else TacticalSurface,
                        border = BorderStroke(1.dp, if (peers.isNotEmpty()) RadioCyan else TacticalSurfaceHighlight)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = "Online Peers",
                                tint = if (peers.isNotEmpty()) RadioCyan else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${peers.size}",
                                color = if (peers.isNotEmpty()) RadioCyan else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Chat / Messaging Button with unread badge
                    IconButton(
                        onClick = {
                            viewModel.markMessagesAsRead()
                            showChatDialog = true
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (unreadCount > 0) RadioCyan.copy(alpha = 0.25f) else TacticalSurface)
                            .testTag("open_chat_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(
                                        containerColor = SignalGreen,
                                        contentColor = Color.Black
                                    ) {
                                        Text("$unreadCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Messages",
                                tint = if (userProfile.isMessagingEnabled) RadioCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Settings Button
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(TacticalSurface)
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MIC PERMISSION BANNER
            AnimatedVisibility(
                visible = !hasPermission,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF451A1A),
                    border = BorderStroke(1.dp, TransmitRed)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = TransmitRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Microphone access is required to broadcast audio.",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TransmitRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Text("GRANT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // TACTICAL RADIO DISPLAY PANEL
            TacticalDisplay(
                channel = activeChannel,
                transmissionState = transmissionState,
                wifiState = wifiState,
                peerCount = peersOnCurrentChannel.size,
                isLoopbackTest = isLoopbackTestEnabled,
                selectedSpeaker = selectedAudioDevice,
                hotspotState = wifiHotspotState,
                autoWifiState = autoWifiState,
                onSpeakerClick = { showAudioOutputDialog = true },
                onWifiMeshClick = { showWifiMeshDialog = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // REAL-TIME AUDIO VISUALIZER
            AudioVisualizer(
                audioLevel = liveAudioLevel,
                transmissionState = transmissionState
            )

            Spacer(modifier = Modifier.height(14.dp))

            // CHANNEL SELECTOR
            ChannelSelector(
                currentChannel = activeChannel,
                onSelectChannel = { viewModel.selectChannel(it) },
                onOpenChannelSheet = { showChannelsSheet = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PUSH TO TALK BUTTON
            PttButton(
                isTransmitting = transmissionState is TransmissionState.Transmitting,
                pttMode = pttMode,
                hasPermission = hasPermission,
                isMicEnabled = isMicEnabled,
                onPress = { viewModel.handlePttPress() },
                onRelease = { viewModel.handlePttRelease() },
                onClickToggle = { viewModel.handlePttPress() },
                onPermissionRequired = { permissionLauncher.launch(permissionsToRequest) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // QUICK UTILITY CONTROLS BAR
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = TacticalSurface,
                border = BorderStroke(1.dp, TacticalSurfaceHighlight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speaker Select
                    val speakerQuickIcon = when (selectedAudioDevice?.type) {
                        AudioOutputType.LOUDSPEAKER -> Icons.Default.Speaker
                        AudioOutputType.EARPIECE -> Icons.Default.PhoneAndroid
                        AudioOutputType.BLUETOOTH -> Icons.Default.Bluetooth
                        else -> Icons.Default.Headphones
                    }
                    QuickActionButton(
                        icon = speakerQuickIcon,
                        label = selectedAudioDevice?.type?.name?.take(7) ?: "Speaker",
                        isActive = true,
                        onClick = { showAudioOutputDialog = true }
                    )

                    // 2.4GHz Mesh Hotspot
                    QuickActionButton(
                        icon = if (wifiHotspotState.isHosting) Icons.Default.WifiTethering else Icons.Default.Wifi,
                        label = if (wifiHotspotState.isHosting) "2.4G Host" else if (autoWifiState.connectedSsid != null) "Mesh OK" else "2.4G Mesh",
                        isActive = wifiHotspotState.isHosting || autoWifiState.connectedSsid != null,
                        onClick = { showWifiMeshDialog = true }
                    )

                    // PTT Mode Quick Toggle
                    QuickActionButton(
                        icon = Icons.Default.Mic,
                        label = if (pttMode == PttMode.HOLD_TO_TALK) "Hold PTT" else "Toggle",
                        isActive = pttMode == PttMode.TAP_TO_TOGGLE,
                        onClick = {
                            viewModel.setPttMode(
                                if (pttMode == PttMode.HOLD_TO_TALK) PttMode.TAP_TO_TOGGLE else PttMode.HOLD_TO_TALK
                            )
                        }
                    )

                    // Roger Beep Quick Toggle
                    QuickActionButton(
                        icon = if (isRogerBeepEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        label = if (isRogerBeepEnabled) "Beep ON" else "Beep OFF",
                        isActive = isRogerBeepEnabled,
                        onClick = { viewModel.toggleRogerBeep() }
                    )

                    // Loopback Mic Test Quick Toggle
                    QuickActionButton(
                        icon = Icons.Default.Headphones,
                        label = if (isLoopbackTestEnabled) "Test ON" else "Mic Test",
                        isActive = isLoopbackTestEnabled,
                        onClick = { viewModel.toggleLoopbackTest() }
                    )

                    // Channels Directory
                    QuickActionButton(
                        icon = Icons.Default.CellTower,
                        label = "Channels",
                        isActive = false,
                        onClick = { showChannelsSheet = true }
                    )

                    // Text Chat Quick Action
                    QuickActionButton(
                        icon = Icons.Default.Chat,
                        label = if (unreadCount > 0) "Chat ($unreadCount)" else "Chat",
                        isActive = userProfile.isMessagingEnabled,
                        onClick = {
                            viewModel.markMessagesAsRead()
                            showChatDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // SHEET / DIALOG OVERLAYS
        if (showChannelsSheet) {
            ChannelsSheet(
                currentChannel = activeChannel,
                peers = peers,
                onSelectChannel = { viewModel.selectChannel(it) },
                onDismiss = { showChannelsSheet = false }
            )
        }

        if (showPeersDialog) {
            PeersDialog(
                peers = peers,
                myCallSign = callSign,
                myIp = wifiState.ipAddress,
                currentChannelId = activeChannel.id,
                onRefresh = { viewModel.refreshNetwork() },
                onDismiss = { showPeersDialog = false }
            )
        }

        if (showAudioOutputDialog) {
            AudioOutputDialog(
                devices = detectedAudioDevices,
                selectedDevice = selectedAudioDevice,
                onSelectDevice = { viewModel.selectAudioOutput(it) },
                onRefresh = { viewModel.refreshAudioDevices() },
                onDismiss = { showAudioOutputDialog = false }
            )
        }

        if (showWifiMeshDialog) {
            WifiMeshDialog(
                hotspotState = wifiHotspotState,
                autoWifiState = autoWifiState,
                discoveredNetworks = discoveredWalkieNetworks,
                hasHotspotPermission = viewModel.hasHotspotPermission(),
                currentDynamicSsid = viewModel.currentDynamicSsid,
                onRegenerateDynamicSsid = { viewModel.regenerateDynamicSsid() },
                onRequestPermission = { permissionLauncher.launch(permissionsToRequest) },
                onCreateHotspot = {
                    if (!viewModel.hasHotspotPermission()) {
                        permissionLauncher.launch(permissionsToRequest)
                    } else {
                        viewModel.create24GhzHotspot()
                    }
                },
                onStopHotspot = { viewModel.stopHotspot() },
                onToggleAutoConnect = { viewModel.toggleAutoConnect(it) },
                onConnectToNetwork = { viewModel.connectToWalkieNetwork(it) },
                onDisconnectNetwork = { viewModel.disconnectWalkieNetwork() },
                onScan = { viewModel.scanForWalkieNetworks() },
                onDismiss = { showWifiMeshDialog = false }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                callSign = callSign,
                pttMode = pttMode,
                isRogerBeepEnabled = isRogerBeepEnabled,
                volumeGain = volumeGain,
                isLoopbackTestEnabled = isLoopbackTestEnabled,
                wifiState = wifiState,
                deviceId = viewModel.deviceId,
                isMicEnabled = isMicEnabled,
                isNoiseCancellationEnabled = isNoiseCancellationEnabled,
                isEchoCancellationEnabled = isEchoCancellationEnabled,
                isAudioClarityBoostEnabled = isAudioClarityBoostEnabled,
                audioDevices = detectedAudioDevices,
                selectedAudioDevice = selectedAudioDevice,
                hotspotState = wifiHotspotState,
                autoWifiState = autoWifiState,
                userProfile = userProfile,
                onOpenProfileSetup = {
                    showProfileSetupDialog = true
                },
                onUpdateCallSign = { viewModel.updateCallSign(it) },
                onSetPttMode = { viewModel.setPttMode(it) },
                onToggleRogerBeep = { viewModel.toggleRogerBeep() },
                onToggleMic = { viewModel.toggleMic() },
                onToggleNoiseCancellation = { viewModel.toggleNoiseCancellation() },
                onToggleEchoCancellation = { viewModel.toggleEchoCancellation() },
                onToggleAudioClarityBoost = { viewModel.toggleAudioClarityBoost() },
                onSetVolumeGain = { viewModel.setVolumeGain(it) },
                onToggleLoopbackTest = { viewModel.toggleLoopbackTest() },
                onSelectAudioDevice = { viewModel.selectAudioOutput(it) },
                onOpenWifiMesh = { showWifiMeshDialog = true },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showChatDialog) {
            LaunchedEffect(Unit) {
                viewModel.markMessagesAsRead()
            }
            ChatDialog(
                userProfile = userProfile,
                messages = chatMessages,
                peers = peers,
                currentChannel = activeChannel,
                onSendMessage = { text, recipId ->
                    viewModel.sendChatMessage(text, recipId)
                },
                onOpenProfileSetup = {
                    showProfileSetupDialog = true
                },
                onClearChat = {
                    viewModel.clearChatMessages()
                },
                onDismiss = {
                    viewModel.markMessagesAsRead()
                    showChatDialog = false
                }
            )
        }

        if (showProfileSetupDialog) {
            ProfileSetupDialog(
                userProfile = userProfile,
                onSaveProfile = { name, mobile ->
                    viewModel.saveUserProfile(name, mobile, enableMessaging = true)
                },
                onDisableMessaging = {
                    viewModel.disableMessaging()
                },
                onDismiss = { showProfileSetupDialog = false }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) TacticalAmber else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (isActive) TacticalAmberLight else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
