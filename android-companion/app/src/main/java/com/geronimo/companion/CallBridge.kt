package com.geronimo.companion

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import org.json.JSONObject

/** Maps trusted local BLE commands onto Android Telecom actions. */
object CallBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun send(message: JSONObject) { BluetoothBridge.publish(message) }

    fun handleCommand(command: JSONObject) {
        mainHandler.post {
            when (command.optString("command")) {
                "ANSWER" -> {
                    val call = GeronimoInCallService.activeCall
                    if (call?.state == Call.STATE_RINGING) call.answer(VideoProfile.STATE_AUDIO_ONLY)
                }
                "REJECT" -> {
                    val call = GeronimoInCallService.activeCall
                    if (call?.state == Call.STATE_RINGING) call.reject(false, null)
                }
                "DISCONNECT" -> GeronimoInCallService.activeCall?.disconnect()
                "DIAL" -> GeronimoInCallService.placeCall(command.optString("number"), command.optString("name"))
                "MUTE" -> GeronimoInCallService.setMutedFromUi(command.optBoolean("enabled", false))
                "SPEAKER" -> GeronimoInCallService.setSpeakerFromUi(command.optBoolean("enabled", false))
                "ALLOWLIST_ADD" -> {
                    val number = command.optString("number")
                    val name = command.optString("name")
                    if (number.isNotBlank()) PhoneAllowlist.add(number, name)
                }
                "ALLOWLIST_REMOVE" -> PhoneAllowlist.remove(command.optString("number"))
                else -> Log.w("GeronimoCallBridge", "Unknown call command")
            }
        }
    }
}