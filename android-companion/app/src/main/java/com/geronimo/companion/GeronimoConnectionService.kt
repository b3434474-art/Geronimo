package com.geronimo.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Keeps the BLE phone link available while Geronimo runs in the background. */
class GeronimoConnectionService : Service() {
    companion object {
        private const val CHANNEL_ID = "geronimo_connection"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_STOP = "com.geronimo.companion.STOP_CONNECTION"
    }
    private var bridge: BluetoothBridge? = null

    override fun onCreate() {
        super.onCreate()
        AppContext.application = applicationContext
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting Bluetooth connection…"))
        bridge = BluetoothBridge(applicationContext) { message ->
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
        }
        bridge?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Geronimo phone connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Geronimo phone link")
            .setContentText(message.take(120))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    override fun onDestroy() {
        bridge?.stop()
        bridge = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}