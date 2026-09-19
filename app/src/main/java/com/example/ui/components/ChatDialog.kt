package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.PeerDevice
import com.example.model.UserProfile
import com.example.model.WalkieChannel
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalDarkBg
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransmitRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDialog(
    userProfile: UserProfile,
    messages: List<ChatMessage>,
    peers: List<PeerDevice>,
    currentChannel: WalkieChannel,
    onSendMessage: (String, String?) -> Unit,
    onOpenProfileSetup: () -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var selectedRecipientId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .testTag("chat_dialog")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = TacticalDarkBg,
            border = BorderStroke(1.dp, TacticalSurfaceHighlight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
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
                                .background(RadioCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = null,
                                tint = RadioCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TACTICAL MESSAGING",
                                color = RadioCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (userProfile.isMessagingEnabled) {
                                    "${userProfile.userName} • ${userProfile.mobileNumber}"
                                } else {
                                    "Messaging Disabled • Profile Required"
                                },
                                color = if (userProfile.isMessagingEnabled) SignalGreen else TransmitRed,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row {
                        if (messages.isNotEmpty()) {
                            IconButton(onClick = onClearChat) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Chat",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Profile Verification Status Gate
                if (!userProfile.isMessagingEnabled || !userProfile.isValid) {
                    // Locked Overlay / Call-to-action to register profile
                    MessagingGateCard(
                        onOpenProfileSetup = onOpenProfileSetup
                    )
                } else {
                    // Recipient Selector Filter (Broadcast vs Direct Peer)
                    RecipientSelectorRow(
                        currentChannel = currentChannel,
                        peers = peers,
                        selectedRecipientId = selectedRecipientId,
                        onSelectRecipient = { selectedRecipientId = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Messages List
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(TacticalSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, TacticalSurfaceHighlight, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        if (messages.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No messages yet",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Send a text to devices on CH-${currentChannel.id} or tap a peer above",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        } else {
                            val filteredMessages = if (selectedRecipientId == null) {
                                messages
                            } else {
                                messages.filter {
                                    (it.senderId == selectedRecipientId && it.recipientId != null) ||
                                            (it.isFromMe && it.recipientId == selectedRecipientId) ||
                                            (it.recipientId == null)
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                items(filteredMessages, key = { it.id }) { msg ->
                                    ChatMessageBubble(msg = msg)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            placeholder = {
                                Text(
                                    text = if (selectedRecipientId == null) "Message CH-${currentChannel.id}..." else "Direct message...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (messageText.isNotBlank()) {
                                        onSendMessage(messageText, selectedRecipientId)
                                        messageText = ""
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RadioCyan,
                                unfocusedBorderColor = TacticalSurfaceHighlight,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = TacticalSurface,
                                unfocusedContainerColor = TacticalSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText, selectedRecipientId)
                                    messageText = ""
                                }
                            },
                            enabled = messageText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadioCyan,
                                contentColor = Color.Black,
                                disabledContainerColor = TacticalSurfaceHighlight,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_chat_button"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessagingGateCard(
    onOpenProfileSetup: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TacticalAmber.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(TacticalAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = TacticalAmber,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "PROFILE SETUP REQUIRED",
                color = TacticalAmber,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Per system security requirements, you must register your Name and Mobile Number before messaging services can be enabled on this device.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onOpenProfileSetup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalAmber,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("setup_profile_button")
            ) {
                Icon(
                    Icons.Default.Badge,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("SET NAME & MOBILE NUMBER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun RecipientSelectorRow(
    currentChannel: WalkieChannel,
    peers: List<PeerDevice>,
    selectedRecipientId: String?,
    onSelectRecipient: (String?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Broadcast to channel pill
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSelectRecipient(null) },
            color = if (selectedRecipientId == null) RadioCyan.copy(alpha = 0.2f) else TacticalSurface,
            border = BorderStroke(
                1.dp,
                if (selectedRecipientId == null) RadioCyan else TacticalSurfaceHighlight
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALL (CH-${currentChannel.id})",
                    color = if (selectedRecipientId == null) RadioCyan else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Available online peers
        peers.take(3).forEach { peer ->
            val isSelected = selectedRecipientId == peer.id
            val displayName = if (peer.userName.isNotBlank()) peer.userName else peer.callSign
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectRecipient(if (isSelected) null else peer.id) },
                color = if (isSelected) TacticalAmber.copy(alpha = 0.2f) else TacticalSurface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) TacticalAmber else TacticalSurfaceHighlight
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (peer.isMessagingEnabled) SignalGreen else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayName.take(10),
                        color = if (isSelected) TacticalAmber else TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(msg: ChatMessage) {
    val isMe = msg.isFromMe
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(msg.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // Sender Metadata Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isMe) "YOU" else "${msg.senderName} (${msg.senderCallSign})",
                color = if (isMe) RadioCyan else TacticalAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (!isMe && msg.senderMobile.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${msg.senderMobile}",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = timeStr,
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Message Bubble
        Surface(
            modifier = Modifier.clip(
                RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isMe) 12.dp else 2.dp,
                    bottomEnd = if (isMe) 2.dp else 12.dp
                )
            ),
            color = if (isMe) Color(0xFF0F3642) else TacticalSurfaceHighlight,
            border = BorderStroke(
                1.dp,
                if (isMe) RadioCyan.copy(alpha = 0.5f) else Color.Transparent
            )
        ) {
            Text(
                text = msg.text,
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
