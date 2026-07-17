package app.gocast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.gocast.ui.ConnectScreen
import app.gocast.ui.theme.GocastTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_RESUME_CAST = "app.gocast.action.RESUME_CAST"
    }

    private var deepLinkUri by mutableStateOf<Uri?>(null)
    private var resumeCastRequestId by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkUri = intent?.data
        if (intent?.action == ACTION_RESUME_CAST) {
            resumeCastRequestId++
        }
        setContent {
            GocastTheme {
                // Without a Surface, Compose never paints the theme background —
                // the window stays white regardless of dark mode.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectScreen(
                        deepLinkUri = deepLinkUri,
                        resumeCastRequestId = resumeCastRequestId,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUri = intent.data
        if (intent.action == ACTION_RESUME_CAST) {
            resumeCastRequestId++
        }
    }
}
