package com.example.myapplication.vpn

import android.content.Context
import android.os.Build
import com.example.myapplication.model.LogLevel
import com.example.myapplication.model.OfficeTunnelState
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.repository.VpnRepository

/**
 * Office-subnet tunnel implementation built on Android's native IKEv2/IPsec control-plane
 * library via [IkeSessionController].
 *
 * ARCHITECTURE: This class never creates its own VPN/TUN interface. [OfficeVpnService] remains
 * the single app-owned VpnService TUN; this class only negotiates/maintains the IKE SA + Child
 * SA used to prove real connectivity to the office gateway (e.g. a Check Point IPsec gateway).
 *
 * KNOWN LIMITATION (intentionally not hidden): real kernel-assisted ESP tunnel-mode packet
 * forwarding (`IpSecManager.IpSecTunnelInterface`) requires the system-only
 * `MANAGE_IPSEC_TUNNELS` permission, unavailable to this third-party app. Until an in-process
 * ESP dataplane is implemented, office-subnet packets are intentionally NOT forwarded here --
 * [routePacket] always returns [OfficeTunnelRouteResult.Dropped] with an explicit reason instead
 * of pretending traffic reached the gateway. The IKE control-plane negotiation itself is real and
 * fully functional, and is useful to validate gateway reachability/credentials/proposals.
 */
class IpsecOfficeTunnel(
    private val context: Context,
    private val repository: VpnRepository
) : OfficeTunnelEngine {

    private var controller: IkeSessionController? = null

    @Volatile
    private var childSaEstablished: Boolean = false

    override val isReady: Boolean
        get() = childSaEstablished

    override fun start(config: VpnConfig) {
        if (controller != null) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            repository.updateOfficeTunnelState(
                OfficeTunnelState.Failed("Requires Android 12 (API 31)+ for built-in IKE library")
            )
            repository.addLog(
                LogLevel.ERROR,
                "Office IKE negotiation skipped: unsupported Android version",
                "Running API ${Build.VERSION.SDK_INT}, built-in android.net.ipsec.ike requires API 31+"
            )
            return
        }

        repository.updateOfficeTunnelState(OfficeTunnelState.Negotiating)
        repository.addLog(
            LogLevel.INFO,
            "IKE negotiation starting",
            "Gateway: ${config.officeGatewayIp}, Auth: ${config.authType.displayName}, " +
                "Subnets: ${config.officeSubnets.joinToString { it.toString() }}"
        )

        val ikeController = IkeSessionController(context) { event -> handleEvent(config, event) }
        controller = ikeController
        ikeController.start(config)
    }

    override fun stop() {
        controller?.stop()
        controller = null
        childSaEstablished = false
        repository.updateOfficeTunnelState(OfficeTunnelState.Idle)
    }

    override fun routePacket(
        buffer: ByteArray,
        length: Int,
        parsedInfo: ParsedPacketInfo
    ): OfficeTunnelRouteResult {
        if (!childSaEstablished) {
            return OfficeTunnelRouteResult.Dropped(
                "Office tunnel Child SA not established (control-plane negotiating or unavailable)"
            )
        }

        // Honest gating -- see class doc: ESP packet-level dataplane is not implemented in this
        // build, so a successfully negotiated Child SA still cannot carry real traffic yet.
        return OfficeTunnelRouteResult.Dropped(
            "Office ESP dataplane not implemented in this build (IKE control-plane only); " +
                "packet to ${parsedInfo.destinationIp}:${parsedInfo.destinationPort} not forwarded"
        )
    }

    private fun handleEvent(config: VpnConfig, event: IkeControlPlaneEvent) {
        when (event) {
            is IkeControlPlaneEvent.Connecting -> {
                repository.updateOfficeTunnelState(OfficeTunnelState.Negotiating)
                repository.addLog(
                    LogLevel.INFO,
                    "IKE_INIT/IKE_AUTH exchange starting",
                    "Gateway: ${config.officeGatewayIp}"
                )
            }
            is IkeControlPlaneEvent.IkeSessionUp -> {
                repository.addLog(
                    LogLevel.INFO,
                    "IKE Session established",
                    "Remote: ${event.remoteAddress}"
                )
            }
            is IkeControlPlaneEvent.ChildSaUp -> {
                childSaEstablished = true
                repository.updateOfficeTunnelState(OfficeTunnelState.ChildSaEstablished)
                repository.addLog(
                    LogLevel.INFO,
                    "Child SA established for office subnet traffic selectors",
                    "Gateway: ${config.officeGatewayIp}"
                )
            }
            is IkeControlPlaneEvent.ChildSaDown -> {
                childSaEstablished = false
                repository.updateOfficeTunnelState(OfficeTunnelState.ChildSaLost(event.reason))
                repository.addLog(LogLevel.WARN, "Child SA lost", event.reason)
            }
            is IkeControlPlaneEvent.Closed -> {
                childSaEstablished = false
                repository.updateOfficeTunnelState(OfficeTunnelState.Idle)
                repository.addLog(
                    LogLevel.INFO,
                    "IKE session closed",
                    event.reason
                )
            }
            is IkeControlPlaneEvent.Error -> {
                childSaEstablished = false
                repository.updateOfficeTunnelState(OfficeTunnelState.Failed(event.reason))
                repository.addLog(
                    LogLevel.ERROR,
                    "IKE negotiation error",
                    "${event.reason}${event.code?.let { " (code=$it)" } ?: ""}"
                )
            }
        }
    }
}
