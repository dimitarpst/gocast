package app.gocast.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewState

@Composable
fun ViewerPane(
    url: String,
    reloadToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    key(reloadToken) {
        val state = rememberWebViewState(url)
        WebView(
            state = state,
            modifier = modifier.fillMaxSize(),
        )
    }
}
