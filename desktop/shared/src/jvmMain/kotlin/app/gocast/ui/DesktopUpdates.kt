package app.gocast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gocast.update.AvailableRelease
import app.gocast.update.GitHubUpdates
import app.gocast.update.ReleaseInfo
import app.gocast.update.UpdateFailure
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    data object Checking : UpdateState
    data class Current(val ahead: Boolean) : UpdateState
    data class Available(val release: AvailableRelease) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(val release: AvailableRelease, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
    data object Opened : UpdateState
}

@Composable
fun DesktopUpdateControl(canInstall: Boolean) {
    var visible by remember { mutableStateOf(false) }
    TextButton(onClick = { visible = true }) {
        val suffix = if (ReleaseInfo.isDevelopment) " Dev" else ""
        Text("gocast ${ReleaseInfo.version}$suffix · Updates", style = MaterialTheme.typography.bodySmall)
    }
    if (visible) UpdateDialog(canInstall, onDismiss = { visible = false })
}

@Composable
private fun UpdateDialog(canInstall: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val latestCanInstall by rememberUpdatedState(canInstall)
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var job by remember { mutableStateOf<Job?>(null) }
    var opening by remember { mutableStateOf(false) }

    fun check() {
        job?.cancel()
        state = UpdateState.Checking
        job = scope.launch {
            try {
                val release = GitHubUpdates.check()
                state = if (release.version > ReleaseInfo.version) {
                    val cached = release.asset?.let { GitHubUpdates.cachedDownload(it) }
                    if (cached == null) UpdateState.Available(release) else UpdateState.Ready(release, cached)
                } else UpdateState.Current(release.version < ReleaseInfo.version)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = UpdateState.Failed((error as? UpdateFailure)?.message ?: "Couldn't reach GitHub. Check your connection and try again.")
            }
        }
    }

    fun releases() {
        runCatching { GitHubUpdates.openReleases() }.onFailure {
            state = UpdateState.Failed("Couldn't open your browser. Releases are at github.com/${ReleaseInfo.repository}/releases.")
        }
    }

    LaunchedEffect(Unit) { check() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    AlertDialog(
        onDismissRequest = { job?.cancel(); onDismiss() },
        title = { Text("gocast updates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Version ${ReleaseInfo.version}${if (ReleaseInfo.isDevelopment) " · Development build" else ""}")
                when (val current = state) {
                    UpdateState.Checking -> {
                        Text("Checking GitHub for updates…")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Current -> Text(if (current.ahead) "This build is newer than the latest published release." else "You're up to date.")
                    is UpdateState.Available -> {
                        Text("Version ${current.release.version} is available.")
                        if (current.release.notes.isNotBlank()) {
                            Text(current.release.notes, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                        }
                        if (current.release.asset == null) Text("There isn't a compatible download available for this PC yet. You can check the release page.")
                    }
                    is UpdateState.Downloading -> {
                        Text("Downloading update · ${(current.progress * 100).toInt()}%")
                        LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Ready -> {
                        Text("Download verified. Open setup to finish updating.")
                        if (!canInstall) Text("Finish casting and disconnect your phone before opening setup.")
                    }
                    is UpdateState.Failed -> Text(current.message)
                    UpdateState.Opened -> Text("Setup is open. Follow its instructions to finish updating. gocast will stay open until you close it.")
                }
            }
        },
        confirmButton = {
            when (val current = state) {
                is UpdateState.Available -> {
                    val asset = current.release.asset
                    TextButton(onClick = {
                        if (asset == null) releases() else {
                            state = UpdateState.Downloading(0f)
                            job = scope.launch {
                                try {
                                    val file = GitHubUpdates.download(asset) { progress -> state = UpdateState.Downloading(progress) }
                                    state = UpdateState.Ready(current.release, file)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    state = UpdateState.Failed((error as? UpdateFailure)?.message ?: "The download couldn't finish. Try again, or open Releases to download it manually.")
                                }
                            }
                        }
                    }) { Text(if (asset == null) "Open Releases" else "Download update") }
                }
                is UpdateState.Ready -> TextButton(enabled = canInstall && !opening, onClick = {
                    opening = true
                    job = scope.launch {
                        try {
                            GitHubUpdates.verifyDownloaded(current.file, checkNotNull(current.release.asset))
                            if (latestCanInstall) {
                                GitHubUpdates.openInstaller(current.file)
                                state = UpdateState.Opened
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            state = UpdateState.Failed((error as? UpdateFailure)?.message ?: "Couldn't open setup. Open Releases to download and run it manually.")
                        } finally {
                            opening = false
                        }
                    }
                }) { Text(if (opening) "Checking download…" else "Open setup") }
                is UpdateState.Failed -> TextButton(onClick = ::check) { Text("Try again") }
                is UpdateState.Current -> TextButton(onClick = ::releases) { Text("Open Releases") }
                else -> Unit
            }
        },
        dismissButton = {
            Row {
                if (state is UpdateState.Failed) TextButton(onClick = ::releases) { Text("Open Releases") }
                TextButton(onClick = { job?.cancel(); onDismiss() }) {
                    Text(if (state is UpdateState.Downloading) "Cancel download" else "Close")
                }
            }
        },
    )
}
