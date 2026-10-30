package app.gocast.network

/**
 * Waits between attempts. Kept short: a failed attempt is cheap, and a mid-cast
 * phone waiting 10 s on a network that already works felt broken (30 Oct).
 * Network changes restart attempts on their own (NetworkWatch).
 */
class ReconnectPolicy (
    private val delaysMs: List<Long> = listOf(1_000L, 1_000L, 2_000L, 3_000L, 5_000L)
){
    fun delayMsForAttempt(attempt: Int): Long{
        if (attempt < 0) return delaysMs.first()
        if (attempt >= delaysMs.size) return delaysMs.last()
        return delaysMs[attempt]
    }
}