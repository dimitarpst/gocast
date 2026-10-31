package app.gocast.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PhoneUpdateState {
    data object None : PhoneUpdateState
    data class Available(val update: PhoneUpdate) : PhoneUpdateState
    data class Downloading(val update: PhoneUpdate, val progress: Float) : PhoneUpdateState
    /** Downloaded; Android must first allow GoCast to install apps (asked once). */
    data class NeedsPermission(val update: PhoneUpdate, val apk: File) : PhoneUpdateState
    data class Failed(val update: PhoneUpdate, val message: String) : PhoneUpdateState
}

/** Checks GitHub once per app start; the Update card on Home drives the rest. */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<PhoneUpdateState>(PhoneUpdateState.None)
    val state: StateFlow<PhoneUpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        if (PhoneUpdates.enabled) {
            viewModelScope.launch {
                val update = runCatching { PhoneUpdates.check() }.getOrNull() ?: return@launch
                _state.value = PhoneUpdateState.Available(update)
            }
        }
        viewModelScope.launch {
            PhoneUpdates.installFailure.collect { message ->
                val update = currentUpdate() ?: return@collect
                if (message != null) _state.value = PhoneUpdateState.Failed(update, message)
            }
        }
    }

    fun startUpdate() {
        val update = currentUpdate() ?: return
        if (downloadJob?.isActive == true) return
        PhoneUpdates.clearInstallFailure()
        downloadJob = viewModelScope.launch {
            try {
                _state.value = PhoneUpdateState.Downloading(update, 0f)
                val apk = PhoneUpdates.download(getApplication(), update) { progress ->
                    _state.value = PhoneUpdateState.Downloading(update, progress)
                }
                installOrAsk(update, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = PhoneUpdateState.Failed(update, e.message ?: "The download failed")
            }
        }
    }

    /** Back from Android's "Install unknown apps" setting: carry on if it was allowed. */
    fun onResumed() {
        val waiting = _state.value as? PhoneUpdateState.NeedsPermission ?: return
        if (PhoneUpdates.canInstall(getApplication())) installOrAsk(waiting.update, waiting.apk)
    }

    private fun installOrAsk(update: PhoneUpdate, apk: File) {
        if (!PhoneUpdates.canInstall(getApplication())) {
            _state.value = PhoneUpdateState.NeedsPermission(update, apk)
            return
        }
        runCatching { PhoneUpdates.install(getApplication(), apk) }
            .onSuccess {
                // Android's own confirmation follows. If the user cancels it, Update is still there.
                _state.value = PhoneUpdateState.Available(update)
            }
            .onFailure {
                _state.value = PhoneUpdateState.Failed(update, it.message ?: "Could not open the installer")
            }
    }

    private fun currentUpdate(): PhoneUpdate? = when (val state = _state.value) {
        PhoneUpdateState.None -> null
        is PhoneUpdateState.Available -> state.update
        is PhoneUpdateState.Downloading -> state.update
        is PhoneUpdateState.NeedsPermission -> state.update
        is PhoneUpdateState.Failed -> state.update
    }
}
