package app.gocast.network

class ReconnectPolicy (
    private val delaysMs: List<Long> = listOf(1_000L, 2_000L, 5_000L, 10_000L)
){
    fun delayMsForAttempt(attempt: Int): Long{
        if (attempt < 0) return delaysMs.first()
        if (attempt >= delaysMs.size) return delaysMs.last()
        return delaysMs[attempt]
    }
}