package app.gocast.network

import app.gocast.protocol.HostEndpoint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lightweight probe so connect failures can say *why* (firewall / guest Wi‑Fi /
 * VPN / wrong host) instead of a generic timeout.
 */
object LanHealthProbe {

    enum class Result {
        /** `/health` returned gocast's expected body. */
        GocastOk,
        /** TCP/HTTP answered but not gocast. */
        NotGocast,
        /** No usable response — blocked, offline, or wrong network. */
        Unreachable,
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun check(host: String, port: Int): Result {
        val url = "http://${HostEndpoint.authority(host, port)}/health"
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim().orEmpty()
                when {
                    response.isSuccessful && body.contains("gocast", ignoreCase = true) ->
                        Result.GocastOk
                    response.isSuccessful || response.code in 400..599 ->
                        Result.NotGocast
                    else -> Result.Unreachable
                }
            }
        } catch (_: Exception) {
            Result.Unreachable
        }
    }

    /** @param triedRemote the signaling room was tried first and the PC wasn't there either. */
    fun explainFailure(host: String, port: Int, cause: Throwable?, triedRemote: Boolean = false): String {
        return when (check(host, port)) {
            Result.GocastOk ->
                "Reached the PC but casting didn't start — open gocast on the PC and scan again."
            Result.NotGocast ->
                "Something else is using $host:$port — make sure gocast is running on the PC."
            Result.Unreachable -> unreachableMessage(cause, triedRemote)
        }
    }

    private fun unreachableMessage(cause: Throwable?, triedRemote: Boolean): String {
        if (triedRemote) {
            return "Can't reach the PC over the internet or this Wi‑Fi. Is gocast open on the PC?"
        }
        val tip =
            "Same Wi‑Fi (not guest)? VPN off? On the PC, allow gocast through Windows Firewall."
        return when (cause) {
            is java.net.UnknownHostException ->
                "Can't find that PC on Wi‑Fi. $tip"
            is java.net.ConnectException,
            is java.net.NoRouteToHostException,
            is java.net.SocketTimeoutException,
            ->
                "Can't reach the PC. $tip"
            else ->
                "Can't reach the PC. $tip"
        }
    }
}
