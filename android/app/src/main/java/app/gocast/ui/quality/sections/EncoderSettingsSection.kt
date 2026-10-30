package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.ToggleRow

@Composable
fun EncoderSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    val recommended = CastQualitySettings.Default
    ToggleRow(
        label = "VP8 hardware on Intel phones",
        checked = settings.enableIntelVp8Encoder,
        recommended = recommended.enableIntelVp8Encoder,
        help = SettingHelpCatalog.intelVp8,
        onInfo = actions.onInfo,
        onCheckedChange = {
            actions.onChange(settings.copy(enableIntelVp8Encoder = it))
        },
    )
    ToggleRow(
        label = "Better H.264 compression",
        checked = settings.enableH264HighProfile,
        recommended = recommended.enableH264HighProfile,
        help = SettingHelpCatalog.h264High,
        onInfo = actions.onInfo,
        onCheckedChange = {
            actions.onChange(settings.copy(enableH264HighProfile = it))
        },
    )
}
