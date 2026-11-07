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
import android.provider.ContactsContract
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
        @Volatile var activeCallerName: String = ""
            private set
        @Volatile private var appContext: Context? = null
        @Volatile private var serviceInstance: GeronimoInCallService? = null
        @Volatile private var pendingDialName: String = ""
        private const val CHANNEL_ID = "geronimo_calls"
        private const val NOTIFICATION_ID = 4101
        private var ringtonePlayer: MediaPlayer? = null

        /** A saved number may skip the dialer only when Android's CALL_PHONE permission is granted. */
        fun placeCall(number: String, contactName: String = "") {
            val context = AppContext.application ?: appContext ?: return
            val safeNumber = number.filter { it.isDigit() || it in "+*#" }
            if (safeNumber.length < 3) {
                CallBridge.send(JSONObject().put("event", "CALL_ERROR").put("message", "Enter a valid phone number."))
                return
            }
            val uri = Uri.fromParts("tel", safeNumber, null)
            try {
                val trusted = PhoneAllowlist.isAllowed(context, safeNumber)
                val mayCallDirectly = trusted &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                pendingDialName = contactName.ifBlank { PhoneAllowlist.nameFor(context, safeNumber).orEmpty() }
                if (mayCallDirectly) {
                    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    telecom.placeCall(uri, Bundle())
                } else {
                    context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                CallBridge.send(JSONObject().put("event", "CALL_DIAL_REQUESTED")
                    .put("number", safeNumber).put("name", pendingDialName).put("direct", mayCallDirectly))
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
        AppContext.application = applicationContext
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
            override fun onDetailsChanged(changedCall: Call, details: Call.Details) = publishCallState(changedCall, changedCall.state)
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        publishCallState(call, call.state)
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        if (activeCall === call) {
            activeCall = null
            activeCallerName = ""
        }
        stopRingtone()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        CallBridge.send(JSONObject().put("event", "CALL_ENDED"))
        super.onCallRemoved(call)
    }

    private fun publishCallState(call: Call, state: Int) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown number"
        val name = resolveCallerName(call, number)
        activeCallerName = name
        when (state) {
            Call.STATE_RINGING -> {
                startRingtone(incoming = true)
                showCallNotification("Incoming call", if (name.isNotBlank()) "$name · $number" else number)
                CallBridge.send(JSONObject().put("event", "CALL_RINGING").put("number", number).put("name", name))
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                startRingtone(incoming = false)
                showCallNotification("Calling", if (name.isNotBlank()) "$name · $number" else number)
                CallBridge.send(JSONObject().put("event", "CALL_DIALING").put("number", number).put("name", name))
            }
            Call.STATE_ACTIVE -> {
                stopRingtone()
                showCallNotification("Call in progress", if (name.isNotBlank()) "$name · $number" else number)
                CallBridge.send(JSONObject().put("event", "CALL_ACTIVE").put("number", number).put("name", name))
            }
            Call.STATE_DISCONNECTED -> {
                stopRingtone()
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
                CallBridge.send(JSONObject().put("event", "CALL_ENDED"))
            }
        }
    }

    private fun resolveCallerName(call: Call, number: String): String {
        call.details.callerDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
        PhoneAllowlist.nameFor(this, number)?.takeIf { it.isNotBlank() }?.let { return it }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return ""
        return try {
            val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(lookup, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        } catch (_: Exception) { "" }
    }

    private fun startRingtone(incoming: Boolean) {
        stopRingtone()
        try {
            val soundRes = if (incoming) R.raw.incoming_ringtone else R.raw.outgoing_ringtone
            val player = MediaPlayer.create(this, soundRes) ?: return
            player.isLooping = true
            ringtonePlayer = player
            player.start()
        } catch (_: Exception) { stopRingtone() }
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
        catch (_: SecurityException) { }
    }
}