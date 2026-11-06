package com.geronimo.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Receives cellular call events when Geronimo is selected as the default phone app.
 * Incoming calls are never answered automatically. The notification only opens
 * a screen where the user must tap Answer.
 */
class GeronimoInCallService : InCallService() {
    companion object {
        @Volatile
        var activeCall: Call? = null
            private set
        private const val CHANNEL_ID = "geronimo_calls"
        private const val NOTIFICATION_ID = 4101
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) {
                activeCall = null
                NotificationManagerCompat.from(this@GeronimoInCallService).cancel(NOTIFICATION_ID)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call
        call.registerCallback(callback)
        if (call.state == Call.STATE_RINGING) showIncomingCallNotification(call)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        if (activeCall === call) activeCall = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onCallRemoved(call)
    }

    private fun showIncomingCallNotification(call: Call) {
        val handle = call.details.handle?.schemeSpecificPart ?: "Unknown caller"
        val openCallScreen = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 4101, openCallScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText(handle)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notifications may be disabled; calls remain controlled by Android.
        }
    }
}
