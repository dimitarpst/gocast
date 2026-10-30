package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.CastQualitySettings.DegradationPreference
import app.gocast.settings.CastQualitySettings.NetworkPriority
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.OptionPicker

private val BitratePriorityOptions = listOf(0.5 to "Lower", 1.0 to "Normal", 2.0 to "Higher", 4.0 to "Highest")

private fun maxBitrateLabel(mbps: Int): String = when (mbps) {
    2 -> "2 Mbps · weak Wi‑Fi"
    8 -> "8 Mbps · home Wi‑Fi"
    16 -> "16 Mbps · fast Wi‑Fi"
    else -> "$mbps Mbps"
}

private fun minBitrateLabel(mbps: Int): String = if (mbps == 0) "No minimum" else "$mbps Mbps"

private fun degradationLabel(pref: DegradationPreference): String = when (pref) {
    DegradationPreference.DISABLED -> "Do nothing"
    DegradationPreference.MAINTAIN_FRAMERATE -> "Keep it smooth"
    DegradationPreference.MAINTAIN_RESOLUTION -> "Keep it sharp"
    DegradationPreference.BALANCED -> "Balance both"
}

private fun networkPriorityLabel(priority: NetworkPriority): String = when (priority) {
    NetworkPriority.VERY_LOW -> "Very low"
    NetworkPriority.LOW -> "Low"
    NetworkPriority.MEDIUM -> "Medium"
    NetworkPriority.HIGH -> "High"
}

@Composable
fun BitrateSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    val recommended = CastQualitySettings.Default
    OptionPicker(
        label = "Max bitrate",
        selectedLabel = maxBitrateLabel(settings.maxBitrateBps / 1_000_000),
        options = CastQualitySettings.MaxBitrateMbpsOptions.map(::maxBitrateLabel),
        recommendedIndex = CastQualitySettings.MaxBitrateMbpsOptions
            .indexOf(recommended.maxBitrateBps / 1_000_000),
        help = SettingHelpCatalog.maxBitrate,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(
                    maxBitrateBps = CastQualitySettings.MaxBitrateMbpsOptions[index] * 1_000_000,
                ),
            )
        },
    )
    OptionPicker(
        label = "Min bitrate",
        selectedLabel = minBitrateLabel(settings.minBitrateBps / 1_000_000),
        options = CastQualitySettings.MinBitrateMbpsOptions.map(::minBitrateLabel),
        recommendedIndex = CastQualitySettings.MinBitrateMbpsOptions
            .indexOf(recommended.minBitrateBps / 1_000_000),
        help = SettingHelpCatalog.minBitrate,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(
                    minBitrateBps = CastQualitySettings.MinBitrateMbpsOptions[index] * 1_000_000,
                ),
            )
        },
    )
    OptionPicker(
        label = "Start bitrate",
        selectedLabel = "${settings.startBitrateBps / 1_000_000} Mbps",
        options = CastQualitySettings.StartBitrateMbpsOptions.map { "$it Mbps" },
        recommendedIndex = CastQualitySettings.StartBitrateMbpsOptions
            .indexOf(recommended.startBitrateBps / 1_000_000),
        help = SettingHelpCatalog.startBitrate,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(
                    startBitrateBps = CastQualitySettings.StartBitrateMbpsOptions[index] * 1_000_000,
                ),
            )
        },
    )
}

@Composable
fun StreamingSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    val recommended = CastQualitySettings.Default
    OptionPicker(
        label = "Max frame rate",
        selectedLabel = "${settings.maxFramerate} fps",
        options = CastQualitySettings.FpsOptions.map { "$it fps" },
        recommendedIndex = CastQualitySettings.FpsOptions.indexOf(recommended.maxFramerate),
        help = SettingHelpCatalog.maxFramerate,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(maxFramerate = CastQualitySettings.FpsOptions[index]),
            )
        },
    )
    OptionPicker(
        label = "Extra shrink",
        selectedLabel = CastQualitySettings.ScaleDownOptions
            .firstOrNull { it.first == settings.scaleResolutionDownBy }?.second
            ?: "${settings.scaleResolutionDownBy}× smaller",
        options = CastQualitySettings.ScaleDownOptions.map { it.second },
        recommendedIndex = CastQualitySettings.ScaleDownOptions
            .indexOfFirst { it.first == recommended.scaleResolutionDownBy },
        help = SettingHelpCatalog.scaleDown,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(
                    scaleResolutionDownBy = CastQualitySettings.ScaleDownOptions[index].first,
                ),
            )
        },
    )
    OptionPicker(
        label = "When the connection struggles",
        selectedLabel = degradationLabel(settings.degradationPreference),
        options = DegradationPreference.entries.map(::degradationLabel),
        recommendedIndex = recommended.degradationPreference.ordinal,
        help = SettingHelpCatalog.degradation,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(degradationPreference = DegradationPreference.entries[index]),
            )
        },
    )
    OptionPicker(
        label = "Wi‑Fi priority",
        selectedLabel = networkPriorityLabel(settings.networkPriority),
        options = NetworkPriority.entries.map(::networkPriorityLabel),
        recommendedIndex = recommended.networkPriority.ordinal,
        help = SettingHelpCatalog.networkPriority,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(networkPriority = NetworkPriority.entries[index]),
            )
        },
    )
    OptionPicker(
        label = "Video priority",
        selectedLabel = BitratePriorityOptions
            .firstOrNull { it.first == settings.bitratePriority }?.second
            ?: "${settings.bitratePriority}×",
        options = BitratePriorityOptions.map { it.second },
        recommendedIndex = BitratePriorityOptions
            .indexOfFirst { it.first == recommended.bitratePriority },
        help = SettingHelpCatalog.bitratePriority,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(settings.copy(bitratePriority = BitratePriorityOptions[index].first))
        },
    )
}
