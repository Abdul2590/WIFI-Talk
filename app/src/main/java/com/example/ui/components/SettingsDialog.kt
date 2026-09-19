package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioConstants
import com.example.model.AudioOutputDevice
import com.example.model.AudioOutputType
import com.example.model.AutoWifiState
import com.example.model.PttMode
import com.example.model.UserProfile
import com.example.model.WifiConnectionState
import com.example.model.WifiHotspotState
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    callSign: String,
    pttMode: PttMode,
    isRogerBeepEnabled: Boolean,
    volumeGain: Float,
    isLoopbackTestEnabled: Boolean,
    wifiState: WifiConnectionState,
    deviceId: String,
    isMicEnabled: Boolean = true,
    isNoiseCancellationEnabled: Boolean = true,
    isEchoCancellationEnabled: Boolean = true,
    isAudioClarityBoostEnabled: Boolean = true,
    audioDevices: List<AudioOutputDevice> = emptyList(),
    selectedAudioDevice: AudioOutputDevice? = null,
    hotspotState: WifiHotspotState = WifiHotspotState(),
    autoWifiState: AutoWifiState = AutoWifiState(),
    userProfile: UserProfile = UserProfile(),
    onOpenProfileSetup: () -> Unit = {},
    onUpdateCallSign: (String) -> Unit,
    onSetPttMode: (PttMode) -> Unit,
    onToggleRogerBeep: () -> Unit,
    onToggleMic: () -> Unit = {},
    onToggleNoiseCancellation: () -> Unit = {},
    onToggleEchoCancellation: () -> Unit = {},
    onToggleAudioClarityBoost: () -> Unit = {},
    onSetVolumeGain: (Float) -> Unit,
    onToggleLoopbackTest: () -> Unit,
    onSelectAudioDevice: (AudioOutputDevice) -> Unit = {},
    onOpenWifiMesh: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var editingCallSign by remember(callSign) { mutableStateOf(callSign) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("settings_dialog")
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
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = TacticalAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RADIO SETTINGS",
                            color = TacticalAmber,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CallSign Input
                Text(
                    text = "CALL SIGN / HANDLE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editingCallSign,
                        onValueChange = { editingCallSign = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("callsign_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TacticalAmber,
                            unfocusedBorderColor = TacticalSurfaceHighlight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = TacticalSurface,
                            unfocusedContainerColor = TacticalSurface
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onUpdateCallSign(editingCallSign)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TacticalAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_callsign_button")
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Messaging Profile Setup Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = TacticalSurface,
                    border = BorderStroke(1.dp, if (userProfile.isMessagingEnabled) SignalGreen.copy(alpha = 0.4f) else TacticalAmber.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MESSAGING IDENTITY",
                                color = if (userProfile.isMessagingEnabled) SignalGreen else TacticalAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (userProfile.isMessagingEnabled) SignalGreen.copy(alpha = 0.2f) else TransmitRed.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (userProfile.isMessagingEnabled) "ENABLED" else "DISABLED",
                                    color = if (userProfile.isMessagingEnabled) SignalGreen else TransmitRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (userProfile.isMessagingEnabled) {
                            Text(
                                text = "Operator: ${userProfile.userName}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Mobile: ${userProfile.mobileNumber}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "To chat with peers, you must set your Name and Mobile Number.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onOpenProfileSetup,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userProfile.isMessagingEnabled) RadioCyan else TacticalAmber,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("settings_setup_profile_button")
                        ) {
                            Text(
                                text = if (userProfile.isMessagingEnabled) "EDIT NAME & MOBILE" else "REGISTER NAME & MOBILE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PTT Mode Selector
                Text(
                    text = "PUSH-TO-TALK MODE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSetPttMode(PttMode.HOLD_TO_TALK) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pttMode == PttMode.HOLD_TO_TALK) TacticalAmber else TacticalSurface,
                            contentColor = if (pttMode == PttMode.HOLD_TO_TALK) Color.Black else TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (pttMode == PttMode.HOLD_TO_TALK) TacticalAmber else TacticalSurfaceHighlight)
                    ) {
                        Text("HOLD TO TALK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onSetPttMode(PttMode.TAP_TO_TOGGLE) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pttMode == PttMode.TAP_TO_TOGGLE) TacticalAmber else TacticalSurface,
                            contentColor = if (pttMode == PttMode.TAP_TO_TOGGLE) Color.Black else TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (pttMode == PttMode.TAP_TO_TOGGLE) TacticalAmber else TacticalSurfaceHighlight)
                    ) {
                        Text("TAP TO TOGGLE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Roger Beep Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Roger Beep & Chirp",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Radio sound tone when pressing & releasing PTT",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isRogerBeepEnabled,
                        onCheckedChange = { onToggleRogerBeep() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = TacticalAmber,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = TacticalSurfaceHighlight
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Device Microphone Toggle Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (isMicEnabled) SignalGreen else TransmitRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMicEnabled) "Device Microphone (Enabled)" else "Device Microphone (Disabled)",
                                color = if (isMicEnabled) TextPrimary else TransmitRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isMicEnabled) "Captures voice during PTT transmission" else "Microphone muted: only silence sent during transmission",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isMicEnabled,
                        onCheckedChange = { onToggleMic() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = SignalGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = TacticalSurfaceHighlight
                        ),
                        modifier = Modifier.testTag("device_mic_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Background Noise Cancellation Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = SignalGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Noise Cancellation & Gate",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Removes background room noise, wind rumble, and microphone static",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isNoiseCancellationEnabled,
                        onCheckedChange = { onToggleNoiseCancellation() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = SignalGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = TacticalSurfaceHighlight
                        ),
                        modifier = Modifier.testTag("noise_cancellation_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Acoustic Echo Cancellation Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Hearing,
                                contentDescription = null,
                                tint = RadioCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Echo Cancellation (AEC)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Suppresses feedback and acoustic loopback from loudspeaker",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isEchoCancellationEnabled,
                        onCheckedChange = { onToggleEchoCancellation() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = RadioCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = TacticalSurfaceHighlight
                        ),
                        modifier = Modifier.testTag("echo_cancellation_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Crystal Clear Audio Boost Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SpatialAudio,
                                contentDescription = null,
                                tint = TacticalAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Clear Audio Speech Formants",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "HD voice enhancement for crystal-clear vocal intelligibility",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isAudioClarityBoostEnabled,
                        onCheckedChange = { onToggleAudioClarityBoost() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = TacticalAmber,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = TacticalSurfaceHighlight
                        ),
                        modifier = Modifier.testTag("audio_clarity_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Loopback / Mic Self-Test Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = null,
                                tint = RadioCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mic Loopback Test",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Play your own voice through speaker to test mic audio locally",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isLoopbackTestEnabled,
                        onCheckedChange = { onToggleLoopbackTest() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = RadioCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = TacticalSurfaceHighlight
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speaker Volume Gain Slider
                Text(
                    text = "SPEAKER BOOST GAIN (${(volumeGain * 100).roundToInt()}%)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = volumeGain,
                    onValueChange = onSetVolumeGain,
                    valueRange = 1.0f..3.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = TacticalAmber,
                        activeTrackColor = TacticalAmber,
                        inactiveTrackColor = TacticalSurfaceHighlight
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-detected Speaker Devices & Output Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPEAKER OUTPUT (${audioDevices.size} DETECTED)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "AUTO-ROUTING",
                        color = SignalGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                audioDevices.forEach { device ->
                    val isSelected = device.id == selectedAudioDevice?.id || (selectedAudioDevice == null && device.isSelected)
                    val icon = when (device.type) {
                        AudioOutputType.LOUDSPEAKER -> Icons.Default.Speaker
                        AudioOutputType.EARPIECE -> Icons.Default.PhoneAndroid
                        AudioOutputType.BLUETOOTH -> Icons.Default.Bluetooth
                        else -> Icons.Default.Headphones
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectAudioDevice(device) }
                            .testTag("settings_speaker_${device.type.name}"),
                        color = if (isSelected) Color(0xFF0F293E) else TacticalSurface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) RadioCyan else TacticalSurfaceHighlight
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) RadioCyan else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = device.name,
                                            color = if (isSelected) TextPrimary else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (device.isDefault) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "(Default)",
                                                color = TacticalAmber,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = device.description,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) RadioCyan else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2.4GHz Wi-Fi Mesh & Hotspot Quick Link
                Text(
                    text = "2.4 GHz WI-FI MESH & PEER HOTSPOT",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenWifiMesh() }
                        .testTag("open_mesh_from_settings"),
                    color = TacticalSurface,
                    border = BorderStroke(1.dp, TacticalSurfaceHighlight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (hotspotState.isHosting) SignalGreen.copy(alpha = 0.2f) else TacticalAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hotspotState.isHosting) Icons.Default.WifiTethering else Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (hotspotState.isHosting) SignalGreen else TacticalAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (hotspotState.isHosting) "2.4GHz Hotspot: ${hotspotState.ssid}"
                                    else if (autoWifiState.connectedSsid != null) "Connected: ${autoWifiState.connectedSsid}"
                                    else "Local 2.4GHz Wi-Fi Mesh",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (hotspotState.isHosting) "Broadcasting 2.4GHz • Tap to manage"
                                    else "Auto-connect to peer walkies • Tap to configure",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Button(
                            onClick = onOpenWifiMesh,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TacticalAmber.copy(alpha = 0.2f),
                                contentColor = TacticalAmber
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "MANAGE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Technical Diagnostic Card
                Text(
                    text = "DIAGNOSTICS & NETWORK",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = TacticalSurfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DiagItem("Device ID", deviceId)
                        DiagItem("Local Wi-Fi IP", wifiState.ipAddress)
                        DiagItem("Broadcast Subnet", wifiState.broadcastAddress)
                        DiagItem("UDP Port", "${AudioConstants.DEFAULT_PORT}")
                        DiagItem("Audio Latency", "< 15 ms (${AudioConstants.CHUNK_DURATION_MS}ms frame)")
                        DiagItem("Sample Rate", "${AudioConstants.SAMPLE_RATE} Hz (16-bit PCM)")
                        DiagItem("Multicast Lock", if (wifiState.isMulticastLockAcquired) "Active (Enabled)" else "Standard")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
