package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.OptionPicker

@Composable
fun CaptureSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    val recommended = CastQualitySettings.Default
    OptionPicker(
        label = "Picture size",
        selectedLabel = CastQualitySettings.ResolutionScaleOptions
            .firstOrNull { it.first == settings.resolutionScale }?.second
            ?: "${(settings.resolutionScale * 100).toInt()}%",
        options = CastQualitySettings.ResolutionScaleOptions.map { it.second },
        recommendedIndex = CastQualitySettings.ResolutionScaleOptions
            .indexOfFirst { it.first == recommended.resolutionScale },
        help = SettingHelpCatalog.resolutionScale,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(
                    resolutionScale = CastQualitySettings.ResolutionScaleOptions[index].first,
                ),
            )
        },
    )
    OptionPicker(
        label = "Capture frame rate",
        selectedLabel = "${settings.captureFps} fps",
        options = CastQualitySettings.FpsOptions.map { "$it fps" },
        recommendedIndex = CastQualitySettings.FpsOptions.indexOf(recommended.captureFps),
        help = SettingHelpCatalog.captureFps,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(captureFps = CastQualitySettings.FpsOptions[index]),
            )
        },
    )
    OptionPicker(
        label = "Screen density",
        selectedLabel = "${settings.virtualDisplayDpi} dpi",
        options = CastQualitySettings.DpiOptions.map { "$it dpi" },
        recommendedIndex = CastQualitySettings.DpiOptions.indexOf(recommended.virtualDisplayDpi),
        help = SettingHelpCatalog.virtualDisplayDpi,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(virtualDisplayDpi = CastQualitySettings.DpiOptions[index]),
            )
        },
    )
}
