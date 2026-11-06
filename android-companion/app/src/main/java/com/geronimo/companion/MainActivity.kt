package com.geronimo.companion

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 32, 32, 32)
        }

        content.addView(
            TextView(this).apply {
                text = "Geronimo"
                textSize = 28f
                setPadding(0, 0, 0, 12)
            }
        )

        content.addView(
            TextView(this).apply {
                text = "Windows connection: Not connected"
                textSize = 18f
                setPadding(0, 0, 0, 16)
            }
        )

        content.addView(
            TextView(this).apply {
                text = "The Bluetooth connection and phone-call integration are not implemented yet."
                textSize = 16f
            }
        )

        content.addView(
            TextView(this).apply {
                text = "Incoming calls will always ask for your approval. This app does not auto-answer calls."
                textSize = 16f
                setPadding(0, 24, 0, 0)
            }
        )

        setContentView(content)
    }
}
