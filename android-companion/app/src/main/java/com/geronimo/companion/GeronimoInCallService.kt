package com.geronimo.companion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Cellular call integration. Incoming calls are never answered without an explicit tap. */
class GeronimoInCallService : InCallService() {
    companion object {
        @Volatile var activeCall: Call? = null
            private set
        @Volatile private var appContext: Context? = null
        @Volatile private var serviceInstance: GeronimoInCallService? = null
        private const val CHANNEL_ID = "geronimo_calls"
        private const val NOTIFICATION_ID = 4101
        private var ringtonePlayer: MediaPlayer? = null

        /** Only a locally trusted outgoing contact may skip the system dialer confirmation. */
        fun placeCall(number: String, allowlisted: Boolean, contactName: String = "") {
            val context = appContext ?: return
            val safeNumber = number.filter { it.isDigit() || it in "+*#" }
            if (safeNumber.length < 3) {
                CallBridge.send(JSONObject().put("event", "CALL_ERROR").put("message", "Enter a valid phone number."))
                return
            }
            val uri = Uri.fromParts("tel", safeNumber, null)
            try {
                val mayCallDirectly = allowlisted &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                if (mayCallDirectly) {
                    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    telecom.placeCall(uri, Bundle())
                } else {
                    context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                CallBridge.send(JSONObject().put("event", "CALL_DIAL_REQUESTED")
                    .put("number", safeNumber).put("name", contactName))
            } catch (error: Exception) {
                CallBridge.send(JSONObject().put("event", "CALL_ERROR")
                    .put("message", error.message ?: "The phone could not start the call."))
            }
        }

        fun setMutedFromUi(muted: Boolean) { serviceInstance?.setMuted(muted) }
        fun setSpeakerFromUi(enabled: Boolean) {
            serviceInstance?.setAudioRoute(if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
        }

        private fun stopRingtone() {
            try {
                ringtonePlayer?.let { player ->
                    if (player.isPlaying) player.stop()
                    player.release()
                }
            } catch (_: Exception) { }
            ringtonePlayer = null
        }
    }

    private val callbacks = ConcurrentHashMap<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        serviceInstance = this
        createCallChannel()
    }

    override fun onDestroy() {
        stopRingtone()
        if (serviceInstance === this) serviceInstance = null
        if (activeCall == null) appContext = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call
        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) = publishCallState(changedCall, state)
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        publishCallState(call, call.state)
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        if (activeCall === call) activeCall = null
        stopRingtone()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        CallBridge.send(JSONObject().put("event", "CALL_ENDED"))
        super.onCallRemoved(call)
    }

    private fun publishCallState(call: Call, state: Int) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown number"
        when (state) {
            Call.STATE_RINGING -> {
                startRingtone(incoming = true)
                showCallNotification("Incoming call", number)
                CallBridge.send(JSONObject().put("event", "CALL_RINGING").put("number", number))
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                startRingtone(incoming = false)
                showCallNotification("Calling", number)
                CallBridge.send(JSONObject().put("event", "CALL_DIALING").put("number", number))
            }
            Call.STATE_ACTIVE -> {
                stopRingtone()
                showCallNotification("Call in progress", number)
                CallBridge.send(JSONObject().put("event", "CALL_ACTIVE").put("number", number))
            }
            Call.STATE_DISCONNECTED -> {
                stopRingtone()
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
                CallBridge.send(JSONObject().put("event", "CALL_ENDED"))
            }
        }
    }

    private fun startRingtone(incoming: Boolean) {
        stopRingtone()
        try {
            val soundRes = if (incoming) R.raw.incoming_ringtone else R.raw.outgoing_ringtone
            val player = MediaPlayer.create(this, soundRes) ?: return
            player.isLooping = true
            ringtonePlayer = player
            player.start()
        } catch (_: Exception) {
            stopRingtone()
        }
    }

    private fun createCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Geronimo incoming calls", NotificationManager.IMPORTANCE_HIGH
            ))
        }
    }

    private fun showCallNotification(title: String, number: String) {
        val openCallScreen = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, openCallScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(number)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        try { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* Notifications can be denied in Android settings. */ }
    }
}
