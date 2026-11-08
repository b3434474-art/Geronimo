package com.geronimo.companion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
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
            serviceInstance?.setSpeakerRoute(enabled)
        }

        fun requestCallEndpointFromUi(identifier: String) {
            val service = serviceInstance
            if (service == null) {
                CallBridge.send(JSONObject().put("event", "CALL_AUDIO_ROUTE_RESULT")
                    .put("ok", false).put("message", "The Android call service is not available."))
            } else {
                service.requestCallEndpoint(identifier)
            }
        }

        fun refreshCallStateFromUi() {
            serviceInstance?.publishCurrentCallSnapshot()
        }
    }

    private val callbacks = ConcurrentHashMap<Call, Call.Callback>()
    private val lastPublishedEventByCall = ConcurrentHashMap<Call, String>()
    private val callEndpoints = ConcurrentHashMap<String, CallEndpoint>()
    private var ringtonePlayer: MediaPlayer? = null
    private var ringtoneMode: String = ""
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasSentCallEnded = true

    private val ringtoneFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            stopRingtone()
        }
    }

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
        hasSentCallEnded = false
        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                publishCallState(changedCall, state)
            }

            override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
                publishCallState(changedCall, changedCall.state)
            }
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        publishCallState(call, call.state)
        sendCallAudioEndpoints()
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        lastPublishedEventByCall.remove(call)

        if (activeCall === call) {
            val replacement = callbacks.keys.firstOrNull {
                it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
            }
            activeCall = replacement
            if (replacement != null) {
                publishCallState(replacement, replacement.state)
            } else {
                activeCallerName = ""
                stopRingtone()
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
                publishCallEndedOnce()
            }
        }
        super.onCallRemoved(call)
    }

    private fun publishCallEndedOnce() {
        if (hasSentCallEnded) return
        hasSentCallEnded = true
        CallBridge.send(JSONObject().put("event", "CALL_ENDED"))
    }

    private fun publishCallState(call: Call, state: Int) {
        if (state == Call.STATE_DISCONNECTING) {
            stopRingtone()
            if (activeCall === call) {
                CallBridge.send(JSONObject().put("event", "CALL_ENDING"))
            }
            return
        }

        if (state == Call.STATE_DISCONNECTED) {
            stopRingtone()
            if (activeCall === call) {
                val replacement = callbacks.keys.firstOrNull {
                    it !== call && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
                }
                activeCall = replacement
                if (replacement != null) {
                    publishCallState(replacement, replacement.state)
                } else {
                    activeCallerName = ""
                    NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
                    publishCallEndedOnce()
                }
            }
            return
        }

        activeCall = call
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown number"
        val name = resolveCallerName(call, number)
        activeCallerName = name
        val fingerprint = "$state|$number|$name"
        if (lastPublishedEventByCall.put(call, fingerprint) == fingerprint) return

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
            Call.STATE_HOLDING -> {
                stopRingtone()
                CallBridge.send(JSONObject().put("event", "CALL_HOLDING").put("number", number).put("name", name))
            }
        }
    }

    /** Re-send the existing call state when a transport reconnects. */
    private fun publishCurrentCallSnapshot() {
        val call = activeCall ?: return
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown number"
        val name = resolveCallerName(call, number)
        activeCallerName = name
        val event = when (call.state) {
            Call.STATE_RINGING -> "CALL_RINGING"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "CALL_DIALING"
            Call.STATE_ACTIVE -> "CALL_ACTIVE"
            Call.STATE_HOLDING -> "CALL_HOLDING"
            Call.STATE_DISCONNECTING -> "CALL_ENDING"
            else -> return
        }
        CallBridge.send(JSONObject().put("event", event).put("number", number).put("name", name))
        sendCallAudioEndpoints()
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
        val requestedMode = if (incoming) "INCOMING" else "OUTGOING"
        if (ringtoneMode == requestedMode && ringtonePlayer?.isPlaying == true) return
        stopRingtone()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audioManager = getSystemService(AudioManager::class.java)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(ringtoneFocusListener)
            .build()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return

        audioFocusRequest = focusRequest
        try {
            val soundRes = if (incoming) R.raw.incoming_ringtone else R.raw.outgoing_ringtone
            val player = MediaPlayer.create(this, soundRes, attributes, 0) ?: run {
                stopRingtone()
                return
            }
            player.isLooping = true
            ringtonePlayer = player
            ringtoneMode = requestedMode
            player.start()
        } catch (_: Exception) {
            stopRingtone()
        }
    }

    private fun stopRingtone() {
        try {
            ringtonePlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        } catch (_: Exception) { }
        ringtonePlayer = null
        ringtoneMode = ""
        audioFocusRequest?.let { request ->
            try {
                getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request)
            } catch (_: Exception) { }
        }
        audioFocusRequest = null
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        callEndpoints.clear()
        availableEndpoints.forEach { endpoint ->
            callEndpoints[endpoint.identifier.toString()] = endpoint
        }
        sendCallAudioEndpoints()
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        sendCallAudioEndpoints()
    }

    private fun endpointTypeName(type: Int): String = when (type) {
        CallEndpoint.TYPE_EARPIECE -> "EARPIECE"
        CallEndpoint.TYPE_BLUETOOTH -> "BLUETOOTH"
        CallEndpoint.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        CallEndpoint.TYPE_SPEAKER -> "SPEAKER"
        CallEndpoint.TYPE_STREAMING -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    private fun sendCallAudioEndpoints() {
        if (Build.VERSION.SDK_INT < 34) return
        try {
            val current = getCurrentCallEndpoint()
            val available = JSONArray()
            callEndpoints.values
                .sortedWith(compareBy<CallEndpoint> { it.endpointType }.thenBy { it.endpointName.toString() })
                .forEach { endpoint ->
                    available.put(JSONObject()
                        .put("id", endpoint.identifier.toString())
                        .put("name", endpoint.endpointName.toString())
                        .put("type", endpoint.endpointType)
                        .put("typeName", endpointTypeName(endpoint.endpointType)))
                }
            CallBridge.send(JSONObject()
                .put("event", "CALL_AUDIO_ENDPOINTS")
                .put("available", available)
                .put("currentId", current.identifier.toString())
                .put("currentName", current.endpointName.toString())
                .put("currentType", current.endpointType)
                .put("currentTypeName", endpointTypeName(current.endpointType)))
        } catch (_: Exception) {
            // Telecom may not have a current endpoint until a call is active.
        }
    }

    private fun requestCallEndpoint(identifier: String) {
        if (Build.VERSION.SDK_INT < 34) {
            CallBridge.send(JSONObject().put("event", "CALL_AUDIO_ROUTE_RESULT")
                .put("ok", false).put("message", "Audio route selection requires Android 14 or newer."))
            return
        }
        val endpoint = callEndpoints[identifier]
        if (endpoint == null) {
            sendCallAudioEndpoints()
            CallBridge.send(JSONObject().put("event", "CALL_AUDIO_ROUTE_RESULT")
                .put("ok", false).put("message", "That audio device is no longer available. Refresh the list and try again."))
            return
        }
        try {
            requestCallEndpointChange(endpoint, mainExecutor, object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) {
                    CallBridge.send(JSONObject().put("event", "CALL_AUDIO_ROUTE_RESULT")
                        .put("ok", true).put("message", "Android accepted the audio route request for ${endpoint.endpointName}."))
                }

                override fun onError(error: CallEndpointException) {
                    CallBridge.send(JSONObject().put("event", "CALL_AUDIO_ROUTE_RESULT")
                        .put("ok", false).put("message", error.message ?: "Android could not change the call audio route."))
                }
            })
        } catch (error: Exception) {
            CallBridge.send(JSONObject().put("event", "CALL_AUDIO_ROUTE_RESULT")
                .put("ok", false).put("message", error.message ?: "Android could not change the call audio route."))
        }
    }

    private fun setSpeakerRoute(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 34) {
            val type = if (enabled) CallEndpoint.TYPE_SPEAKER else CallEndpoint.TYPE_EARPIECE
            val endpoint = callEndpoints.values.firstOrNull { it.endpointType == type }
            if (endpoint != null) {
                requestCallEndpoint(endpoint.identifier.toString())
                return
            }
        }
        @Suppress("DEPRECATION")
        setAudioRoute(if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
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