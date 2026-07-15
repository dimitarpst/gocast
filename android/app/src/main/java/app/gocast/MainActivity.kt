package app.gocast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.gocast.domain.JoinUrlParser
import app.gocast.network.SignalingClient
import app.gocast.ui.theme.GocastTheme

class MainActivity : ComponentActivity() {
    private var deepLinkUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkUri = intent?.data
        setContent {
            GocastTheme {
                ConnectScreen(deepLinkUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUri = intent.data
    }
}

@Composable
private fun ConnectScreen(deepLinkUri: Uri?) {
    var status by remember { mutableStateOf("Scan QR to connect") }

    LaunchedEffect(deepLinkUri) {
        val params = JoinUrlParser.parse(deepLinkUri) ?: return@LaunchedEffect
        status = "Connecting..."
        val ok = SignalingClient().sendHello(params.host, params.port, params.token)
        status = if (ok) "Connected!" else "Failed"
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(status, style = MaterialTheme.typography.headlineMedium)
    }
}