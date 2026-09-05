package com.avinash.yatramitra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avinash.yatramitra.ui.theme.OnSurface
import com.avinash.yatramitra.ui.theme.OnSurfaceVariant
import kotlin.math.min

private val MarkGradientColors = listOf(Color(0xFFFF6B35), Color(0xFFF97316), Color(0xFFE11D48))

/** The circular gradient badge + map-pin mark, recreated from the exported logo SVG. */
@Composable
fun YatraMitraMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier = modifier.size(size)) {
        val s = min(this.size.width, this.size.height) / 44f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            brush = Brush.linearGradient(colors = MarkGradientColors),
            radius = 21f * s,
            center = center
        )
        // Pin shape (SVG-local 44x44 box, origin at the icon group's own top-left).
        val pin = Path().apply {
            moveTo(22f * s, 10f * s)
            cubicTo(16.5f * s, 10f * s, 12f * s, 14.5f * s, 12f * s, 20f * s)
            cubicTo(12f * s, 26f * s, 22f * s, 34f * s, 22f * s, 34f * s)
            cubicTo(22f * s, 34f * s, 32f * s, 26f * s, 32f * s, 20f * s)
            cubicTo(32f * s, 14.5f * s, 27.5f * s, 10f * s, 22f * s, 10f * s)
            close()
        }
        drawPath(pin, color = Color.White)
        drawCircle(color = Color(0xFFFF6B35), radius = 4f * s, center = Offset(22f * s, 19f * s))
        val beacon = Path().apply {
            moveTo(22f * s, 6f * s)
            lineTo(24f * s, 10f * s)
            lineTo(22f * s, 9f * s)
            lineTo(20f * s, 10f * s)
            close()
        }
        drawPath(beacon, color = Color(0xFFFFE4D6))
    }
}

/** The full logo lockup (mark + "Yatra"/"Mitra" wordmark + optional tagline). */
@Composable
fun YatraMitraLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = 36.dp,
    showTagline: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        YatraMitraMark(size = markSize)
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Row {
                Text(
                    "Yatra",
                    color = OnSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Mitra",
                    color = Color(0xFFF97316),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            if (showTagline) {
                Text(
                    "SMART GROUP TRIP PLANNER",
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
