package com.geronimo.companion

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens Android's dialer with a number; the user reviews and places the call. */
object CallLauncher {
    fun openDialer(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
