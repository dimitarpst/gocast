package app.gocast.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop viewer pane — WebView2 (system Edge via Wry) only. */
@Composable
fun ViewerPane(
    url: String,
    modifier: Modifier = Modifier,
) {
    WebView2ViewerPane(
        url = url,
        modifier = modifier,
    )
}
