package app.gocast.settings

/**
 * How heavily a knob taxes a given resource, and what it mainly buys you.
 */
enum class CostWeight {
    LIGHT,
    NOTICEABLE,
    HEAVY,
}

enum class CostAxis {
    CLARITY,
    MOTION,
    WIFI,
    PHONE,
    LATENCY,
}

data class CostChip(
    val axis: CostAxis,
    val weight: CostWeight,
) {
    val axisLabel: String
        get() = when (axis) {
            CostAxis.CLARITY -> "Clarity"
            CostAxis.MOTION -> "Motion"
            CostAxis.WIFI -> "Wi‑Fi"
            CostAxis.PHONE -> "Phone"
            CostAxis.LATENCY -> "Latency"
        }

    val weightLabel: String
        get() = when (weight) {
            CostWeight.LIGHT -> "Light impact"
            CostWeight.NOTICEABLE -> "Noticeable impact"
            CostWeight.HEAVY -> "Heavy impact"
        }
}

data class SettingHelp(
    val title: String,
    val body: String,
    val costs: List<CostChip>,
)

/** Plain-language help for every quality knob. */
object SettingHelpCatalog {
    val resolutionScale = SettingHelp(
        title = "Resolution scale",
        body = "How big the picture is that gets sent to your PC.\n\n" +
            "100% means every pixel from your phone screen. " +
            "Lower values shrink the picture first — it uses less Wi‑Fi and phone power, " +
            "but text and fine detail look softer.\n\n" +
            "Tip: a slightly smaller, clear picture usually looks better than a huge " +
            "picture that your network can't keep up with.",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.HEAVY),
            CostChip(CostAxis.WIFI, CostWeight.HEAVY),
            CostChip(CostAxis.PHONE, CostWeight.HEAVY),
        ),
    )

    val captureFps = SettingHelp(
        title = "Capture FPS",
        body = "How many times per second your phone grabs a new frame of the screen.\n\n" +
            "Higher = smoother scrolling and video. Lower = cooler phone and less Wi‑Fi use.\n\n" +
            "60 feels great for games and video. 30 is plenty for reading apps or browsing.",
        costs = listOf(
            CostChip(CostAxis.MOTION, CostWeight.HEAVY),
            CostChip(CostAxis.PHONE, CostWeight.NOTICEABLE),
            CostChip(CostAxis.WIFI, CostWeight.NOTICEABLE),
        ),
    )

    val virtualDisplayDpi = SettingHelp(
        title = "Virtual display DPI",
        body = "Tells Android how “dense” the fake screen used for casting should feel.\n\n" +
            "Most people can leave this alone. Change it only if something on the cast " +
            "looks oddly zoomed or tiny compared to the real phone.",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.LIGHT),
            CostChip(CostAxis.PHONE, CostWeight.LIGHT),
        ),
    )

    val preferredCodec = SettingHelp(
        title = "Preferred codec",
        body = "A codec is how your phone compresses the screen picture before sending it over Wi‑Fi. " +
            "The right choice can keep the phone cool and the text sharp; the wrong one can heat the " +
            "phone and make everything look soft — often more than changing bitrate a little.\n\n" +
            "• H.264 — Best default. Uses a dedicated chip on most phones (cooler, sharper text). " +
            "Almost every PC can play it.\n\n" +
            "• H.265 — Same hardware idea, often clearer at the same Wi‑Fi use. " +
            "Your PC must support it or the cast may fail or fall back.\n\n" +
            "• VP8 / VP9 — Often run in software on Android, so the phone works harder and " +
            "screen text can look mushier. Fine as fallbacks if H.264/H.265 aren’t available.\n\n" +
            "• AV1 — Rare on phones. Only pick this if you know both the phone and the PC support it.\n\n" +
            "Tip: stick with H.264 unless you’re experimenting. A software codec hurts Phone and " +
            "Clarity more than bumping max bitrate one step.\n\n" +
            "Changing this while casting briefly reconnects the video (no need to reshare).",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.HEAVY),
            CostChip(CostAxis.PHONE, CostWeight.HEAVY),
            CostChip(CostAxis.LATENCY, CostWeight.NOTICEABLE),
        ),
    )

    val maxBitrate = SettingHelp(
        title = "Max bitrate",
        body = "The upper limit on how much data per second the cast may use.\n\n" +
            "Higher = sharper picture when Wi‑Fi is strong. " +
            "Too high for your network causes lag and stuttering.\n\n" +
            "A good everyday starting point is around 8 Mbps on home Wi‑Fi. " +
            "If the picture looks blocky, try raising this — or lowering resolution if Wi‑Fi is weak.",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.HEAVY),
            CostChip(CostAxis.WIFI, CostWeight.HEAVY),
            CostChip(CostAxis.LATENCY, CostWeight.NOTICEABLE),
        ),
    )

    val minBitrate = SettingHelp(
        title = "Min bitrate",
        body = "The lowest quality floor the cast tries to stay above when the network dips.\n\n" +
            "Keeps things from suddenly turning into mush. " +
            "Set to 0 if you want no floor. " +
            "Too high on a weak connection can cause freezes instead of a softer picture.",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.NOTICEABLE),
            CostChip(CostAxis.WIFI, CostWeight.NOTICEABLE),
            CostChip(CostAxis.LATENCY, CostWeight.LIGHT),
        ),
    )

    val startBitrate = SettingHelp(
        title = "Start bitrate",
        body = "Quality when the cast first connects.\n\n" +
            "Without this, the stream often begins blurry and slowly improves. " +
            "Setting a solid start makes the first seconds look much closer to the final quality.\n\n" +
            "Pick something near the max bitrate you usually use.",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.HEAVY),
            CostChip(CostAxis.WIFI, CostWeight.NOTICEABLE),
            CostChip(CostAxis.LATENCY, CostWeight.LIGHT),
        ),
    )

    val maxFramerate = SettingHelp(
        title = "Max framerate",
        body = "Cap on how many frames per second actually leave the phone.\n\n" +
            "Pair it with Capture FPS. Use 60 for games and video, 30 for reading and browsing " +
            "to save battery and Wi‑Fi.",
        costs = listOf(
            CostChip(CostAxis.MOTION, CostWeight.HEAVY),
            CostChip(CostAxis.WIFI, CostWeight.NOTICEABLE),
            CostChip(CostAxis.PHONE, CostWeight.NOTICEABLE),
        ),
    )

    val scaleDown = SettingHelp(
        title = "Scale resolution down by",
        body = "An extra shrink applied while compressing the video.\n\n" +
            "2× means half the width and height at encode time. " +
            "Prefer Resolution scale for the main size choice; use this only if you need " +
            "another cut without changing capture size.",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.HEAVY),
            CostChip(CostAxis.WIFI, CostWeight.HEAVY),
            CostChip(CostAxis.PHONE, CostWeight.NOTICEABLE),
        ),
    )

    val degradation = SettingHelp(
        title = "When the connection struggles",
        body = "What to sacrifice first if Wi‑Fi or the phone can’t keep up:\n\n" +
            "• Keep resolution — picture stays sharp; motion may hitch (great for text).\n" +
            "• Keep framerate — motion stays smooth; picture may soften (great for games).\n" +
            "• Balanced — a bit of both.\n" +
            "• Disabled — don’t auto-adjust.",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.HEAVY),
            CostChip(CostAxis.MOTION, CostWeight.HEAVY),
            CostChip(CostAxis.LATENCY, CostWeight.NOTICEABLE),
        ),
    )

    val networkPriority = SettingHelp(
        title = "Network priority",
        body = "Hints how important this cast’s traffic is on the network.\n\n" +
            "With only one stream, High is fine. This rarely changes what you see day to day.",
        costs = listOf(
            CostChip(CostAxis.WIFI, CostWeight.LIGHT),
            CostChip(CostAxis.LATENCY, CostWeight.LIGHT),
        ),
    )

    val bitratePriority = SettingHelp(
        title = "Bitrate priority",
        body = "A fine-tuning knob for how aggressively bitrate is claimed.\n\n" +
            "Max bitrate matters much more. Leave this at 1.0 unless you’re experimenting.",
        costs = listOf(
            CostChip(CostAxis.WIFI, CostWeight.LIGHT),
            CostChip(CostAxis.CLARITY, CostWeight.LIGHT),
        ),
    )

    val intelVp8 = SettingHelp(
        title = "Intel VP8 encoder",
        body = "Optional encoder path used only with some codecs.\n\n" +
            "Safe to leave on. Screen casting usually prefers H.264 anyway.\n\n" +
            "Changing this while casting briefly rebuilds the encoder (no need to reshare).",
        costs = listOf(
            CostChip(CostAxis.PHONE, CostWeight.LIGHT),
            CostChip(CostAxis.CLARITY, CostWeight.LIGHT),
        ),
    )

    val h264High = SettingHelp(
        title = "H.264 high profile",
        body = "Lets the phone’s H.264 encoder use a richer compression mode.\n\n" +
            "Usually means a clearer picture at the same Wi‑Fi budget. " +
            "Turn off only if casting fails on a quirky device.\n\n" +
            "Changing this while casting briefly rebuilds the encoder (no need to reshare).",
        costs = listOf(
            CostChip(CostAxis.CLARITY, CostWeight.NOTICEABLE),
            CostChip(CostAxis.PHONE, CostWeight.LIGHT),
        ),
    )
}
