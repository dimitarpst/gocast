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
import app.gocast.domain.ConnectionState
import app.gocast.network.SessionConnector
import app.gocast.ui.theme.GocastTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope

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
    val scope = rememberCoroutineScope()
    val connector = remember { SessionConnector(scope) }
    val connectionState by connector.state.collectAsState()

    LaunchedEffect(deepLinkUri) {
        val params = JoinUrlParser.parse(deepLinkUri)
        if(params == null){
            connector.stop()
            return@LaunchedEffect
        }
        connector.start(params)
    }

    DisposableEffect(Unit) {
        onDispose { connector.stop() }
    }

    val label = when (val state = connectionState){
        is ConnectionState.Idle -> "Scan QR to connect"
        is ConnectionState.Connecting -> "Connecting..."
        is ConnectionState.Connected -> "Connected!"
        is ConnectionState.Reconnecting -> "Reconnecting..."
        is ConnectionState.Failed -> state.reason
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}