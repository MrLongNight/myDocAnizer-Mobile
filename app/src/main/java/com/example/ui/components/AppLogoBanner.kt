package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * 1:1 Original myDocAnizer Mobile PNG Logo Banner:
 * Zeigt ausschließlich die originale PNG-Grafikdatei (mydocanizer_mobile.png) an.
 */
@Composable
fun AppLogoBanner(
    modifier: Modifier = Modifier,
    iconHeight: Dp = 110.dp,
    fontSize: Float = 22f,
    includeContainer: Boolean = false,
    showTagline: Boolean = false,
    showMobileBadge: Boolean = true,
    tagline: String = "first private and smart document management system"
) {
    Box(
        modifier = modifier.testTag("app_logo_banner_container"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.mydocanizer_mobile),
            contentDescription = "myDocAnizer Mobile",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(iconHeight)
                .fillMaxWidth()
                .testTag("app_logo_image")
        )
    }
}
