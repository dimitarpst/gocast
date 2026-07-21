package app.gocast.platform

/**
 * Best-effort prep for the WebView2 (Wry) path.
 * WebView2 reads these from the process environment when the Edge browser
 * process starts — set them before any WebView is created.
 */
object WebView2Support {
    private const val ARGS_ENV = "WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS"
    private const val AUTOPLAY_FLAG = "--autoplay-policy=no-user-gesture-required"

    /**
     * Opaque dark clear color (AARRGGBB). Applied before controller creation so
     * resize/navigate never paints the default white — API set alone can still flash.
     */
    private const val BG_ENV = "WEBVIEW2_DEFAULT_BACKGROUND_COLOR"
    private const val BG_DARK = "FF12141A"

    fun prepareProcessEnvironment() {
        ensureEnvContains(ARGS_ENV, AUTOPLAY_FLAG)
        runCatching { putEnv(BG_ENV, BG_DARK) }
    }

    /**
     * Java's environment map is normally immutable; on Windows HotSpot we can
     * still mutate the internal maps so child WebView2 picks up autoplay / bg.
     * If mutation fails, set the env var externally before launch.
     */
    private fun ensureEnvContains(key: String, flag: String) {
        val existing = System.getenv(key).orEmpty()
        if (existing.split(' ').any { it == flag }) return
        val merged = if (existing.isBlank()) flag else "$existing $flag"
        runCatching { putEnv(key, merged) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun putEnv(key: String, value: String) {
        val processEnvironment = Class.forName("java.lang.ProcessEnvironment")
        val theEnvironment = processEnvironment.getDeclaredField("theEnvironment")
        theEnvironment.isAccessible = true
        (theEnvironment.get(null) as MutableMap<String, String>)[key] = value

        val caseInsensitive =
            processEnvironment.getDeclaredField("theCaseInsensitiveEnvironment")
        caseInsensitive.isAccessible = true
        (caseInsensitive.get(null) as MutableMap<String, String>)[key] = value
    }
}
