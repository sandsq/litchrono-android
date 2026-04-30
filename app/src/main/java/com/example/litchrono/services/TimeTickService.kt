package com.example.litchrono.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.litchrono.QuoteWidget

class TimeTickService : Service() {
    companion object {
        private const val TAG = "TimeTickService"
        private const val CHANNEL_ID = "litchrono_time_tick"
        private const val NOTIF_ID = 1001
    }

    private var receiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        registerTickReceiver()
        Log.d(TAG, "onCreate: service started and receiver registered")
    }

    override fun onDestroy() {
        unregisterTickReceiver()
        stopForeground(true)
        super.onDestroy()
        Log.d(TAG, "onDestroy: service stopped and receiver unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Time Tick Service", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        builder.setContentTitle("Litchrono")
            .setContentText("Keeping clock widget in sync")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
        return builder.build()
    }

    private fun registerTickReceiver() {
        try {
            if (receiver == null) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                }
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent?) {
                        var action = intent?.action
                        Log.d(TAG, "tick received: $action")
                        // Forward to widget provider
                        val forward = Intent(context, QuoteWidget::class.java).apply {
                            action = QuoteWidget.ACTION_UPDATE
                            putExtra(QuoteWidget.EXTRA_FROM_TICK, true)
                            putExtra(QuoteWidget.EXTRA_LAST_SCHEDULED, System.currentTimeMillis())
                        }
                        context.sendBroadcast(forward)
                    }
                }
                applicationContext.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "registerTickReceiver failed", e)
        }
    }

    private fun unregisterTickReceiver() {
        try {
            receiver?.let { applicationContext.unregisterReceiver(it) }
            receiver = null
        } catch (e: Exception) {
            Log.w(TAG, "unregisterTickReceiver failed", e)
        }
    }
}
