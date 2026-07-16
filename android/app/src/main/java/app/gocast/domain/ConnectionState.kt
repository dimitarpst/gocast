package app.gocast.domain

sealed interface ConnectionState{
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object Reconnecting : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}