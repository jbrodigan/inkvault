package com.inkvault.zones

import kotlinx.serialization.Serializable

/** What a tapped printed icon triggers. Share/Email of the current page, as PNG or PDF. */
@Serializable
enum class ZoneAction(val label: String) {
    SHARE_PNG("Share · PNG"),
    SHARE_PDF("Share · PDF"),
    EMAIL_PNG("Email · PNG"),
    EMAIL_PDF("Email · PDF"),
}

/**
 * A rectangular tap target bound to an app action, in RAW Ncode coordinates. The box is learned by
 * tracing the printed icon (so its extent is exact, not guessed). A printed icon sits at the same
 * place on every page of a notebook, so matching ignores the page id and works notebook-wide.
 */
@Serializable
data class ActionZone(
    val id: String,
    val action: ZoneAction,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * If [pts] is a "tap" — every point within [eps] of the others (a near-stationary press, not a
 * stroke of writing) — return its centre; otherwise null. Pure so it can be unit-tested.
 */
fun tapCentre(pts: List<Pair<Float, Float>>, eps: Float): Pair<Float, Float>? {
    if (pts.isEmpty()) return null
    val minX = pts.minOf { it.first }; val maxX = pts.maxOf { it.first }
    val minY = pts.minOf { it.second }; val maxY = pts.maxOf { it.second }
    if (maxX - minX > eps || maxY - minY > eps) return null
    return (minX + maxX) / 2f to (minY + maxY) / 2f
}

/** Bounding box of [pts] as [left, top, right, bottom], or null if empty (the traced calibration). */
fun boundsOf(pts: List<Pair<Float, Float>>): List<Float>? {
    if (pts.isEmpty()) return null
    return listOf(pts.minOf { it.first }, pts.minOf { it.second }, pts.maxOf { it.first }, pts.maxOf { it.second })
}
