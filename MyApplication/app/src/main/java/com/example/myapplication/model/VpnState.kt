package com.example.myapplication.model

sealed class VpnState {
    object Disconnected : VpnState()
    object Connecting : VpnState()
    object Connected : VpnState()
    object Disconnecting : VpnState()
    data class Error(val message: String) : VpnState()

    val name: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Connecting -> "Connecting"
            is Connected -> "Connected"
            is Disconnecting -> "Disconnecting"
            is Error -> "Error: $message"
        }
}
