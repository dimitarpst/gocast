package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.OptionDropdown
import app.gocast.ui.quality.components.SettingRow

@Composable
fun CodecSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    SettingRow(
        help = SettingHelpCatalog.preferredCodec,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Preferred codec",
            selectedLabel = settings.preferredCodec.name,
            options = CastQualitySettings.VideoCodec.entries.map { it.name },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        preferredCodec = CastQualitySettings.VideoCodec.entries[index],
                    ),
                )
            },
        )
    }
}
