package com.geronimo.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Keeps the USB and BLE phone links available while Geronimo runs in the background. */
class GeronimoConnectionService : Service() {
    companion object {
        private const val CHANNEL_ID = "geronimo_connection"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_STOP = "com.geronimo.companion.STOP_CONNECTION"
        const val ACTION_RESTART_BLUETOOTH = "com.geronimo.companion.RESTART_BLUETOOTH"
    }
    private var bridge: BluetoothBridge? = null

    override fun onCreate() {
        super.onCreate()
        AppContext.application = applicationContext
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting USB and Bluetooth links…"))
        UsbBridge.start(applicationContext) { message ->
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
        }
        startBluetoothBridge()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART_BLUETOOTH -> startBluetoothBridge()
        }
        return START_STICKY
    }

    private fun startBluetoothBridge() {
        bridge?.stop()
        bridge = BluetoothBridge(applicationContext) { message ->
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
        }
        try {
            bridge?.start()
        } catch (error: SecurityException) {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification("USB ready · Bluetooth permission not granted")
            )
        } catch (error: Exception) {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification("USB ready · Bluetooth could not start: ${error.message}")
            )
        }
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
        UsbBridge.stop()
        bridge?.stop()
        bridge = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}