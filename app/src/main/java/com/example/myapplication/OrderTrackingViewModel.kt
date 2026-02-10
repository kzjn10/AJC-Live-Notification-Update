package com.example.myapplication

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OrderTrackingViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val KEY_IS_TRACKING = "is_tracking"
    }

    private val _currentStatus = MutableStateFlow<OrderStatus?>(null)
    val currentStatus: StateFlow<OrderStatus?> = _currentStatus.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ordinal = intent?.getIntExtra("status_ordinal", -1) ?: -1
            if (ordinal >= 0 && ordinal < OrderStatus.entries.size) {
                val status = OrderStatus.entries[ordinal]
                _currentStatus.value = status
                if (status == OrderStatus.DELIVERED) {
                    _isTracking.value = false
                    savedStateHandle[KEY_IS_TRACKING] = false
                }
            }
        }
    }

    init {
        val filter = IntentFilter("com.example.myapplication.ORDER_STATUS_UPDATE")
        ContextCompat.registerReceiver(
            application,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        if (OrderTrackingService.isRunning) {
            _isTracking.value = true
            val index = OrderTrackingService.currentStatusIndex.get()
            if (index > 0 && index <= OrderStatus.entries.size) {
                _currentStatus.value = OrderStatus.entries[index - 1]
            }
        }
    }

    fun startTracking() {
        if (_isTracking.value) return
        
        _isTracking.value = true
        savedStateHandle[KEY_IS_TRACKING] = true
        _currentStatus.value = null
        
        val intent = Intent(getApplication(), OrderTrackingService::class.java).apply {
            action = OrderTrackingService.ACTION_START
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopTracking() {
        _isTracking.value = false
        savedStateHandle[KEY_IS_TRACKING] = false
        
        val intent = Intent(getApplication(), OrderTrackingService::class.java).apply {
            action = OrderTrackingService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun resetTracking() {
        stopTracking()
        _currentStatus.value = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(statusReceiver)
        } catch (_: Exception) { }
    }
}
