package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * 1:1 Original myDocAnizer Desktop PNG Logo Banner:
 * Verwendet direkt die originale mydocanizer_desktop.png Bilddatei ohne Nachbauten.
 */
@Composable
fun MyDocAnizerDesktopLogo(
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    showSubtitle: Boolean = true
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .testTag("desktop_logo_banner"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.mydocanizer_desktop),
                contentDescription = "myDocAnizer Desktop",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(height)
                    .wrapContentWidth()
                    .testTag("desktop_logo_image")
            )
        }
    }
}

/**
 * Kompatibilitäts-Alias für bestehende Aufrufe
 */
@Composable
fun MyDocAnizerLightLogo(
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    showSubtitle: Boolean = true
) {
    MyDocAnizerDesktopLogo(modifier = modifier, height = height, showSubtitle = showSubtitle)
}


