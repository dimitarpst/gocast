package app.gocast.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.rememberWebViewState

/** Match app chrome / viewer HTML so the native surface isn't white before first paint. */
private val ViewerSurfaceColor = Color(0xFF12141A)

/**
 * WebView2 viewer (Wry via kdroidfilter composewebview).
 * Loads the localhost `/view` URL served by the desktop app.
 */
@Composable
fun WebView2ViewerPane(
    url: String,
    reloadToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    key(reloadToken) {
        val state = rememberWebViewState(url = url) {
            backgroundColor = ViewerSurfaceColor
            desktopWebSettings.transparent = false
        }
        WebView(
            state = state,
            modifier = modifier.fillMaxSize(),
        )
    }
}
