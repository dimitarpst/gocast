package app.gocast.settings

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CastQualityProfile(
    val id: String,
    val name: String,
    val builtIn: Boolean = false,
    val settings: CastQualitySettings,
) {
    companion object {
        const val ID_SMOOTH = "builtin_smooth"
        const val ID_BALANCED = "builtin_balanced"
        const val ID_SHARP = "builtin_sharp"

        val BuiltIns: List<CastQualityProfile> = listOf(
            CastQualityProfile(
                id = ID_SMOOTH,
                name = "Smooth",
                builtIn = true,
                settings = CastQualitySettings.Smooth,
            ),
            CastQualityProfile(
                id = ID_BALANCED,
                name = "Balanced",
                builtIn = true,
                settings = CastQualitySettings.Default,
            ),
            CastQualityProfile(
                id = ID_SHARP,
                name = "Sharp",
                builtIn = true,
                settings = CastQualitySettings.Sharp,
            ),
        )

        fun createUser(name: String, settings: CastQualitySettings): CastQualityProfile =
            CastQualityProfile(
                id = "user_${UUID.randomUUID()}",
                name = name.trim().ifEmpty { "Custom" },
                builtIn = false,
                settings = settings,
            )
    }
}
