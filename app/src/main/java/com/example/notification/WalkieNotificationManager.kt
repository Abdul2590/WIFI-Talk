package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicInteger

class WalkieNotificationManager(private val context: Context) {
    private val tag = "WalkieNotificationMgr"

    companion object {
        const val CHANNEL_ID_CHAT = "walkie_chat_channel"
        const val CHANNEL_NAME_CHAT = "Walkie Talkie Chat"
        const val EXTRA_OPEN_CHAT = "extra_open_chat"
        private val notificationIdCounter = AtomicInteger(1001)
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID_CHAT,
                    CHANNEL_NAME_CHAT,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts and messages received over the Walkie-Talkie network"
                    enableLights(true)
                    lightColor = Color.CYAN
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200)
                    setShowBadge(true)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(tag, "Failed to create notification channel", e)
            }
        }
    }

    /**
     * Displays a system notification in the notification center for an incoming chat message.
     */
    fun showChatNotification(
        messageId: String,
        senderName: String,
        senderCallSign: String,
        text: String,
        channelId: Int,
        isDirect: Boolean
    ) {
        try {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            if (!notificationManagerCompat.areNotificationsEnabled()) {
                Log.w(tag, "Notifications are disabled by the user or system")
                return
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_CHAT, true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                messageId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val displayTitle = if (isDirect) {
                "Direct message from $senderName"
            } else {
                "$senderName [CH-$channelId]"
            }

            val subText = if (senderCallSign.isNotBlank()) senderCallSign else "CH-$channelId"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAT)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(displayTitle)
                .setContentText(text)
                .setSubText(subText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(displayTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 200, 100, 200))
                .build()

            // Stable notification ID derived from messageId
            val notificationId = (messageId.hashCode() and 0x7FFFFFFF) % 10000 + 1000
            notificationManagerCompat.notify(notificationId, notification)
            Log.d(tag, "Posted notification for message from $senderName")
        } catch (e: SecurityException) {
            Log.w(tag, "Missing POST_NOTIFICATIONS permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to display chat notification", e)
        }
    }

    /**
     * Clears all active walkie talkie chat notifications.
     */
    fun clearChatNotifications() {
        try {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.cancelAll()
        } catch (e: Exception) {
            Log.w(tag, "Error clearing notifications: ${e.message}")
        }
    }
}
