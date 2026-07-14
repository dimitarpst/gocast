package app.gocast

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import app.gocast.domain.model.JoinSession
import app.gocast.ui.QrCard

@Composable
fun App(session: JoinSession) {
  MaterialTheme{
      QrCard(session)
  }
}