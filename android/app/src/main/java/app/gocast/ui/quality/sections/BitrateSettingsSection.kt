package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.OptionDropdown
import app.gocast.ui.quality.components.SettingRow

@Composable
fun BitrateSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    SettingRow(
        help = SettingHelpCatalog.maxBitrate,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Max bitrate",
            selectedLabel = "${settings.maxBitrateBps / 1_000_000} Mbps",
            options = CastQualitySettings.MaxBitrateMbpsOptions.map { "$it Mbps" },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        maxBitrateBps = CastQualitySettings.MaxBitrateMbpsOptions[index] * 1_000_000,
                    ),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.minBitrate,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Min bitrate",
            selectedLabel = if (settings.minBitrateBps == 0) {
                "0 (unlimited floor)"
            } else {
                "${settings.minBitrateBps / 1_000_000} Mbps"
            },
            options = CastQualitySettings.MinBitrateMbpsOptions.map {
                if (it == 0) "0 (unlimited floor)" else "$it Mbps"
            },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        minBitrateBps = CastQualitySettings.MinBitrateMbpsOptions[index] * 1_000_000,
                    ),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.startBitrate,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Start bitrate",
            selectedLabel = "${settings.startBitrateBps / 1_000_000} Mbps",
            options = CastQualitySettings.StartBitrateMbpsOptions.map { "$it Mbps" },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        startBitrateBps = CastQualitySettings.StartBitrateMbpsOptions[index] * 1_000_000,
                    ),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.maxFramerate,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Max framerate",
            selectedLabel = "${settings.maxFramerate}",
            options = CastQualitySettings.FpsOptions.map { "$it" },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(maxFramerate = CastQualitySettings.FpsOptions[index]),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.scaleDown,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Scale resolution down by",
            selectedLabel = CastQualitySettings.ScaleDownOptions
                .firstOrNull { it.first == settings.scaleResolutionDownBy }?.second
                ?: "${settings.scaleResolutionDownBy}×",
            options = CastQualitySettings.ScaleDownOptions.map { it.second },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        scaleResolutionDownBy = CastQualitySettings.ScaleDownOptions[index].first,
                    ),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.degradation,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "When the connection struggles",
            selectedLabel = settings.degradationPreference.name,
            options = CastQualitySettings.DegradationPreference.entries.map { it.name },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        degradationPreference =
                            CastQualitySettings.DegradationPreference.entries[index],
                    ),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.networkPriority,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Network priority",
            selectedLabel = settings.networkPriority.name,
            options = CastQualitySettings.NetworkPriority.entries.map { it.name },
            onSelectIndex = { index ->
                actions.onChange(
                    settings.copy(
                        networkPriority = CastQualitySettings.NetworkPriority.entries[index],
                    ),
                )
            },
        )
    }
    SettingRow(
        help = SettingHelpCatalog.bitratePriority,
        onInfo = actions.onInfo,
        onCostTap = actions.onCostTap,
    ) {
        OptionDropdown(
            label = "Bitrate priority",
            selectedLabel = settings.bitratePriority.toString(),
            options = listOf("0.5", "1.0", "2.0", "4.0"),
            onSelectIndex = { index ->
                val values = listOf(0.5, 1.0, 2.0, 4.0)
                actions.onChange(settings.copy(bitratePriority = values[index]))
            },
        )
    }
}
