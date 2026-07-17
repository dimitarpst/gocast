package app.gocast.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CastQualityStore {
    private const val PREFS = "gocast_quality"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_USER_PROFILES = "user_profiles_json"
    private const val KEY_RESOLUTION_SCALE = "resolution_scale"
    private const val KEY_CAPTURE_FPS = "capture_fps"
    private const val KEY_VIRTUAL_DPI = "virtual_dpi"
    private const val KEY_MAX_BITRATE = "max_bitrate_bps"
    private const val KEY_MIN_BITRATE = "min_bitrate_bps"
    private const val KEY_START_BITRATE = "start_bitrate_bps"
    private const val KEY_PREFERRED_CODEC = "preferred_codec"
    private const val KEY_MAX_FRAMERATE = "max_framerate"
    private const val KEY_SCALE_DOWN = "scale_resolution_down_by"
    private const val KEY_DEGRADATION = "degradation_preference"
    private const val KEY_BITRATE_PRIORITY = "bitrate_priority"
    private const val KEY_NETWORK_PRIORITY = "network_priority"
    private const val KEY_INTEL_VP8 = "enable_intel_vp8"
    private const val KEY_H264_HIGH = "enable_h264_high"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): CastQualitySettings {
        val p = prefs(context)
        val defaults = CastQualitySettings.Default
        return CastQualitySettings(
            resolutionScale = p.getFloat(KEY_RESOLUTION_SCALE, defaults.resolutionScale),
            captureFps = p.getInt(KEY_CAPTURE_FPS, defaults.captureFps),
            virtualDisplayDpi = p.getInt(KEY_VIRTUAL_DPI, defaults.virtualDisplayDpi),
            maxBitrateBps = p.getInt(KEY_MAX_BITRATE, defaults.maxBitrateBps),
            minBitrateBps = p.getInt(KEY_MIN_BITRATE, defaults.minBitrateBps),
            startBitrateBps = p.getInt(KEY_START_BITRATE, defaults.startBitrateBps),
            preferredCodec = runCatching {
                CastQualitySettings.VideoCodec.valueOf(
                    p.getString(KEY_PREFERRED_CODEC, defaults.preferredCodec.name)!!,
                )
            }.getOrDefault(defaults.preferredCodec),
            maxFramerate = p.getInt(KEY_MAX_FRAMERATE, defaults.maxFramerate),
            scaleResolutionDownBy = Double.fromBits(
                p.getLong(KEY_SCALE_DOWN, defaults.scaleResolutionDownBy.toRawBits()),
            ),
            degradationPreference = runCatching {
                CastQualitySettings.DegradationPreference.valueOf(
                    p.getString(KEY_DEGRADATION, defaults.degradationPreference.name)!!,
                )
            }.getOrDefault(defaults.degradationPreference),
            bitratePriority = Double.fromBits(
                p.getLong(KEY_BITRATE_PRIORITY, defaults.bitratePriority.toRawBits()),
            ),
            networkPriority = runCatching {
                CastQualitySettings.NetworkPriority.valueOf(
                    p.getString(KEY_NETWORK_PRIORITY, defaults.networkPriority.name)!!,
                )
            }.getOrDefault(defaults.networkPriority),
            enableIntelVp8Encoder = p.getBoolean(KEY_INTEL_VP8, defaults.enableIntelVp8Encoder),
            enableH264HighProfile = p.getBoolean(KEY_H264_HIGH, defaults.enableH264HighProfile),
        )
    }

    fun save(context: Context, settings: CastQualitySettings) {
        prefs(context).edit()
            .putFloat(KEY_RESOLUTION_SCALE, settings.resolutionScale)
            .putInt(KEY_CAPTURE_FPS, settings.captureFps)
            .putInt(KEY_VIRTUAL_DPI, settings.virtualDisplayDpi)
            .putInt(KEY_MAX_BITRATE, settings.maxBitrateBps)
            .putInt(KEY_MIN_BITRATE, settings.minBitrateBps)
            .putInt(KEY_START_BITRATE, settings.startBitrateBps)
            .putString(KEY_PREFERRED_CODEC, settings.preferredCodec.name)
            .putInt(KEY_MAX_FRAMERATE, settings.maxFramerate)
            .putLong(KEY_SCALE_DOWN, settings.scaleResolutionDownBy.toRawBits())
            .putString(KEY_DEGRADATION, settings.degradationPreference.name)
            .putLong(KEY_BITRATE_PRIORITY, settings.bitratePriority.toRawBits())
            .putString(KEY_NETWORK_PRIORITY, settings.networkPriority.name)
            .putBoolean(KEY_INTEL_VP8, settings.enableIntelVp8Encoder)
            .putBoolean(KEY_H264_HIGH, settings.enableH264HighProfile)
            .apply()
    }

    fun activeProfileId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_PROFILE_ID, null)

    fun setActiveProfileId(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
    }

    fun allProfiles(context: Context): List<CastQualityProfile> =
        CastQualityProfile.BuiltIns + loadUserProfiles(context)

    fun loadUserProfiles(context: Context): List<CastQualityProfile> {
        val raw = prefs(context).getString(KEY_USER_PROFILES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CastQualityProfile.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun saveUserProfiles(context: Context, profiles: List<CastQualityProfile>) {
        val usersOnly = profiles.filter { !it.builtIn }
        prefs(context).edit()
            .putString(KEY_USER_PROFILES, json.encodeToString(usersOnly))
            .apply()
    }

    fun upsertUserProfile(context: Context, profile: CastQualityProfile) {
        require(!profile.builtIn) { "Cannot overwrite a built-in profile" }
        val next = loadUserProfiles(context)
            .filterNot { it.id == profile.id } + profile
        saveUserProfiles(context, next)
    }

    fun deleteUserProfile(context: Context, id: String) {
        saveUserProfiles(context, loadUserProfiles(context).filterNot { it.id == id })
        if (activeProfileId(context) == id) {
            setActiveProfileId(context, CastQualityProfile.ID_BALANCED)
        }
    }

    fun applyProfile(context: Context, profile: CastQualityProfile) {
        save(context, profile.settings)
        setActiveProfileId(context, profile.id)
    }
}
