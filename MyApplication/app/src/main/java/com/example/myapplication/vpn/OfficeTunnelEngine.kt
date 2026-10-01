package com.example.myapplication.vpn

import com.example.myapplication.model.VpnConfig

/**
 * Result of attempting to route a single office-subnet packet over the office tunnel dataplane.
 *
 * Implementations of [OfficeTunnelEngine] MUST NOT return [Routed] unless the packet was actually
 * handed off for real transmission toward the office gateway. If the tunnel is not ready, or the
 * dataplane is not (yet) implemented, implementations MUST return [Dropped] with an explicit,
 * human-readable reason so callers can log/meter the failure instead of silently pretending
 * success.
 */
sealed class OfficeTunnelRouteResult {
    data class Routed(val bytes: Int) : OfficeTunnelRouteResult()
    data class Dropped(val reason: String) : OfficeTunnelRouteResult()
}

/**
 * Abstraction over the office-subnet tunnel dataplane used by [OfficeVpnService].
 *
 * The app-owned [OfficeVpnService] TUN interface remains the single VPN owner on the device.
 * Implementations of this interface are responsible for negotiating/maintaining connectivity
 * to the office gateway (e.g. via IKEv2/IPsec) and, when possible, forwarding office-subnet
 * packets read from the TUN toward that gateway -- without ever creating a second, competing
 * VPN/tunnel interface.
 */
interface OfficeTunnelEngine {

    /** True once the tunnel has a usable, negotiated session ready to carry office traffic. */
    val isReady: Boolean

    /** Starts control-plane negotiation for the office tunnel. Asynchronous; non-blocking. */
    fun start(config: VpnConfig)

    /** Tears down the office tunnel session and releases any held resources. */
    fun stop()

    /**
     * Attempts to route a single office-subnet IP packet (as read from the TUN) over the tunnel.
     *
     * @param buffer the raw IP packet bytes (TUN read buffer).
     * @param length the valid length of [buffer].
     * @param parsedInfo pre-parsed packet metadata from [PacketParser].
     */
    fun routePacket(
        buffer: ByteArray,
        length: Int,
        parsedInfo: ParsedPacketInfo
    ): OfficeTunnelRouteResult
}
