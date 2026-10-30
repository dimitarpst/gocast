package app.gocast.ui.quality.sections

import androidx.compose.runtime.Composable
import app.gocast.settings.CastQualitySettings
import app.gocast.settings.CastQualitySettings.VideoCodec
import app.gocast.settings.SettingHelpCatalog
import app.gocast.ui.quality.QualityUiActions
import app.gocast.ui.quality.components.OptionPicker

private fun codecLabel(codec: VideoCodec): String = when (codec) {
    VideoCodec.H264 -> "H.264 · most compatible"
    VideoCodec.H265 -> "H.265 · sharper, if your PC supports it"
    VideoCodec.VP8 -> "VP8 · backup"
    VideoCodec.VP9 -> "VP9 · backup"
    VideoCodec.AV1 -> "AV1 · rarely supported"
}

@Composable
fun CodecSettingsSection(
    settings: CastQualitySettings,
    actions: QualityUiActions,
) {
    OptionPicker(
        label = "Preferred format",
        selectedLabel = codecLabel(settings.preferredCodec),
        options = VideoCodec.entries.map(::codecLabel),
        recommendedIndex = CastQualitySettings.Default.preferredCodec.ordinal,
        help = SettingHelpCatalog.preferredCodec,
        onInfo = actions.onInfo,
        onSelectIndex = { index ->
            actions.onChange(
                settings.copy(preferredCodec = VideoCodec.entries[index]),
            )
        },
    )
}
