package com.inkvault.pen

/**
 * Maps a connected pen (by its reported name/model) to its manufacturer's official page — for a
 * user-initiated "Official page" link. Only well-known, stable manufacturer URLs (no fabricated or
 * guessed deep links). LAMY ncode pens are NeoLAB hardware sold under the LAMY brand, so they point
 * to LAMY; Neo pens point to NeoLAB; anything else falls back to the NeoLAB SDK org.
 */
object PenLinks {
    const val NEOLAB = "https://www.neosmartpen.com"
    const val LAMY = "https://www.lamy.com"
    const val SDK = "https://github.com/NeoSmartpen"

    /** Official page for [penName] (e.g. "LAMY_safari", "NWP-F80", "Neosmartpen_M1+", "NWP-F55"). */
    fun officialUrl(penName: String?): String {
        val n = penName.orEmpty().uppercase()
        return when {
            "LAMY" in n || "NWP-F80" in n -> LAMY
            "NEO" in n || "M1" in n || "NWP-F5" in n || "NWP-F1" in n || "DIMO" in n -> NEOLAB
            else -> SDK
        }
    }
}
