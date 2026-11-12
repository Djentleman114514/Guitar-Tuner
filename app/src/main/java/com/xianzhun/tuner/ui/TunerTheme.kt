package com.xianzhun.tuner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF101A18)
val Panel = Color(0xFF192723)
val Accent = Color(0xFFC3F29B)
val Cream = Color(0xFFF1F4E9)
val Muted = Color(0xFF92A49B)
val Amber = Color(0xFFF1BE7A)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Ink,
            background = Ink, onBackground = Cream,
            surface = Panel, onSurface = Cream,
            onSurfaceVariant = Muted, outline = Color(0xFF33463D),
        ),
        typography = Typography(),
        content = content,
    )
}
