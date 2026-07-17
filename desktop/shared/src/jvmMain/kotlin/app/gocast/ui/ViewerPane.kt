package app.gocast.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewState

@Composable
fun ViewerPane(
    url: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(url)
    WebView(
        state = state,
        modifier = modifier.fillMaxSize(),
    )
}
