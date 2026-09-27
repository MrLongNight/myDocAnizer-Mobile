package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getColorSchemeForSkin(skin: String, isDark: Boolean, highContrast: Boolean = false): ColorScheme {
    if (highContrast) {
        return if (isDark) {
            darkColorScheme(
                primary = Color(0xFF38BDF8),
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF0284C7),
                onPrimaryContainer = Color.White,
                secondary = Color(0xFF34D399),
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF059669),
                onSecondaryContainer = Color.White,
                background = Color(0xFF000000),
                onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF0D1117),
                onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF1E293B),
                onSurfaceVariant = Color(0xFFF8FAFC),
                outline = Color(0xFFE2E8F0),
                outlineVariant = Color(0xFF94A3B8),
                error = Color(0xFFF87171),
                onError = Color.Black,
                errorContainer = Color(0xFF991B1B),
                onErrorContainer = Color.White
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF1D4ED8),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDBEAFE),
                onPrimaryContainer = Color(0xFF000000),
                secondary = Color(0xFF047857),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFD1FAE5),
                onSecondaryContainer = Color(0xFF000000),
                background = Color(0xFFFFFFFF),
                onBackground = Color(0xFF000000),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF000000),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF000000),
                outline = Color(0xFF000000),
                outlineVariant = Color(0xFF475569),
                error = Color(0xFFB91C1C),
                onError = Color.White,
                errorContainer = Color(0xFFFEE2E2),
                onErrorContainer = Color(0xFF7F1D1D)
            )
        }
    }

    return when (skin) {
        "EMERALD" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF10B981),
                onPrimary = Color(0xFF022C22),
                primaryContainer = Color(0xFF064E3B),
                onPrimaryContainer = Color(0xFFA7F3D0),
                secondary = Color(0xFF2DD4BF),
                onSecondary = Color(0xFF042F2E),
                secondaryContainer = Color(0xFF115E59),
                onSecondaryContainer = Color(0xFF99F6E4),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF059669),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD1FAE5),
                onPrimaryContainer = Color(0xFF064E3B),
                secondary = Color(0xFF0D9488),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCCFBF1),
                onSecondaryContainer = Color(0xFF115E59),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "SLATE" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFF94A3B8),
                onPrimary = Color(0xFF0F172A),
                primaryContainer = Color(0xFF334155),
                onPrimaryContainer = Color(0xFFF1F5F9),
                secondary = Color(0xFF64748B),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF1E293B),
                onSecondaryContainer = Color(0xFFCBD5E1),
                background = Color(0xFF0B0F17),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF182234),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF2D3748),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF475569),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE2E8F0),
                onPrimaryContainer = Color(0xFF1E293B),
                secondary = Color(0xFF334155),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF1F5F9),
                onSecondaryContainer = Color(0xFF0F172A),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "AMBER" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFF59E0B),
                onPrimary = Color(0xFF451A03),
                primaryContainer = Color(0xFF78350F),
                onPrimaryContainer = Color(0xFFFDE68A),
                secondary = Color(0xFFFB923C),
                onSecondary = Color(0xFF431407),
                secondaryContainer = Color(0xFF9A3412),
                onSecondaryContainer = Color(0xFFFFEDD5),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFD97706),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFEF3C7),
                onPrimaryContainer = Color(0xFF78350F),
                secondary = Color(0xFFB45309),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFFEDD5),
                onSecondaryContainer = Color(0xFF7C2D12),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "PURPLE" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFA78BFA),
                onPrimary = Color(0xFF2E1065),
                primaryContainer = Color(0xFF5B21B6),
                onPrimaryContainer = Color(0xFFEDE9FE),
                secondary = Color(0xFFC084FC),
                onSecondary = Color(0xFF3B0764),
                secondaryContainer = Color(0xFF6B21A8),
                onSecondaryContainer = Color(0xFFF3E8FF),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF7C3AED),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEDE9FE),
                onPrimaryContainer = Color(0xFF4C1D95),
                secondary = Color(0xFF6D28D9),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF3E8FF),
                onSecondaryContainer = Color(0xFF581C87),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        "ROSE" -> if (isDark) {
            darkColorScheme(
                primary = Color(0xFFFB7185),
                onPrimary = Color(0xFF4C0519),
                primaryContainer = Color(0xFF881337),
                onPrimaryContainer = Color(0xFFFFE4E6),
                secondary = Color(0xFFF43F5E),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF9F1239),
                onSecondaryContainer = Color(0xFFFFF1F2),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFBE123C),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFE4E6),
                onPrimaryContainer = Color(0xFF881337),
                secondary = Color(0xFF9F1239),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFFF1F2),
                onSecondaryContainer = Color(0xFF4C0519),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color.White,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF64748B)
            )
        }
        else -> { // "BLUE" (Default)
            if (isDark) {
                darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFF1E3A8A),
                    onPrimaryContainer = Color(0xFFDBEAFE),
                    secondary = Color(0xFF0284C7),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFF075985),
                    onSecondaryContainer = Color(0xFFE0F2FE),
                    background = Color(0xFF0F172A),
                    onBackground = Color(0xFFF1F5F9),
                    surface = Color(0xFF1E293B),
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = Color(0xFF334155),
                    onSurfaceVariant = Color(0xFF94A3B8)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2563EB),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFDBEAFE),
                    onPrimaryContainer = Color(0xFF1E3A8A),
                    secondary = Color(0xFF0284C7),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFE0F2FE),
                    onSecondaryContainer = Color(0xFF0C4A6E),
                    background = Color(0xFFF8FAFC),
                    onBackground = Color(0xFF0F172A),
                    surface = Color.White,
                    onSurface = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSkin: String = "BLUE",
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorSchemeForSkin(colorSkin, darkTheme, highContrast)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

