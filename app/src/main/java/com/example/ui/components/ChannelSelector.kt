package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WalkieChannel
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalAmberLight
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun ChannelSelector(
    currentChannel: WalkieChannel,
    onSelectChannel: (WalkieChannel) -> Unit,
    onOpenChannelSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentIndex = WalkieChannel.ALL_CHANNELS.indexOfFirst { it.id == currentChannel.id }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .testTag("channel_selector"),
        color = TacticalSurface,
        border = BorderStroke(1.dp, TacticalSurfaceHighlight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous Channel Button
            IconButton(
                onClick = {
                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else WalkieChannel.ALL_CHANNELS.lastIndex
                    onSelectChannel(WalkieChannel.ALL_CHANNELS[prevIndex])
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E283A))
                    .testTag("prev_channel_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Channel",
                    tint = TacticalAmber
                )
            }

            // Channel Title & Quick Channel Grid Trigger
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenChannelSheet() }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TUNED: CH-${currentChannel.id}",
                        color = TacticalAmberLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Channels Grid",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = currentChannel.frequency,
                    color = RadioCyan,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            // Next Channel Button
            IconButton(
                onClick = {
                    val nextIndex = if (currentIndex < WalkieChannel.ALL_CHANNELS.lastIndex) currentIndex + 1 else 0
                    onSelectChannel(WalkieChannel.ALL_CHANNELS[nextIndex])
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E283A))
                    .testTag("next_channel_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Channel",
                    tint = TacticalAmber
                )
            }
        }
    }
}
