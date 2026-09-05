package com.avinash.yatramitra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Fixed palette so a given name always maps to the same color across the app/session. */
private val AvatarPalette = listOf(
    Color(0xFF9D4300), // primary
    Color(0xFF006C49), // tertiary
    Color(0xFF565E74), // secondary
    Color(0xFFE11D48),
    Color(0xFF0F172A),
    Color(0xFF00838F)
)

private fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun colorFor(seed: String): Color {
    if (seed.isEmpty()) return AvatarPalette[0]
    val index = abs(seed.hashCode()) % AvatarPalette.size
    return AvatarPalette[index]
}

/** A circular colored avatar showing a member's initials, deterministic per name — used in place
 *  of a real profile photo (no photo upload/storage in this app). */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(colorFor(name), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsFor(name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.4f).sp,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
