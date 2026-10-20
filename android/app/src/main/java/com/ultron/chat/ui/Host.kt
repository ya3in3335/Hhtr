package com.ultron.chat.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Device-side actions the Activity provides to the Compose UI. */
interface Host {
    fun micGranted(): Boolean
    fun locGranted(): Boolean
    /** Requests the OS permissions the user toggled on; reports what was actually granted. */
    fun requestPermissions(wantMic: Boolean, wantLoc: Boolean, done: (micGranted: Boolean, locGranted: Boolean) -> Unit)

    fun startListening(lang: String, onText: (String) -> Unit, onEnd: () -> Unit)
    fun stopListening()
    fun speak(text: String, lang: String)

    /** Reads the last known location (null if unavailable / not permitted). */
    fun lastLocation(cb: (lat: Double?, lon: Double?) -> Unit)

    /** Persists the language and recreates the activity so strings + direction update. */
    fun setLocale(lang: String)
    fun currentLang(): String
}

val LocalHost = staticCompositionLocalOf<Host> { error("Host not provided") }
