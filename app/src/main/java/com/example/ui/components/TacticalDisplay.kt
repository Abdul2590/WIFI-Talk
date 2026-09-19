package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioOutputDevice
import com.example.model.AudioOutputType
import com.example.model.AutoWifiState
import com.example.model.TransmissionState
import com.example.model.WalkieChannel
import com.example.model.WifiConnectionState
import com.example.model.WifiHotspotState
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.RadioCyanDark
import com.example.ui.theme.RadioDisplayBg
import com.example.ui.theme.RadioDisplayBorder
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberDark
import com.example.ui.theme.TacticalAmberLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransmitRed

@Composable
fun TacticalDisplay(
    channel: WalkieChannel,
    transmissionState: TransmissionState,
    wifiState: WifiConnectionState,
    peerCount: Int,
    isLoopbackTest: Boolean,
    selectedSpeaker: AudioOutputDevice? = null,
    hotspotState: WifiHotspotState = WifiHotspotState(),
    autoWifiState: AutoWifiState = AutoWifiState(),
    onSpeakerClick: () -> Unit = {},
    onWifiMeshClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "display_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val borderColor by animateColorAsState(
        targetValue = when (transmissionState) {
            is TransmissionState.Transmitting -> TransmitRed
            is TransmissionState.Receiving -> SignalGreen
            is TransmissionState.Idle -> RadioDisplayBorder
        },
        label = "display_border"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(16.dp))
            .testTag("tactical_display_panel"),
        color = RadioDisplayBg,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF04111A),
                            RadioDisplayBg,
                            Color(0xFF051722)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Status Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wi-Fi / Mesh Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (hotspotState.isHosting) Color(0xFF0D331D)
                            else if (autoWifiState.connectedSsid != null) Color(0xFF0C2B3C)
                            else Color(0xFF0C2433)
                        )
                        .clickable { onWifiMeshClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("tactical_wifi_mesh_pill")
                ) {
                    Icon(
                        imageVector = if (hotspotState.isHosting) Icons.Default.WifiTethering
                        else if (wifiState.isConnected) Icons.Default.Wifi
                        else Icons.Default.WifiOff,
                        contentDescription = "Wi-Fi Status",
                        tint = if (hotspotState.isHosting) SignalGreen
                        else if (wifiState.isConnected) RadioCyan
                        else TransmitRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (hotspotState.isHosting) "2.4G HOTSPOT"
                        else if (autoWifiState.connectedSsid != null) autoWifiState.connectedSsid ?: "PEER"
                        else wifiState.ipAddress,
                        color = if (hotspotState.isHosting) SignalGreen
                        else if (wifiState.isConnected) RadioCyan
                        else TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Speaker Output Pill
                val speakerIcon = when (selectedSpeaker?.type) {
                    AudioOutputType.LOUDSPEAKER -> Icons.Default.Speaker
                    AudioOutputType.EARPIECE -> Icons.Default.PhoneAndroid
                    AudioOutputType.BLUETOOTH -> Icons.Default.Bluetooth
                    else -> Icons.Default.VolumeUp
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C2433))
                        .clickable { onSpeakerClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("tactical_speaker_output_pill")
                ) {
                    Icon(
                        imageVector = speakerIcon,
                        contentDescription = "Speaker Output",
                        tint = TacticalAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = (selectedSpeaker?.name ?: "SPK").uppercase(),
                        color = TacticalAmberLight,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Loopback / Test Mode badge if on
                if (isLoopbackTest) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(TacticalAmberDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Loopback Test",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "TEST",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Peers Online Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF0C2433), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (peerCount > 0) SignalGreen else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$peerCount PEER${if (peerCount != 1) "S" else ""}",
                        color = if (peerCount > 0) TextPrimary else TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Channel Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = channel.name,
                        color = TacticalAmberLight,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = channel.frequency,
                        color = RadioCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // State Pill (TRANSMITTING, RECEIVING, or STANDBY)
                when (transmissionState) {
                    is TransmissionState.Transmitting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TransmitRed.copy(alpha = pulseAlpha))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Broadcasting",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "TX BROADCAST",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    is TransmissionState.Receiving -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SignalGreen.copy(alpha = pulseAlpha))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Receiving",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "RX RECEIVING",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    is TransmissionState.Idle -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0E2938))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = "Standby",
                                tint = RadioCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "STANDBY",
                                color = RadioCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Sub-banner for Incoming Speaker CallSign
            if (transmissionState is TransmissionState.Receiving) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09302A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPEAKER: ",
                        color = SignalGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = transmissionState.speakerCallSign,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
