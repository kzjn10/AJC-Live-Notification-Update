package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * NotificationHelper supporting Android 10+ (API 29+)
 * Uses latest Notification APIs on Android 16+ (API 36+)
 * Falls back to NotificationCompat for older versions
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "order_delivery_channel"
        const val NOTIFICATION_ID = 1001
        private const val API_36 = 36
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Order Delivery Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Live notifications for order delivery status updates"
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showOrderStatusNotification(status: OrderStatus) {
        val notification = buildNotification(status)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun buildNotification(status: OrderStatus): Notification {
        val remoteViews = createRemoteViews(status)
        val isOngoing = status != OrderStatus.DELIVERED && status != OrderStatus.CANCELED
        val contentIntent = createContentIntent()
        
        return if (Build.VERSION.SDK_INT >= API_36) {
            buildNotificationApi36(remoteViews, isOngoing, contentIntent)
        } else {
            buildNotificationCompat(remoteViews, isOngoing, contentIntent)
        }
    }
    
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createRemoteViews(status: OrderStatus): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_order_status)
        
        remoteViews.setTextViewText(R.id.notification_title, status.notificationTitle)
        remoteViews.setTextViewText(R.id.notification_subtitle, getArrivalTimeText())
        
        val segmentIds = listOf(
            R.id.progress_segment_1,
            R.id.progress_segment_2,
            R.id.progress_segment_3,
            R.id.progress_segment_4
        )
        
        segmentIds.forEachIndexed { index, viewId ->
            val isActive = index < status.segmentProgress
            val drawableRes = if (isActive) R.drawable.segment_active else R.drawable.segment_inactive
            remoteViews.setImageViewResource(viewId, drawableRes)
        }
        
        return remoteViews
    }
    
    @SuppressLint("MissingPermission")
    @RequiresApi(API_36)
    private fun buildNotificationApi36(
        remoteViews: RemoteViews,
        isOngoing: Boolean,
        contentIntent: PendingIntent
    ): Notification {
        val publicNotification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .build()

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setContentIntent(contentIntent)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPublicVersion(publicNotification)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setFullScreenIntent(contentIntent, true)
            .build()
    }
    
    private fun buildNotificationCompat(
        remoteViews: RemoteViews,
        isOngoing: Boolean,
        contentIntent: PendingIntent
    ): Notification {
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .build()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setContentIntent(contentIntent)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicNotification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(contentIntent, true)
            .build()
    }
    
    private fun getArrivalTimeText(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 20)
        val startTime = SimpleDateFormat("h:mm", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.MINUTE, 10)
        val endTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
        return "Arriving by: $startTime-$endTime"
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

enum class OrderStatus(
    val message: String,
    val detailedMessage: String,
    val progress: Int,
    val notificationTitle: String,
    val segmentProgress: Int
) {
    ORDER_PLACED(
        "Order Confirmed",
        "Your order has been confirmed and is being processed.",
        15,
        "Order Confirmed",
        1
    ),
    MERCHANT_ACCEPTED(
        "Preparing Your Order",
        "The merchant has accepted your order and is preparing it.",
        30,
        "Preparing Your Order",
        1
    ),
    DRIVER_ASSIGNED(
        "Driver On The Way",
        "A driver has been assigned and is on the way to pick up your order.",
        50,
        "Driver On The Way",
        2
    ),
    PICKED_UP(
        "Picked Up",
        "Your order has been picked up by the driver.",
        70,
        "Picked Up",
        3
    ),
    ARRIVING_SOON(
        "Arriving Soon",
        "Your order is almost there! Get ready to receive it.",
        85,
        "Arriving Soon",
        3
    ),
    DELIVERED(
        "Delivered",
        "Your order has been delivered. Enjoy!",
        100,
        "Delivered",
        4
    ),
    CANCELED(
        "Order Canceled",
        "Your order has been canceled. Sorry!",
        100,
        "Order Canceled",
        0
    )
}
