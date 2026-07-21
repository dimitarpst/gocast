package app.gocast.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.gocast.platform.GocastDataDirs
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.rememberWebViewState

/** Match app chrome / viewer HTML so the native surface isn't white before first paint. */
private val ViewerSurfaceColor = Color(0xFF12141A)

/**
 * Runs before page scripts — forces a dark document so navigation never flashes white.
 */
private const val DarkFirstPaintScript =
    "document.documentElement.style.background='#12141A';" +
        "document.documentElement.style.colorScheme='dark';" +
        "if(document.body){document.body.style.background='#12141A';}"

/**
 * WebView2 viewer (Wry via kdroidfilter composewebview).
 * Loads the localhost `/view` URL served by the desktop app.
 *
 * Caller keeps this mounted for the whole session (1.dp while pairing,
 * full size under the OS connecting overlay when cast starts). Never remove
 * from composition — that destroys the HWND.
 */
@Composable
fun WebView2ViewerPane(
    url: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(url = url) {
        backgroundColor = ViewerSurfaceColor
        desktopWebSettings.transparent = false
        desktopWebSettings.initScript = DarkFirstPaintScript
        // Packaged MSI installs under Program Files — WebView2 cannot create its
        // default UDF next to the exe. Point at LocalAppData instead.
        desktopWebSettings.dataDirectory = GocastDataDirs.webview2UserData().absolutePath
        desktopWebSettings.autoplayWithoutUserInteraction = true
    }

    Box(modifier = modifier) {
        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
