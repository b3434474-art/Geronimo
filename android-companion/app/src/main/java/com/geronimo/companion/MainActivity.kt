package com.geronimo.companion

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
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
    private lateinit var numberInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContext.application = applicationContext
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        content.addView(TextView(this).apply {
            text = "Geronimo"
            textSize = 30f
            setPadding(0, 0, 0, 20)
        })
        statusView = TextView(this).apply { text = "USB or Bluetooth connection can be started"; textSize = 18f }
        content.addView(statusView)
        content.addView(Button(this).apply {
            text = "Set as default phone app"
            setOnClickListener { requestDialerRole() }
        })
        content.addView(Button(this).apply { text = "Connect to Windows"; setOnClickListener { startBridge() } })
        content.addView(Button(this).apply {
            text = "Stop Windows connection"
            setOnClickListener {
                stopService(Intent(this@MainActivity, GeronimoConnectionService::class.java))
                statusView.text = "Windows connection stopped"
            }
        })
        content.addView(Button(this).apply {
            text = "Enable caller names from contacts"
            setOnClickListener { requestPermission(Manifest.permission.READ_CONTACTS, 44) }
        })
        content.addView(Button(this).apply {
            text = "Allow direct dialing for trusted contacts"
            setOnClickListener { requestPermission(Manifest.permission.CALL_PHONE, 45) }
        })
        content.addView(TextView(this).apply {
            text = "Call a number"; textSize = 18f; setPadding(0, 24, 0, 4)
        })
        numberInput = EditText(this).apply {
            hint = "Phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        content.addView(numberInput)
        content.addView(Button(this).apply {
            text = "Open phone dialer"
            setOnClickListener { CallLauncher.openDialer(this@MainActivity, numberInput.text.toString().trim()) }
        })
        content.addView(TextView(this).apply {
            text = "Incoming calls are NEVER answered automatically. Android will ask you to confirm Geronimo as your default phone app."
            textSize = 14f; setPadding(0, 16, 0, 0)
        })
        content.addView(TextView(this).apply {
            text = "USB carries call controls and caller updates without requiring Bluetooth permissions. Android can share call audio only through a compatible audio endpoint shown in the call-audio list; the app cannot directly capture cellular-call audio."
            textSize = 14f; setPadding(0, 20, 0, 0)
        })
        setContentView(content)

        requestNotificationPermissionIfNeeded()
        if (Build.VERSION.SDK_INT >= 34 && !hasConnectionServicePermission()) {
            // Android 14+ requires a qualifying runtime permission before this connected-device
            // foreground service can start. USB itself still uses ADB, not Bluetooth pairing.
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), 41)
            statusView.text = "Allow at least one Nearby devices permission to keep the phone link running. USB still uses authorized ADB, not Bluetooth pairing."
        } else {
            startBridge()
        }
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 42)
            } else if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) statusView.text = "Geronimo is the default phone app."
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

    private fun requestPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            statusView.text = "Permission already granted"
        } else ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    private fun hasConnectionServicePermission(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

    private fun startBridge() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, GeronimoConnectionService::class.java))
            statusView.text = "Phone link started. USB works over an authorized cable; Bluetooth is the optional fallback."
        } catch (error: Exception) {
            statusView.text = "Could not start the phone connection service: ${error.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            41 -> if (hasConnectionServicePermission()) {
                startBridge()
                statusView.text = "Phone link started. USB uses authorized ADB; Bluetooth fallback works when both Nearby devices permissions are allowed."
            } else {
                statusView.text = "Android did not grant a permission needed for the persistent connection service. The service has not been started."
            }
            43 -> statusView.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "Call notifications enabled." else "Notifications are disabled in Android settings."
            44 -> statusView.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "Caller names from contacts enabled." else "Caller names will only come from caller ID or the Geronimo allowlist."
            45 -> statusView.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "Trusted contacts can use direct dialing." else "Trusted calls will open the phone dialer for manual confirmation."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42) statusView.text =
            if (resultCode == RESULT_OK) "Geronimo is now the default phone app." else "Default phone app role was not granted."
    }
}