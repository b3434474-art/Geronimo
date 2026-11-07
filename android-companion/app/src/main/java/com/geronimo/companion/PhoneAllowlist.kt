package com.geronimo.companion

import android.content.Context

/** Phone-side allowlist; Windows cannot mark an arbitrary number as trusted. */
object PhoneAllowlist {
    private const val PREFS = "geronimo_trusted_contacts"
    fun normalize(number: String): String = number.filter { it.isDigit() || it == '+' }

    fun add(number: String, name: String) {
        val context = AppContext.application ?: return
        val normalized = normalize(number)
        if (normalized.length < 3) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(normalized, name.trim().take(60)).apply()
    }

    fun remove(number: String) {
        val context = AppContext.application ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(normalize(number)).apply()
    }

    fun isAllowed(context: Context, number: String): Boolean {
        val normalized = normalize(number)
        if (normalized.length < 3) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(normalized)
    }

    fun nameFor(context: Context, number: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(normalize(number), null)
}

object AppContext {
    @Volatile var application: Context? = null
}