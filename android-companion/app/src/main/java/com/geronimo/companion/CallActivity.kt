package com.geronimo.companion

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.telecom.Call
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Incoming-call UI inspired by the supplied sketch. Answer is always manual. */
class CallActivity : Activity() {
    private lateinit var caller: TextView
    private lateinit var state: TextView
    private lateinit var answer: Button
    private lateinit var decline: Button
    private lateinit var hangUp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 32)
            setBackgroundColor(Color.WHITE)
        }
        layout.addView(TextView(this).apply {
            text = "GERONIMO"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = "Incoming call"
            textSize = 20f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 52, 0, 8)
        })
        caller = TextView(this).apply {
            textSize = 30f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        layout.addView(caller)
        state = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }
        layout.addView(state)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        answer = callButton("☎", "Answer", Color.rgb(35, 220, 0), "Answer incoming call") {
            val call = GeronimoInCallService.activeCall
            if (call?.state == Call.STATE_RINGING) {
                call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
                refresh()
            }
        }
        decline = callButton("☎", "Decline", Color.rgb(240, 0, 20), "Decline incoming call") {
            val call = GeronimoInCallService.activeCall
            if (call?.state == Call.STATE_RINGING) call.reject(false, null)
            finish()
        }
        hangUp = callButton("☎", "Hang up", Color.rgb(240, 0, 20), "End current call") {
            GeronimoInCallService.activeCall?.disconnect()
            finish()
        }
        actions.addView(actionColumn(answer))
        actions.addView(actionColumn(decline))
        actions.addView(actionColumn(hangUp))
        layout.addView(actions)
        setContentView(layout)
        refresh()
    }

    private fun callButton(symbol: String, label: String, color: Int, description: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = symbol
            textSize = 30f
            contentDescription = description
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun actionColumn(button: Button): LinearLayout {
        val label = when (button) {
            answer -> "Answer"
            decline -> "Decline"
            else -> "Hang up"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(14, 0, 14, 0)
            addView(button, LinearLayout.LayoutParams(104, 104))
            addView(TextView(this@CallActivity).apply {
                text = label
                textSize = 15f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 0)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val call = GeronimoInCallService.activeCall
        if (call == null) {
            caller.text = "No active call"
            state.text = "Call ended or not available"
            answer.visibility = View.GONE
            decline.visibility = View.GONE
            hangUp.visibility = View.GONE
            return
        }
        caller.text = call.details.handle?.schemeSpecificPart ?: "Unknown caller"
        when (call.state) {
            Call.STATE_RINGING -> {
                state.text = "is calling"
                answer.visibility = View.VISIBLE
                decline.visibility = View.VISIBLE
                hangUp.visibility = View.GONE
            }
            Call.STATE_ACTIVE -> {
                state.text = "Call connected"
                answer.visibility = View.GONE
                decline.visibility = View.GONE
                hangUp.visibility = View.VISIBLE
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                state.text = "Calling…"
                answer.visibility = View.GONE
                decline.visibility = View.GONE
                hangUp.visibility = View.VISIBLE
            }
            else -> {
                state.text = "Call status: ${call.state}"
                answer.visibility = View.GONE
                decline.visibility = View.GONE
                hangUp.visibility = View.VISIBLE
            }
        }
    }
}
