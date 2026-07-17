package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.OptionDropdown
import app.gocast.ui.quality.components.SettingRow

@Composable
fun CaptureSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    SettingRow(
        help = SettingHelpCatalog.resolutionScale,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Resolution scale",
            selectedLabel = CastQualitySettings.ResolutionScaleOptions
                .firstOrNull { it.first == settings.resolutionScale }?.second
                ?: "${(settings.resolutionScale * 100).toInt()}%",
            options = CastQualitySettings.ResolutionScaleOptions.map { it.second },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        resolutionScale = CastQualitySettings.ResolutionScaleOptions[index].first,
                    ),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.captureFps,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Capture FPS",
            selectedLabel = "${settings.captureFps}",
            options = CastQualitySettings.FpsOptions.map { "$it" },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(captureFps = CastQualitySettings.FpsOptions[index]),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.virtualDisplayDpi,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Virtual display DPI",
            selectedLabel = "${settings.virtualDisplayDpi}",
            options = CastQualitySettings.DpiOptions.map { "$it" },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(virtualDisplayDpi = CastQualitySettings.DpiOptions[index]),
                )
            },
        )
    }
}
