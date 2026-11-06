package com.geronimo.companion

import android.app.Activity
import android.os.Bundle
import android.telecom.Call
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Simple in-call screen. Answer is always a deliberate button press. */
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
            setPadding(32, 56, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = "Geronimo"
            textSize = 30f
        })
        caller = TextView(this).apply {
            textSize = 22f
            setPadding(0, 28, 0, 12)
        }
        layout.addView(caller)
        state = TextView(this).apply { textSize = 16f }
        layout.addView(state)

        answer = Button(this).apply {
            text = "Answer"
            setOnClickListener {
                val call = GeronimoInCallService.activeCall
                if (call?.state == Call.STATE_RINGING) {
                    call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
                    refresh()
                }
            }
        }
        decline = Button(this).apply {
            text = "Decline"
            setOnClickListener {
                val call = GeronimoInCallService.activeCall
                if (call?.state == Call.STATE_RINGING) call.reject(false, null)
                finish()
            }
        }
        hangUp = Button(this).apply {
            text = "Hang up"
            setOnClickListener {
                GeronimoInCallService.activeCall?.disconnect()
                finish()
            }
        }
        layout.addView(answer)
        layout.addView(decline)
        layout.addView(hangUp)
        setContentView(layout)
        refresh()
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
                state.text = "Incoming call — choose whether to answer"
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
