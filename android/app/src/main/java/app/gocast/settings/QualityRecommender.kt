package app.gocast.settings

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

data class QualityRecommendation(
    val profile: CastQualityProfile,
    val reason: String,
)

/**
 * Picks a sensible graphics profile from display size, RAM, CPU cores, and Wi‑Fi link speed.
 * Heuristic — not a benchmark — but matches how scrcpy users usually pick max-size/bitrate.
 */
object QualityRecommender {
    fun recommend(context: Context): QualityRecommendation {
        val metrics = context.resources.displayMetrics
        val pixels = metrics.widthPixels.toLong() * metrics.heightPixels.toLong()
        val cores = Runtime.getRuntime().availableProcessors()
        val ramMb = totalRamMb(context)
        val wifiMbps = wifiLinkMbps(context)

        val highRes = pixels >= 2_500_000L // ~1440×1600+ or 1080×2400
        val lowRam = ramMb in 1 until 4096
        val weakCpu = cores in 1..4
        val slowWifi = wifiMbps != null && wifiMbps < 50
        val fastWifi = wifiMbps != null && wifiMbps >= 200

        return when {
            lowRam || weakCpu || slowWifi -> QualityRecommendation(
                profile = CastQualityProfile.BuiltIns.first { it.id == CastQualityProfile.ID_SMOOTH }
                    .let { base ->
                        if (highRes) {
                            base.copy(
                                settings = base.settings.copy(resolutionScale = 0.75f),
                            )
                        } else {
                            base
                        }
                    },
                reason = buildString {
                    append("Smooth")
                    if (highRes) append(" (slightly smaller picture)")
                    append(" fits this phone best")
                    val bits = mutableListOf<String>()
                    if (lowRam) bits += "limited memory"
                    if (weakCpu) bits += "fewer CPU cores"
                    if (slowWifi) bits += "slower Wi‑Fi"
                    if (bits.isNotEmpty()) {
                        append(": ")
                        append(bits.joinToString(", "))
                    }
                    append(".")
                },
            )
            highRes && fastWifi && ramMb >= 6144 && cores >= 6 -> QualityRecommendation(
                profile = CastQualityProfile.BuiltIns.first { it.id == CastQualityProfile.ID_SHARP },
                reason = "Sharp: your phone and Wi‑Fi look strong enough for maximum clarity.",
            )
            else -> QualityRecommendation(
                profile = CastQualityProfile.BuiltIns.first { it.id == CastQualityProfile.ID_BALANCED },
                reason = "Balanced: a solid all-rounder for everyday casting.",
            )
        }
    }

    private fun totalRamMb(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java) ?: return 4096
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L)).toInt()
    }

    private fun wifiLinkMbps(context: Context): Int? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val kbps = if (Build.VERSION.SDK_INT >= 29) {
            caps.linkDownstreamBandwidthKbps
        } else {
            return null
        }
        if (kbps <= 0) return null
        return kbps / 1000
    }
}
