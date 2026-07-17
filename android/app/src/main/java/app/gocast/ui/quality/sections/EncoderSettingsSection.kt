package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.SettingRow
import app.gocast.ui.quality.components.ToggleRow

@Composable
fun EncoderSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    SettingRow(
        help = SettingHelpCatalog.intelVp8,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        ToggleRow(
            label = "Intel VP8 encoder",
            checked = settings.enableIntelVp8Encoder,
            onCheckedChange = {
                actions.onChange(settings.copy(enableIntelVp8Encoder = it))
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.h264High,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        ToggleRow(
            label = "H.264 high profile",
            checked = settings.enableH264HighProfile,
            onCheckedChange = {
                actions.onChange(settings.copy(enableH264HighProfile = it))
            },
        )
    }
}
