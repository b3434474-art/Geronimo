package com.geronimo.companion

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private var bridge: BluetoothBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        content.addView(TextView(this).apply {
            text = "Geronimo"
            textSize = 30f
            setPadding(0, 0, 0, 20)
        })
        statusView = TextView(this).apply {
            text = "Bluetooth setup required"
            textSize = 18f
        }
        content.addView(statusView)
        content.addView(TextView(this).apply {
            text = "Keep this screen open while testing the BLE connection from Windows. This test sends a harmless PING message only; call features are not connected."
            textSize = 15f
            setPadding(0, 20, 0, 0)
        })
        content.addView(TextView(this).apply {
            text = "Incoming calls will always ask for your approval. Geronimo does not auto-answer."
            textSize = 15f
            setPadding(0, 24, 0, 0)
        })
        setContentView(content)

        if (hasBluetoothPermissions()) startBridge()
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT), 41)
        } else startBridge()
    }

    private fun hasBluetoothPermissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
             ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)

    private fun startBridge() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
            statusView.text = "Turn on Bluetooth, then reopen Geronimo."
            return
        }
        bridge = BluetoothBridge(this) { message -> runOnUiThread { statusView.text = message } }
        bridge?.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startBridge()
        else if (requestCode == 41) statusView.text = "Bluetooth permissions are needed for the local connection."
    }

    override fun onDestroy() {
        bridge?.stop()
        super.onDestroy()
    }
}
