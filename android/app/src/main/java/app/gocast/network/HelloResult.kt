package app.gocast.network

sealed interface HelloResult{
    data object Ok : HelloResult
    data class Failed(val reason: String) : HelloResult
}