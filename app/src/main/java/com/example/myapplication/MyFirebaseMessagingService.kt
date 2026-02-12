package com.example.myapplication

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data payload: ${remoteMessage.data}")

        if (remoteMessage.data.isNotEmpty()) {
            handleDataPayload(remoteMessage.data)
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val orderStatus = data["order_status"]
        val statusOrdinal = data["status_ordinal"]?.toIntOrNull()
        val logoUrl = data["logo_url"]
        
        if (orderStatus != null || statusOrdinal != null) {
            updateOrderStatus(statusOrdinal, orderStatus, logoUrl)
        }
    }

    private fun updateOrderStatus(statusOrdinal: Int?, statusName: String?, logoUrl: String?) {
        val status = when {
            statusOrdinal != null && statusOrdinal in OrderStatus.entries.indices -> {
                OrderStatus.entries[statusOrdinal]
            }
            statusName != null -> {
                try {
                    OrderStatus.valueOf(statusName)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid order status: $statusName")
                    null
                }
            }
            else -> null
        }

        status?.let {
            Log.d(TAG, "Updating order status to: ${it.name}")
            if (!logoUrl.isNullOrEmpty()) {
                Log.d(TAG, "Logo URL: $logoUrl")
            }
            
            OrderTrackingService.currentStatusIndex.set(it.ordinal)
            
            val notificationHelper = NotificationHelper(this)
            notificationHelper.showOrderStatusNotification(it, logoUrl)
            
            val broadcastIntent = Intent("com.example.myapplication.ORDER_STATUS_UPDATE")
            broadcastIntent.putExtra("status_ordinal", it.ordinal)
            broadcastIntent.setPackage(packageName)
            sendBroadcast(broadcastIntent)
            
            if (it == OrderStatus.CANCELED) {
                if (OrderTrackingService.isRunning) {
                    val stopIntent = Intent(this, OrderTrackingService::class.java).apply {
                        action = OrderTrackingService.ACTION_STOP
                    }
                    startService(stopIntent)
                }
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    notificationHelper.cancelNotification()
                    Log.d(TAG, "Notification dismissed for CANCELED order")
                }, 3000)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        Log.d(TAG, "FCM token $token")
    }
}
