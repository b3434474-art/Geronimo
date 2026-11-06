package com.geronimo.companion

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.Executors

/** Local paired-Bluetooth call control. It transports JSON commands, not live voice audio. */
object CallBridge {
    const val SERVICE_UUID = "9c7d6e50-4a3b-4f9e-9c8d-1a2b3c4d5e6f"
    private val serviceUuid = UUID.fromString(SERVICE_UUID)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writeLock = Any()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile var onStatus: ((String) -> Unit)? = null

    fun connect(device: BluetoothDevice, callback: (String) -> Unit) {
        closeSocket()
        executor.execute {
            try {
                val candidate = device.createRfcommSocketToServiceRecord(serviceUuid)
                candidate.connect()
                socket = candidate
                mainHandler.post { callback("Calling bridge connected to ${device.name ?: "Windows PC"}") }
                send(JSONObject().put("event", "PHONE_READY"))

                val reader = BufferedReader(java.io.InputStreamReader(candidate.inputStream, Charsets.UTF_8))
                while (candidate.isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    try { handleCommand(JSONObject(line)) }
                    catch (error: Exception) { Log.w("GeronimoCallBridge", "Ignoring malformed command", error) }
                }
            } catch (error: Exception) {
                Log.w("GeronimoCallBridge", "RFCOMM connection ended", error)
                mainHandler.post { callback("Calling bridge disconnected: ${error.message ?: "connection closed"}") }
            } finally {
                closeSocket()
                mainHandler.post { callback("Calling bridge disconnected") }
            }
        }
    }

    fun send(message: JSONObject) {
        try {
            synchronized(writeLock) {
                val current = socket ?: return
                val writer = OutputStreamWriter(current.outputStream, Charsets.UTF_8)
                writer.write(message.toString() + "\n")
                writer.flush()
            }
        } catch (error: Exception) {
            Log.w("GeronimoCallBridge", "Could not send event", error)
            mainHandler.post { onStatus?.invoke("Calling bridge send failed: ${error.message}") }
        }
    }

    fun handleCommand(command: JSONObject) {
        mainHandler.post {
            when (command.optString("command")) {
                "ANSWER" -> {
                    val call = GeronimoInCallService.activeCall
                    if (call?.state == android.telecom.Call.STATE_RINGING)
                        call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
                }
                "REJECT" -> {
                    val call = GeronimoInCallService.activeCall
                    if (call?.state == android.telecom.Call.STATE_RINGING) call.reject(false, null)
                }
                "DISCONNECT" -> GeronimoInCallService.activeCall?.disconnect()
                "DIAL" -> GeronimoInCallService.placeCall(
                    command.optString("number"),
                    command.optBoolean("allowlisted", false),
                    command.optString("name")
                )
                else -> Log.w("GeronimoCallBridge", "Unknown command")
            }
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    fun close() {
        closeSocket()
    }

    private fun closeSocket() {
        synchronized(writeLock) {
            try { socket?.close() } catch (_: Exception) { }
            socket = null
        }
    }
}
