package com.inkvault.ink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pressure-tapered freehand stroke outline — a compact port of the idea behind perfect-freehand
 * (Steve Ruiz, MIT): low-pass the centre line, vary half-width by pressure, and emit a closed fill
 * polygon with rounded ends. Pure (no Compose/Android), so it's unit-testable and shared by the
 * on-screen renderer and the raster (print / share) renderer.
 *
 * Coordinates are whatever space the caller passes (we use canvas / bitmap px). [pressures] is per
 * input point and is normalised *within the stroke* — the pen's absolute pressure range varies by
 * model, so relative pressure gives a reliable taper regardless of scale.
 *
 * Note (known limitation): no mitre/corner joins or variable end-taper like the full library;
 * a ribbon + 180° caps reads well for the smartpen's dense sampling. Upgrade path: real
 * perfect-freehand `getStroke`, or the Jetpack Ink renderer once its Compose support lands.
 *
 * Returns a flat `[x0,y0,x1,y1,…]` polygon to fill; empty if there's nothing to draw.
 */
fun strokeOutline(
    xs: FloatArray,
    ys: FloatArray,
    pressures: FloatArray,
    width: Float,
    thinning: Float = 0.5f,
    streamline: Float = 0.5f,
    capSteps: Int = 8,
): FloatArray {
    val n = xs.size
    val half = width / 2f
    if (n == 0 || half <= 0f) return FloatArray(0)
    if (n == 1) return circlePolygon(xs[0], ys[0], half, capSteps * 2)

    // 1) Low-pass the centre line (streamline) to kill jitter.
    val cx = FloatArray(n); val cy = FloatArray(n)
    cx[0] = xs[0]; cy[0] = ys[0]
    val k = (1f - streamline).coerceIn(0.05f, 1f)
    for (i in 1 until n) {
        cx[i] = cx[i - 1] + (xs[i] - cx[i - 1]) * k
        cy[i] = cy[i - 1] + (ys[i] - cy[i - 1]) * k
    }

    // 2) Per-point radius from pressure, normalised within the stroke.
    var pMin = Float.MAX_VALUE; var pMax = -Float.MAX_VALUE
    for (p in pressures) { if (p < pMin) pMin = p; if (p > pMax) pMax = p }
    val span = pMax - pMin
    val radii = FloatArray(n) { i ->
        val pn = if (span > 1e-3f) ((pressures.getOrElse(i) { pMax } - pMin) / span) else 1f
        half * ((1f - thinning) + thinning * pn)
    }

    // 3) Left/right offsets along the perpendicular; remember the unit tangent for the caps.
    val lx = FloatArray(n); val ly = FloatArray(n); val rx = FloatArray(n); val ry = FloatArray(n)
    val tx = FloatArray(n); val ty = FloatArray(n)
    for (i in 0 until n) {
        val dx = when { i == 0 -> cx[1] - cx[0]; i == n - 1 -> cx[n - 1] - cx[n - 2]; else -> cx[i + 1] - cx[i - 1] }
        val dy = when { i == 0 -> cy[1] - cy[0]; i == n - 1 -> cy[n - 1] - cy[n - 2]; else -> cy[i + 1] - cy[i - 1] }
        val len = hypot(dx, dy).coerceAtLeast(1e-4f)
        val ux = dx / len; val uy = dy / len
        tx[i] = ux; ty[i] = uy
        val nx = -uy; val ny = ux // left-hand normal
        lx[i] = cx[i] + nx * radii[i]; ly[i] = cy[i] + ny * radii[i]
        rx[i] = cx[i] - nx * radii[i]; ry[i] = cy[i] - ny * radii[i]
    }

    // 4) Assemble: left edge → round end cap → right edge (reversed) → round start cap.
    val out = ArrayList<Float>((n * 2 + capSteps * 2 + 4) * 2)
    for (i in 0 until n) { out.add(lx[i]); out.add(ly[i]) }
    appendCap(out, cx[n - 1], cy[n - 1], -ty[n - 1], tx[n - 1], tx[n - 1], ty[n - 1], radii[n - 1], capSteps, 1f)
    for (i in n - 1 downTo 0) { out.add(rx[i]); out.add(ry[i]) }
    appendCap(out, cx[0], cy[0], -ty[0], tx[0], tx[0], ty[0], radii[0], capSteps, -1f)
    return out.toFloatArray()
}

/** Half-circle of points (excluding the two edge endpoints) bulging along the tangent. */
private fun appendCap(
    out: ArrayList<Float>, cx: Float, cy: Float,
    nx: Float, ny: Float, tx: Float, ty: Float, r: Float, steps: Int, sign: Float,
) {
    for (j in 1 until steps) {
        val theta = PI.toFloat() * j / steps
        val c = cos(theta); val s = sin(theta)
        out.add(cx + sign * (c * nx + s * tx) * r)
        out.add(cy + sign * (c * ny + s * ty) * r)
    }
}

private fun circlePolygon(cx: Float, cy: Float, r: Float, steps: Int): FloatArray {
    val out = FloatArray(steps * 2)
    for (i in 0 until steps) {
        val a = 2f * PI.toFloat() * i / steps
        out[2 * i] = cx + cos(a) * r
        out[2 * i + 1] = cy + sin(a) * r
    }
    return out
}
