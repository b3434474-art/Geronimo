package com.geronimo.companion

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private var bridge: BluetoothBridge? = null
    private lateinit var numberInput: EditText

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
        content.addView(Button(this).apply {
            text = "Set as default phone app"
            setOnClickListener { requestDialerRole() }
        })
        content.addView(TextView(this).apply {
            text = "Call a number"
            textSize = 18f
            setPadding(0, 24, 0, 4)
        })
        numberInput = EditText(this).apply {
            hint = "Phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        content.addView(numberInput)
        content.addView(Button(this).apply {
            text = "Open phone dialer"
            setOnClickListener {
                val number = numberInput.text.toString().trim()
                CallLauncher.openDialer(this@MainActivity, number)
            }
        })
        content.addView(TextView(this).apply {
            text = "To show incoming calls in Geronimo, choose it as the default phone app. Android will ask you to confirm. Incoming calls are NEVER answered automatically: open the call screen and tap Answer."
            textSize = 14f
            setPadding(0, 16, 0, 0)
        })
        content.addView(TextView(this).apply {
            text = "Keep this screen open while testing the BLE connection from Windows. Bluetooth messaging works, but remote PC call controls and two-way call audio are not implemented yet."
            textSize = 14f
            setPadding(0, 20, 0, 0)
        })
        setContentView(content)

        requestNotificationPermissionIfNeeded()
        if (hasBluetoothPermissions()) startBridge()
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT), 41)
        } else startBridge()
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 42)
            } else if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                statusView.text = "Geronimo is the default phone app."
            }
        } else {
            val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
            statusView.text = "Open Android settings to choose Geronimo as your phone app."
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 43)
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42) {
            statusView.text = if (resultCode == RESULT_OK) "Geronimo is now the default phone app." else "Default phone app role was not granted."
        }
    }

    override fun onDestroy() {
        bridge?.stop()
        super.onDestroy()
    }
}
