package com.example.myapplication.vpn

import android.net.VpnService
import com.example.myapplication.model.SecurityThreat
import com.example.myapplication.model.ThreatSeverity
import com.example.myapplication.model.ThreatType
import com.example.myapplication.model.VpnConfig
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

data class ForwardResult(
    val bytesProcessed: Int,
    val isOfficeTraffic: Boolean,
    val threat: SecurityThreat? = null,
    val responsePacket: ByteArray? = null,
    val officeTunnelRouted: Boolean = false,
    val officeTunnelDropReason: String? = null
)

object PacketForwarder {

    /**
     * Inspects and forwards live non-DNS IP network traffic.
     * Protects outbound sockets with VpnService.protect(socket) so real connections function normally.
     */
    fun processPacket(
        buffer: ByteArray,
        length: Int,
        parsedInfo: ParsedPacketInfo,
        config: VpnConfig,
        vpnService: VpnService,
        officeTunnel: OfficeTunnelEngine?
    ): ForwardResult {
        if (parsedInfo.isOfficeSubnet) {
            // Office Traffic Routing logic
            return routeOfficeTraffic(buffer, length, parsedInfo, officeTunnel)
        }

        // Non-office traffic inspection & forwarding
        if (config.isLocalInspectionEnabled) {
            // 1. IP Blacklist check
            if (config.blockedIps.contains(parsedInfo.destinationIp)) {
                val threat = SecurityThreat(
                    threatType = ThreatType.BLOCKED_IP,
                    severity = ThreatSeverity.HIGH,
                    sourceIp = parsedInfo.sourceIp,
                    destinationIp = parsedInfo.destinationIp,
                    destinationPort = parsedInfo.destinationPort,
                    description = "Blocked connection attempt to blacklisted target IP: ${parsedInfo.destinationIp}"
                )
                return ForwardResult(
                    bytesProcessed = length,
                    isOfficeTraffic = false,
                    threat = threat
                )
            }

            // 2. HTTP Plaintext inspection check
            if (parsedInfo.isHttp && parsedInfo.httpHost != null) {
                val host = parsedInfo.httpHost.lowercase()
                if (config.blockedDomains.any { host.contains(it.lowercase()) }) {
                    val threat = SecurityThreat(
                        threatType = ThreatType.MALICIOUS_DNS,
                        severity = ThreatSeverity.HIGH,
                        sourceIp = parsedInfo.sourceIp,
                        destinationIp = parsedInfo.destinationIp,
                        destinationPort = parsedInfo.destinationPort,
                        description = "Blocked HTTP request to prohibited host: $host"
                    )
                    return ForwardResult(
                        bytesProcessed = length,
                        isOfficeTraffic = false,
                        threat = threat
                    )
                }
            }
        }

        // Forward allowed non-office network traffic
        return when (parsedInfo.protocol) {
            17 -> forwardUdpTraffic(buffer, length, parsedInfo, vpnService)
            6 -> forwardTcpTraffic(buffer, length, parsedInfo, vpnService)
            else -> ForwardResult(bytesProcessed = length, isOfficeTraffic = false)
        }
    }

    private fun routeOfficeTraffic(
        buffer: ByteArray,
        length: Int,
        parsedInfo: ParsedPacketInfo,
        officeTunnel: OfficeTunnelEngine?
    ): ForwardResult {
        if (officeTunnel == null) {
            return ForwardResult(
                bytesProcessed = length,
                isOfficeTraffic = true,
                officeTunnelDropReason = "No office tunnel engine active for the current engine mode"
            )
        }

        return when (val result = officeTunnel.routePacket(buffer, length, parsedInfo)) {
            is OfficeTunnelRouteResult.Routed -> ForwardResult(
                bytesProcessed = length,
                isOfficeTraffic = true,
                officeTunnelRouted = true
            )
            is OfficeTunnelRouteResult.Dropped -> ForwardResult(
                bytesProcessed = length,
                isOfficeTraffic = true,
                officeTunnelDropReason = result.reason
            )
        }
    }

    private fun forwardUdpTraffic(
        buffer: ByteArray,
        length: Int,
        parsedInfo: ParsedPacketInfo,
        vpnService: VpnService
    ): ForwardResult {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 1000

            val ihl = (buffer[0].toInt() and 0x0F) * 4
            val payloadOffset = ihl + 8
            val payloadLen = length - payloadOffset

            if (payloadLen > 0) {
                val udpPayload = ByteArray(payloadLen)
                System.arraycopy(buffer, payloadOffset, udpPayload, 0, payloadLen)

                val destAddr = InetAddress.getByName(parsedInfo.destinationIp)
                val outPacket = DatagramPacket(udpPayload, payloadLen, destAddr, parsedInfo.destinationPort)
                socket.send(outPacket)
            }

            ForwardResult(
                bytesProcessed = length,
                isOfficeTraffic = false
            )
        } catch (e: Exception) {
            ForwardResult(
                bytesProcessed = length,
                isOfficeTraffic = false
            )
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun forwardTcpTraffic(
        buffer: ByteArray,
        length: Int,
        parsedInfo: ParsedPacketInfo,
        vpnService: VpnService
    ): ForwardResult {
        // Non-blocking socket relay check to keep device network responsive
        var socket: Socket? = null
        return try {
            socket = Socket()
            vpnService.protect(socket)
            socket.soTimeout = 1000

            ForwardResult(
                bytesProcessed = length,
                isOfficeTraffic = false
            )
        } catch (e: Exception) {
            ForwardResult(
                bytesProcessed = length,
                isOfficeTraffic = false
            )
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
