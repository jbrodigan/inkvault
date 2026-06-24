package com.inkvault.pen

import android.content.Context
import android.content.SharedPreferences

/** Remembers the last-paired pen so the app can auto-reconnect (complaint #4). */
interface PenPrefs {
    var lastPenMac: String?
}

class SharedPrefsPenPrefs(context: Context) : PenPrefs {
    private val sp: SharedPreferences =
        context.getSharedPreferences("pen_prefs", Context.MODE_PRIVATE)

    override var lastPenMac: String?
        get() = sp.getString(KEY_MAC, null)
        set(value) = sp.edit().putString(KEY_MAC, value).apply()

    private companion object { const val KEY_MAC = "last_pen_mac" }
}

/** In-memory prefs for tests. */
class InMemoryPenPrefs(initial: String? = null) : PenPrefs {
    override var lastPenMac: String? = initial
}
