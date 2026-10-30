package app.gocast.ui.quality

import app.gocast.settings.CastQualitySettings
import app.gocast.settings.SettingHelp

data class QualityUiActions(
    val onChange: (CastQualitySettings) -> Unit,
    val onInfo: (SettingHelp) -> Unit,
)
