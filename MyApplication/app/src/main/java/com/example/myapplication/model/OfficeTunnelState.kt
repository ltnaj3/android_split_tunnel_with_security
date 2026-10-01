package com.example.myapplication.model

/**
 * Observability state for the office-subnet IKEv2/IPsec control-plane tunnel, independent from
 * the overall [VpnState] of the app-owned [com.example.myapplication.vpn.OfficeVpnService] TUN.
 *
 * This lets the UI/logs distinguish "local TUN is up" (VpnState.Connected) from "the office IKE
 * Child SA is actually established" so the app never reports a false positive "Connected" for
 * office routing before real readiness.
 */
sealed class OfficeTunnelState {
    object Idle : OfficeTunnelState()
    object Negotiating : OfficeTunnelState()
    object ChildSaEstablished : OfficeTunnelState()
    data class ChildSaLost(val reason: String) : OfficeTunnelState()
    data class Failed(val reason: String) : OfficeTunnelState()

    val displayName: String
        get() = when (this) {
            is Idle -> "Idle"
            is Negotiating -> "Negotiating"
            is ChildSaEstablished -> "Child SA Established"
            is ChildSaLost -> "Child SA Lost: $reason"
            is Failed -> "Failed: $reason"
        }
}
