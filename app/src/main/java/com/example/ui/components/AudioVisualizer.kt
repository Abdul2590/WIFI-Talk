package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.TransmissionState
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TransmitRed
import kotlin.random.Random

@Composable
fun AudioVisualizer(
    audioLevel: Float,
    transmissionState: TransmissionState,
    modifier: Modifier = Modifier
) {
    val barCount = 28

    // Fixed seeds for each bar to create realistic natural equalizer contour
    val barFrequencies = remember {
        FloatArray(barCount) { index ->
            val center = barCount / 2f
            val dist = kotlin.math.abs(index - center) / center
            // Bell curve shape with slight variations
            (1f - dist * 0.55f) + (Random.nextFloat() * 0.15f - 0.075f)
        }
    }

    val animatedLevel by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing),
        label = "audio_level_anim"
    )

    val activeColor = when (transmissionState) {
        is TransmissionState.Transmitting -> TransmitRed
        is TransmissionState.Receiving -> SignalGreen
        is TransmissionState.Idle -> TacticalAmber.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070B12))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("audio_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until barCount) {
                val weight = barFrequencies[i]
                val isActive = transmissionState !is TransmissionState.Idle

                // Base height plus scaled audio level
                val rawHeightFactor = if (isActive) {
                    (0.12f + animatedLevel * weight * 0.88f).coerceIn(0.08f, 1.0f)
                } else {
                    0.08f
                }

                val barColor = if (isActive) {
                    when {
                        rawHeightFactor > 0.75f -> TransmitRed
                        rawHeightFactor > 0.45f -> TacticalAmber
                        else -> activeColor
                    }
                } else {
                    Color(0xFF1E293B)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .fillMaxHeight(rawHeightFactor)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
            }
        }
    }
}
