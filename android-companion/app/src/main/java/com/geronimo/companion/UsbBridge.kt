package com.geronimo.companion

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB data bridge. Windows reaches this loopback-only socket through an
 * explicitly authorized Android Debug Bridge (ADB) port forward.
 * It carries call controls/events, never cellular call audio.
 */
object UsbBridge {
    private const val PORT = 43821
    @Volatile private var server: LocalUsbBridgeServer? = null

    fun start(context: Context, onStatus: (String) -> Unit) {
        stop()
        val instance = LocalUsbBridgeServer(onStatus)
        server = instance
        instance.start()
    }

    fun publish(message: JSONObject) {
        server?.publish(message)
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private class LocalUsbBridgeServer(
        private val onStatus: (String) -> Unit
    ) {
        private val running = AtomicBoolean(false)
        private val writeLock = Any()
        @Volatile private var serverSocket: ServerSocket? = null
        @Volatile private var clientSocket: Socket? = null
        @Volatile private var clientWriter: BufferedWriter? = null
        private var worker: Thread? = null

        fun start() {
            if (!running.compareAndSet(false, true)) return
            worker = Thread(::serve, "GeronimoUSB").apply {
                isDaemon = true
                start()
            }
        }

        private fun serve() {
            try {
                ServerSocket().use { listener ->
                    listener.reuseAddress = true
                    listener.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
                    serverSocket = listener
                    onStatus("USB bridge ready — connect the cable and authorize this PC")
                    while (running.get()) {
                        val socket = listener.accept()
                        clientSocket = socket
                        socket.tcpNoDelay = true
                        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                        clientWriter = writer
                        onStatus("USB connection established")
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                            publish(JSONObject().put("event", "PHONE_READY").put("transport", "USB"))
                            GeronimoInCallService.refreshCallStateFromUi()
                            while (running.get()) {
                                val line = reader.readLine() ?: break
                                if (line.length > 4096) continue
                                try {
                                    val command = JSONObject(line)
                                    if (command.optString("command") == "PING") {
                                        publish(JSONObject().put("event", "PHONE_READY").put("transport", "USB"))
                                    } else {
                                        CallBridge.handleCommand(command)
                                    }
                                } catch (_: Exception) {
                                    onStatus("Ignored an invalid USB phone-link message")
                                }
                            }
                        } catch (error: Exception) {
                            if (running.get()) onStatus("USB client disconnected: ${error.message ?: "connection closed"}")
                        } finally {
                            synchronized(writeLock) {
                                clientWriter = null
                            }
                            clientSocket = null
                            try { socket.close() } catch (_: Exception) { }
                        }
                    }
                }
            } catch (error: Exception) {
                if (running.get()) onStatus("USB bridge unavailable: ${error.message ?: "could not open local socket"}")
            } finally {
                serverSocket = null
                clientWriter = null
                clientSocket = null
                running.set(false)
            }
        }

        fun publish(message: JSONObject) {
            synchronized(writeLock) {
                val writer = clientWriter ?: return
                try {
                    writer.write(message.toString())
                    writer.newLine()
                    writer.flush()
                } catch (_: Exception) {
                    try { clientSocket?.close() } catch (_: Exception) { }
                }
            }
        }

        fun stop() {
            running.set(false)
            try { clientSocket?.close() } catch (_: Exception) { }
            try { serverSocket?.close() } catch (_: Exception) { }
            worker?.join(800)
            worker = null
        }
    }
}
