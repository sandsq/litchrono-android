package com.example.litchrono

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

class QuoteUpdateService : Service() {
    private var tickReceiver: BroadcastReceiver? = null
    private val CHANNEL_ID = "litchrono_widget_channel"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("LitchronoWidgetService", "onCreate: registering tick receiver")
        createNotificationChannel()

        // Register ACTION_TIME_TICK to run each minute
        tickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_TIME_TICK) {
                    android.util.Log.d("LitchronoWidgetService", "ACTION_TIME_TICK received")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisAppWidget = ComponentName(context.packageName, QuoteWidgetProvider::class.java.name)
                    val ids = appWidgetManager.getAppWidgetIds(thisAppWidget)
                    for (appWidgetId in ids) {
                        QuoteWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        registerReceiver(tickReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("LitchronoWidgetService", "onStartCommand: starting foreground and updating widgets")
        // Start foreground notification so service is kept alive
        startForeground(NOTIF_ID, buildNotification())

        // Mark service running in shared prefs for quick verification
        try {
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("widget_service_running", true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Do an immediate update when service starts
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val thisAppWidget = ComponentName(packageName, QuoteWidgetProvider::class.java.name)
        val ids = appWidgetManager.getAppWidgetIds(thisAppWidget)
        for (appWidgetId in ids) {
            QuoteWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("LitchronoWidgetService", "onDestroy: unregistering tick receiver")
        try {
            if (tickReceiver != null) {
                unregisterReceiver(tickReceiver)
                tickReceiver = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("widget_service_running", false).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Litchrono Widget", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Keeps the quote widget synchronized each minute"
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        @Suppress("DEPRECATION")
        return builder
            .setContentTitle("Litchrono widget")
            .setContentText("Updating quotes every minute")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }
}
