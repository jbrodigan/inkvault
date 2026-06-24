package com.inkvault.pen

/** Notification constants + pure status-text mapping for the pen foreground service. */
object PenNotifications {
    const val CHANNEL_ID = "pen_connection"
    const val CHANNEL_NAME = "Pen connection"
    const val NOTIF_ID = 1001

    /** Separate HIGH-importance channel for failures only — capture itself stays silent. */
    const val ALERT_CHANNEL_ID = "pen_alert"
    const val ALERT_CHANNEL_NAME = "Pen alerts"
    const val ALERT_NOTIF_ID = 1002
    const val BATTERY_NOTIF_ID = 1003

    /** Low battery (not charging) — actionable, on the alert channel. Pure. */
    fun batteryWarningText(percent: Int): String = "Pen at $percent% — charge it before you need it"

    /** Pure function (no Android deps) → unit-testable. Drives the persistent notification text. */
    fun statusText(state: PenConnState): String = when (state) {
        is PenConnState.Connected -> "Pen connected — capturing · ${state.penName}"
        is PenConnState.Connecting -> "Connecting to pen…"
        is PenConnState.Reconnecting -> "Reconnecting (attempt ${state.attempt})…"
        is PenConnState.BondedElsewhere -> "Pen is paired to another device — tap to take over"
        is PenConnState.PasswordRequired -> "Pen locked — enter password in the app"
        is PenConnState.Disconnected -> "No pen connected"
    }

    /**
     * Capture line for the silent ongoing notification: notebook + page + live stroke count while
     * connected and inking, else the plain connection status. Pure → unit-testable.
     */
    fun captureText(state: PenConnState, notebookTitle: String?, page: Int?, strokeCount: Int): String {
        if (state !is PenConnState.Connected || page == null) return statusText(state)
        val book = notebookTitle?.takeIf { it.isNotBlank() } ?: "Notebook"
        val strokes = if (strokeCount == 1) "1 stroke" else "$strokeCount strokes"
        return "$book · Page $page · $strokes"
    }

    /** Loud-failure line: names the pages already safe + the recovery state. Pure. */
    fun alertText(pagesSafe: Int, reconnecting: Boolean): String {
        val safe = if (pagesSafe == 1) "1 page safe" else "$pagesSafe pages safe"
        val tail = if (reconnecting) "reconnecting…" else "tap to reconnect"
        return "Pen disconnected — $safe · $tail"
    }

    /** Whether the service should keep running for this state (only an explicit stop ends it). */
    fun isActive(state: PenConnState): Boolean = state !is PenConnState.Disconnected
}
