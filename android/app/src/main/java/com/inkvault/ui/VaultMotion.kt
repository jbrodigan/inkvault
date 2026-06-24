package com.inkvault.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.inkvault.R
import com.inkvault.ui.theme.InkGradientStops
import com.inkvault.ui.theme.InkText
import com.inkvault.ui.theme.Navy
import com.inkvault.ui.theme.NavyDeep
import com.inkvault.ui.theme.SteelD
import com.inkvault.ui.theme.SteelL
import com.inkvault.ui.theme.SteelM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Ink" in ink color + "Vault" in the gradient, Sora — the live wordmark (matches the mockup).
 * [inkColor] defaults to the theme's onSurface so "Ink" reads dark-navy on light / near-white on
 * dark (mockup `--ink` flips by theme); pass a fixed light color over an always-dark ground (splash).
 */
@Composable
fun WordmarkText(fontSize: Int = 30, inkColor: Color = MaterialTheme.colorScheme.onSurface) {
    val display = MaterialTheme.typography.displaySmall.copy(fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold)
    Row {
        Text("Ink", style = display.copy(color = inkColor))
        Text("Vault", style = display.copy(brush = Brush.linearGradient(InkGradientStops)))
    }
}

/**
 * True when the system "Remove animations" (accessibility / developer) setting is on — the platform
 * zeroes [Settings.Global.ANIMATOR_DURATION_SCALE]. Compose infinite transitions don't honor it
 * automatically, so gate decorative loops (FAB float, gradient pan) on this. The mockup honors
 * prefers-reduced-motion the same way (line 243: animations off).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Launch splash — the vault "unlocks": the mark scales/fades in over a deep-navy ground, the
 * wordmark rises, brief hold, then [onDone]. Cinematic but short (~1.6s); honors the system's
 * reduced-motion only loosely (it's a one-shot reveal, not a loop).
 */
@Composable
fun VaultSplash(onDone: () -> Unit) {
    val scale = remember { Animatable(0.84f) }
    val markAlpha = remember { Animatable(0f) }
    val wordAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { markAlpha.animateTo(1f, tween(420)) }
        scale.animateTo(1f, tween(820, easing = FastOutSlowInEasing))
        wordAlpha.animateTo(1f, tween(360))
        delay(620)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Navy, NavyDeep),
                    radius = 1200f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.brand_logo_dark),
                contentDescription = null,
                modifier = Modifier.size(132.dp).scale(scale.value).alpha(markAlpha.value),
            )
            Spacer(Modifier.height(22.dp))
            // Splash ground is always deep-navy regardless of theme → force the light ink color.
            Box(Modifier.alpha(wordAlpha.value)) { WordmarkText(fontSize = 34, inkColor = InkText) }
        }
    }
}

/**
 * The combination dial from the logo, spinning — a brushed-steel safe dial used as a loader
 * (sync / securing pages). Steel ring + radial ticks rotate over a small gradient hub.
 */
@Composable
fun DialSpinner(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val t = rememberInfiniteTransition(label = "dial")
    val angle by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val ringW = r * 0.12f
        // static outer ring
        drawCircle(SteelD, radius = r - ringW / 2f, center = center, style = Stroke(width = ringW))
        // rotating ticks + a brighter index notch
        rotate(angle, pivot = center) {
            val ticks = 24
            for (i in 0 until ticks) {
                val a = Math.toRadians(i * 360.0 / ticks)
                val outer = r - ringW
                val inner = outer - r * (if (i % 6 == 0) 0.26f else 0.14f)
                val sx = center.x + (outer * cos(a)).toFloat()
                val sy = center.y + (outer * sin(a)).toFloat()
                val ex = center.x + (inner * cos(a)).toFloat()
                val ey = center.y + (inner * sin(a)).toFloat()
                drawLine(if (i == 0) SteelL else SteelM, Offset(sx, sy), Offset(ex, ey), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
            }
        }
        // gradient hub
        drawCircle(
            Brush.linearGradient(InkGradientStops, start = Offset(0f, 0f), end = Offset(this.size.width, this.size.height)),
            radius = r * 0.34f, center = center,
        )
        drawCircle(NavyDeep, radius = r * 0.12f, center = center)
    }
}
