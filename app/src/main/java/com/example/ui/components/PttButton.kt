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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PttMode
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberDark
import com.example.ui.theme.TacticalAmberLight
import com.example.ui.theme.TacticalDarkBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TransmitRed
import com.example.ui.theme.TransmitRedDark
import com.example.ui.theme.TransmitRedGlow

@Composable
fun PttButton(
    isTransmitting: Boolean,
    pttMode: PttMode,
    hasPermission: Boolean,
    isMicEnabled: Boolean = true,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onClickToggle: () -> Unit,
    onPermissionRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isTransmitting) TransmitRed else TacticalAmber,
        animationSpec = tween(150),
        label = "ptt_color"
    )

    val glowColor by animateColorAsState(
        targetValue = if (isTransmitting) TransmitRedGlow else Color(0x22F59E0B),
        label = "ptt_glow"
    )

    val interactionModifier = if (!hasPermission) {
        Modifier.clickable { onPermissionRequired() }
    } else if (pttMode == PttMode.HOLD_TO_TALK) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onPress()
                waitForUpOrCancellation()
                onRelease()
            }
        }
    } else {
        Modifier.clickable { onClickToggle() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ptt_button_container"),
        contentAlignment = Alignment.Center
    ) {
        val totalSize = maxWidth.coerceIn(160.dp, 230.dp)
        val outerRingSize = totalSize * 0.98f
        val rimRingSize = totalSize * 0.90f
        val buttonSize = totalSize * 0.78f
        val iconSize = (buttonSize * 0.26f).coerceIn(32.dp, 48.dp)
        val titleTextSize = if (totalSize < 190.dp) 13.sp else 15.sp
        val subTextSize = if (totalSize < 190.dp) 9.sp else 10.sp

        Box(
            modifier = Modifier.size(totalSize),
            contentAlignment = Alignment.Center
        ) {
            // Outer pulsing ring when broadcasting
            if (isTransmitting) {
                Box(
                    modifier = Modifier
                        .size(outerRingSize)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(glowColor)
                )
            }

            // Secondary rim ring
            Box(
                modifier = Modifier
                    .size(rimRingSize)
                    .clip(CircleShape)
                    .background(Color(0xFF161F2E))
                    .border(BorderStroke(3.dp, if (isTransmitting) TransmitRedDark else Color(0xFF27354E)), CircleShape)
            )

            // Main tactile button
            Surface(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .shadow(elevation = if (isTransmitting) 16.dp else 8.dp, shape = CircleShape)
                    .then(interactionModifier)
                    .testTag("ptt_button"),
                shape = CircleShape,
                color = if (isTransmitting) Color(0xFF7F1D1D) else Color(0xFF1E2738),
                border = BorderStroke(
                    width = 4.dp,
                    brush = Brush.radialGradient(
                        colors = if (isTransmitting) {
                            listOf(TransmitRed, Color(0xFF991B1B))
                        } else {
                            listOf(TacticalAmberLight, TacticalAmberDark)
                        }
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = if (isTransmitting) {
                                    listOf(Color(0xFFDC2626), Color(0xFF991B1B), Color(0xFF450A0A))
                                } else {
                                    listOf(Color(0xFF2A364F), Color(0xFF1A2333), Color(0xFF0F172A))
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (!hasPermission) {
                                Icons.Default.MicOff
                            } else if (isTransmitting) {
                                if (!isMicEnabled) Icons.Default.MicOff else Icons.Default.Radio
                            } else if (!isMicEnabled) {
                                Icons.Default.MicOff
                            } else {
                                Icons.Default.Mic
                            },
                            contentDescription = "Push To Talk",
                            tint = if (isTransmitting) Color.White else if (!isMicEnabled) TransmitRed else TacticalAmber,
                            modifier = Modifier.size(iconSize)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (!hasPermission) {
                                "GRANT MIC"
                            } else if (isTransmitting) {
                                if (!isMicEnabled) "TX (MIC MUTED)" else "LIVE TX"
                            } else if (!isMicEnabled) {
                                "MIC MUTED"
                            } else {
                                "PUSH TO TALK"
                            },
                            color = if (isTransmitting) Color.White else if (!isMicEnabled) TransmitRed else TacticalAmberLight,
                            fontWeight = FontWeight.Black,
                            fontSize = titleTextSize,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (!hasPermission) {
                                "Permission needed"
                            } else if (!isMicEnabled) {
                                "Unmute mic in settings or top bar"
                            } else if (pttMode == PttMode.HOLD_TO_TALK) {
                                if (isTransmitting) "RELEASE TO END" else "HOLD TO SPEAK"
                            } else {
                                if (isTransmitting) "TAP TO STOP" else "TAP TO LOCK ON"
                            },
                            color = if (isTransmitting) Color(0xFFFFCDD2) else TextMuted,
                            fontSize = subTextSize,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
