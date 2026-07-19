package app.gocast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    logoHeight: Dp = 48.dp,
) {
    val density = LocalDensity.current
    val logo = remember(density) {
        useResource("gocast_logo.svg") { loadSvgPainter(it, density) }
    }

    Image(
        painter = logo,
        contentDescription = "gocast",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(logoHeight),
    )
}
