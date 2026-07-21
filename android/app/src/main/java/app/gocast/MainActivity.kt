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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.gocast.data.OnboardingStore
import app.gocast.ui.ConnectScreen
import app.gocast.ui.OnboardingScreen
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    // Deep links / resume skip the walkthrough so join still works first launch.
                    var showOnboarding by remember {
                        mutableStateOf(
                            deepLinkUri == null &&
                                resumeCastRequestId == 0 &&
                                !OnboardingStore.isDone(context),
                        )
                    }
                    if (showOnboarding) {
                        OnboardingScreen(
                            onFinished = {
                                OnboardingStore.markDone(context)
                                showOnboarding = false
                            },
                        )
                    } else {
                        ConnectScreen(
                            deepLinkUri = deepLinkUri,
                            resumeCastRequestId = resumeCastRequestId,
                        )
                    }
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
