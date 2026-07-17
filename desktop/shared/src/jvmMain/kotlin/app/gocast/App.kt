package app.gocast

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gocast.domain.model.JoinSession
import app.gocast.ui.QrCard
import app.gocast.ui.ViewerPane

@Composable
fun App(session: JoinSession) {
    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            QrCard(
                session = session,
                modifier = Modifier.width(320.dp).fillMaxHeight(),
            )
            VerticalDivider()
            ViewerPane(
                url = "http://127.0.0.1:${session.port}/view",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
