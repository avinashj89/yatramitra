package com.avinash.yatramitra.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the app follows the device's system light/dark setting, or is pinned to one regardless
 *  of it. [SYSTEM] is the default so installs behave exactly as before this preference existed. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The user's chosen appearance, read once at startup and changeable any time from the Profile
 *  sheet. Backed by a plain SharedPreferences file (this is the only local setting in the app —
 *  everything else already lives in Firestore — so a dedicated small store isn't worth it). */
object ThemePreference {
    private const val PREFS_NAME = "yatramitra_prefs"
    private const val KEY_MODE = "theme_mode"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** Loads the saved choice, if any. Call once, before the first Compose frame. */
    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        mode = saved?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, newMode.name).apply()
    }
}
