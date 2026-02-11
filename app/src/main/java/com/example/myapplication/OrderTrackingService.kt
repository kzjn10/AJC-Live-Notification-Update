package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

class OrderTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.example.myapplication.START_TRACKING"
        const val ACTION_STOP = "com.example.myapplication.STOP_TRACKING"
        private const val UPDATE_INTERVAL_MS = 5000L
        
        var isRunning = false
            private set
        
        var currentStatusIndex = AtomicInteger(0)
            private set
    }

    private lateinit var notificationHelper: NotificationHelper
    private val handler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (isRunning) return
        
        isRunning = true
        currentStatusIndex.set(0)
        
        val statuses = OrderStatus.entries.toTypedArray()
        val initialStatus = statuses[0]
        
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notificationHelper.buildNotification(initialStatus)
        )

        statusRunnable = object : Runnable {
            override fun run() {
                val index = currentStatusIndex.get()
                if (index < statuses.size) {
                    val status = statuses[index]
                    
                    if (status == OrderStatus.CANCELED) {
                        notificationHelper.showOrderStatusNotification(status)
                        sendStatusBroadcast(status)
                        handler.postDelayed({
                            stopTracking()
                        }, 3000)
                        return
                    }
                    
                    notificationHelper.showOrderStatusNotification(status)
                    sendStatusBroadcast(status)
                    
                    if (status == OrderStatus.DELIVERED) {
                        handler.postDelayed({
                            stopTracking()
                        }, 1000)
                        return
                    }
                    
                    currentStatusIndex.incrementAndGet()
                    
                    if (currentStatusIndex.get() < statuses.size) {
                        handler.postDelayed(this, UPDATE_INTERVAL_MS)
                    } else {
                        handler.postDelayed({
                            stopTracking()
                        }, 1000)
                    }
                }
            }
        }
        handler.post(statusRunnable!!)
    }

    private fun sendStatusBroadcast(status: OrderStatus) {
        val broadcastIntent = Intent("com.example.myapplication.ORDER_STATUS_UPDATE")
        broadcastIntent.putExtra("status_ordinal", status.ordinal)
        broadcastIntent.setPackage(packageName)
        sendBroadcast(broadcastIntent)
    }

    private fun stopTracking() {
        statusRunnable?.let { handler.removeCallbacks(it) }
        statusRunnable = null
        isRunning = false
        currentStatusIndex.set(0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        statusRunnable?.let { handler.removeCallbacks(it) }
        isRunning = false
    }
}
