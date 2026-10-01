package com.example.myapplication.vpn

import android.net.VpnService
import com.example.myapplication.model.DnsQueryLog
import com.example.myapplication.model.SecurityThreat
import com.example.myapplication.model.ThreatSeverity
import com.example.myapplication.model.ThreatType
import com.example.myapplication.model.VpnConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

data class DnsProxyResult(
    val responsePacket: ByteArray,
    val threat: SecurityThreat?,
    val queryLog: DnsQueryLog
)

object DnsProxy {

    /**
     * Intercepts, inspects, and forwards or blocks UDP DNS queries (Destination Port 53).
     * Protects upstream sockets using VpnService.protect(socket).
     */
    fun processDnsQuery(
        rawIpPacket: ByteArray,
        packetLen: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        config: VpnConfig,
        vpnService: VpnService
    ): DnsProxyResult? {
        val ihl = (rawIpPacket[0].toInt() and 0x0F) * 4
        if (packetLen < ihl + 8 + 12) return null // IP header + UDP header + minimum DNS header (12 bytes)

        val udpPayloadOffset = ihl + 8
        val udpPayloadLen = packetLen - udpPayloadOffset
        if (udpPayloadLen < 12) return null

        val dnsQueryPayload = ByteArray(udpPayloadLen)
        System.arraycopy(rawIpPacket, udpPayloadOffset, dnsQueryPayload, 0, udpPayloadLen)

        val (domain, queryType) = parseDnsQuestion(dnsQueryPayload)
        val cleanDomain = domain?.lowercase() ?: "unknown"

        val isBlocked = config.isLocalInspectionEnabled && config.blockedDomains.any {
            cleanDomain.contains(it.lowercase())
        }

        if (isBlocked) {
            val syntheticDnsResponse = buildSyntheticBlockedDnsResponse(dnsQueryPayload)
            val responseIpPacket = buildIPv4UdpPacket(
                srcIpStr = dstIp,
                dstIpStr = srcIp,
                srcPort = 53,
                dstPort = srcPort,
                payload = syntheticDnsResponse
            )

            val threat = SecurityThreat(
                threatType = ThreatType.MALICIOUS_DNS,
                severity = ThreatSeverity.HIGH,
                sourceIp = srcIp,
                destinationIp = dstIp,
                destinationPort = 53,
                description = "Blocked DNS query for threat domain: $cleanDomain",
                rawPacketSnippet = "DNS Domain: $cleanDomain, QueryType: $queryType"
            )

            val queryLog = DnsQueryLog(
                clientIp = srcIp,
                domain = cleanDomain,
                queryType = queryType,
                isBlocked = true,
                resolvedIp = "0.0.0.0",
                blockReason = "Matched blocked domain filter"
            )

            return DnsProxyResult(
                responsePacket = responseIpPacket,
                threat = threat,
                queryLog = queryLog
            )
        }

        // Allowed DNS query - Forward to Upstream DNS server
        val upstreamServerIp = if (config.upstreamDns.isNotBlank()) config.upstreamDns else config.dnsServers.firstOrNull() ?: "8.8.8.8"
        val upstreamResponsePayload = forwardDnsToUpstream(dnsQueryPayload, upstreamServerIp, vpnService)

        val responsePayload = upstreamResponsePayload ?: buildSyntheticBlockedDnsResponse(dnsQueryPayload)
        val resolvedIp = if (upstreamResponsePayload != null) parseFirstResolvedIp(upstreamResponsePayload) else null

        val responseIpPacket = buildIPv4UdpPacket(
            srcIpStr = dstIp,
            dstIpStr = srcIp,
            srcPort = 53,
            dstPort = srcPort,
            payload = responsePayload
        )

        val queryLog = DnsQueryLog(
            clientIp = srcIp,
            domain = cleanDomain,
            queryType = queryType,
            isBlocked = false,
            resolvedIp = resolvedIp ?: "Resolved via $upstreamServerIp",
            blockReason = null
        )

        return DnsProxyResult(
            responsePacket = responseIpPacket,
            threat = null,
            queryLog = queryLog
        )
    }

    private fun forwardDnsToUpstream(
        dnsQueryPayload: ByteArray,
        upstreamIp: String,
        vpnService: VpnService
    ): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 2500

            val serverAddr = InetAddress.getByName(upstreamIp)
            val outPacket = DatagramPacket(dnsQueryPayload, dnsQueryPayload.size, serverAddr, 53)
            socket.send(outPacket)

            val recvBuffer = ByteArray(2048)
            val inPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            socket.receive(inPacket)

            val responseBytes = ByteArray(inPacket.length)
            System.arraycopy(recvBuffer, 0, responseBytes, 0, inPacket.length)
            responseBytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun parseDnsQuestion(dnsPayload: ByteArray): Pair<String?, String> {
        return try {
            var pos = 12
            val sb = StringBuilder()
            while (pos < dnsPayload.size) {
                val len = dnsPayload[pos].toInt() and 0xFF
                if (len == 0) {
                    pos++
                    break
                }
                if (pos + 1 + len > dnsPayload.size) break
                if (sb.isNotEmpty()) sb.append(".")
                val label = String(dnsPayload, pos + 1, len, StandardCharsets.US_ASCII)
                sb.append(label)
                pos += 1 + len
            }

            var typeStr = "A"
            if (pos + 2 <= dnsPayload.size) {
                val qType = ((dnsPayload[pos].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 1].toInt() and 0xFF)
                typeStr = when (qType) {
                    1 -> "A"
                    28 -> "AAAA"
                    15 -> "MX"
                    16 -> "TXT"
                    5 -> "CNAME"
                    else -> "TYPE_$qType"
                }
            }

            Pair(if (sb.isNotEmpty()) sb.toString() else null, typeStr)
        } catch (e: Exception) {
            Pair(null, "A")
        }
    }

    private fun buildSyntheticBlockedDnsResponse(queryPayload: ByteArray): ByteArray {
        val response = queryPayload.copyOf()
        if (response.size >= 12) {
            // Flags: Response, Opcode Standard, Authoritative, Recursion Available, No Error (0x8180)
            response[2] = 0x81.toByte()
            response[3] = 0x80.toByte()

            // Answer Count = 1 (0x0001)
            response[6] = 0x00.toByte()
            response[7] = 0x01.toByte()

            // Find end of Question section
            var pos = 12
            while (pos < response.size) {
                val len = response[pos].toInt() and 0xFF
                if (len == 0) {
                    pos += 5 // Skip null byte + 2 bytes QTYPE + 2 bytes QCLASS
                    break
                }
                pos += 1 + len
            }

            val answerRecord = byteArrayOf(
                0xC0.toByte(), 0x0C.toByte(), // Name pointer to offset 12
                0x00.toByte(), 0x01.toByte(), // TYPE = A
                0x00.toByte(), 0x01.toByte(), // CLASS = IN
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3C.toByte(), // TTL = 60s
                0x00.toByte(), 0x04.toByte(), // RDLENGTH = 4 bytes
                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // RDATA = 0.0.0.0
            )

            val fullResponse = ByteArray(pos + answerRecord.size)
            System.arraycopy(response, 0, fullResponse, 0, minOf(pos, response.size))
            System.arraycopy(answerRecord, 0, fullResponse, pos, answerRecord.size)
            return fullResponse
        }
        return response
    }

    private fun parseFirstResolvedIp(dnsPayload: ByteArray): String? {
        return try {
            if (dnsPayload.size < 12) return null
            val anCount = ((dnsPayload[6].toInt() and 0xFF) shl 8) or (dnsPayload[7].toInt() and 0xFF)
            if (anCount == 0) return null

            var pos = 12
            while (pos < dnsPayload.size) {
                val len = dnsPayload[pos].toInt() and 0xFF
                if (len == 0) {
                    pos += 5
                    break
                }
                pos += 1 + len
            }

            if (pos + 12 <= dnsPayload.size) {
                val type = ((dnsPayload[pos + 2].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 3].toInt() and 0xFF)
                val rdLength = ((dnsPayload[pos + 10].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 11].toInt() and 0xFF)
                val rdOffset = pos + 12

                if (type == 1 && rdLength == 4 && rdOffset + 4 <= dnsPayload.size) {
                    return "${dnsPayload[rdOffset].toInt() and 0xFF}.${dnsPayload[rdOffset + 1].toInt() and 0xFF}.${dnsPayload[rdOffset + 2].toInt() and 0xFF}.${dnsPayload[rdOffset + 3].toInt() and 0xFF}"
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun buildIPv4UdpPacket(
        srcIpStr: String,
        dstIpStr: String,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ihl = 20
        val udpHeaderLen = 8
        val totalLen = ihl + udpHeaderLen + payload.size
        val packet = ByteArray(totalLen)

        // Version = 4, IHL = 5 (20 bytes)
        packet[0] = 0x45.toByte()
        packet[1] = 0x00.toByte() // TOS / DSCP
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()

        // Identification
        packet[4] = 0x12.toByte()
        packet[5] = 0x34.toByte()

        // Flags & Fragment Offset (Don't Fragment = 0x4000)
        packet[6] = 0x40.toByte()
        packet[7] = 0x00.toByte()

        // TTL = 64, Protocol = 17 (UDP)
        packet[8] = 64.toByte()
        packet[9] = 17.toByte()

        // Source & Destination IP
        val srcParts = srcIpStr.split(".").map { it.toInt().toByte() }
        val dstParts = dstIpStr.split(".").map { it.toInt().toByte() }

        packet[12] = srcParts[0]
        packet[13] = srcParts[1]
        packet[14] = srcParts[2]
        packet[15] = srcParts[3]

        packet[16] = dstParts[0]
        packet[17] = dstParts[1]
        packet[18] = dstParts[2]
        packet[19] = dstParts[3]

        // Calculate IPv4 Checksum
        val ipChecksum = calculateChecksum(packet, 0, ihl)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header
        packet[20] = ((srcPort shr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort shr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()

        val udpLen = udpHeaderLen + payload.size
        packet[24] = ((udpLen shr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()

        // UDP Checksum (0x0000 = disabled in IPv4)
        packet[26] = 0x00.toByte()
        packet[27] = 0x00.toByte()

        // Payload
        System.arraycopy(payload, 0, packet, 28, payload.size)

        return packet
    }

    private fun calculateChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length

        // Set checksum field to zero during calculation
        buffer[offset + 10] = 0
        buffer[offset + 11] = 0

        while (i < end - 1) {
            val word = ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (buffer[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }
}
