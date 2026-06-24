package com.inkvault

import com.google.common.truth.Truth.assertThat
import com.inkvault.ink.strokeOutline
import kotlin.math.abs
import org.junit.Test

class FreehandTest {

    @Test
    fun `constant-pressure straight line is about the requested width`() {
        val xs = floatArrayOf(0f, 10f, 20f, 30f)
        val ys = floatArrayOf(0f, 0f, 0f, 0f)
        val pr = floatArrayOf(1f, 1f, 1f, 1f)
        val out = strokeOutline(xs, ys, pr, width = 4f, thinning = 0.5f, streamline = 0f)

        // A horizontal line → vertical offsets of ±half (2). The outline's max |y| ≈ 2.
        var maxY = 0f
        var i = 1
        while (i < out.size) { maxY = maxOf(maxY, abs(out[i])); i += 2 }
        assertThat(maxY).isWithin(0.5f).of(2f)
    }

    @Test
    fun `a single point yields a non-empty dot polygon`() {
        val out = strokeOutline(floatArrayOf(5f), floatArrayOf(5f), floatArrayOf(1f), width = 3f)
        assertThat(out.size).isGreaterThan(0)
    }

    @Test
    fun `empty input is empty`() {
        assertThat(strokeOutline(FloatArray(0), FloatArray(0), FloatArray(0), width = 3f)).isEmpty()
    }
}
