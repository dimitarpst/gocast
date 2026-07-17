package app.gocast.settings

/** Classifies which live-apply path a settings change needs. */
object CastQualityDiff {
    fun needsFactoryRebuild(old: CastQualitySettings, new: CastQualitySettings): Boolean =
        old.enableIntelVp8Encoder != new.enableIntelVp8Encoder ||
            old.enableH264HighProfile != new.enableH264HighProfile

    fun needsRenegotiation(old: CastQualitySettings, new: CastQualitySettings): Boolean =
        old.preferredCodec != new.preferredCodec

    fun needsCaptureUpdate(old: CastQualitySettings, new: CastQualitySettings): Boolean =
        old.resolutionScale != new.resolutionScale ||
            old.captureFps != new.captureFps ||
            old.virtualDisplayDpi != new.virtualDisplayDpi

    fun needsRtpUpdate(old: CastQualitySettings, new: CastQualitySettings): Boolean =
        old.maxBitrateBps != new.maxBitrateBps ||
            old.minBitrateBps != new.minBitrateBps ||
            old.startBitrateBps != new.startBitrateBps ||
            old.maxFramerate != new.maxFramerate ||
            old.scaleResolutionDownBy != new.scaleResolutionDownBy ||
            old.degradationPreference != new.degradationPreference ||
            old.bitratePriority != new.bitratePriority ||
            old.networkPriority != new.networkPriority
}
