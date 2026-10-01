package com.example.myapplication.vpn

import com.example.myapplication.model.SecurityThreat
import com.example.myapplication.model.ThreatSeverity
import com.example.myapplication.model.ThreatType
import com.example.myapplication.model.VpnConfig

data class ParsedPacketInfo(
    val version: Int,
    val protocol: Int, // 6 = TCP, 17 = UDP, 1 = ICMP
    val sourceIp: String,
    val destinationIp: String,
    val sourcePort: Int,
    val destinationPort: Int,
    val totalLength: Int,
    val isOfficeSubnet: Boolean,
    val isDns: Boolean,
    val isHttp: Boolean,
    val dnsQueryDomain: String? = null,
    val httpHost: String? = null,
    val detectedThreat: SecurityThreat? = null
)

object PacketParser {

    /**
     * Parses a raw IPv4 packet buffer and evaluates split tunneling and inspection rules.
     */
    fun parseAndInspect(
        buffer: ByteArray,
        length: Int,
        config: VpnConfig
    ): ParsedPacketInfo? {
        if (length < 20) return null // Minimum IPv4 header length is 20 bytes

        val versionAndIhl = buffer[0].toInt() and 0xFF
        val version = (versionAndIhl and 0xF0) ushr 4
        if (version != 4) return null // Handle IPv4

        val ihl = (versionAndIhl and 0x0F) * 4
        if (length < ihl) return null

        val totalLength = ((buffer[2].toInt() and 0xFF) shl 8) or (buffer[3].toInt() and 0xFF)
        val protocol = buffer[9].toInt() and 0xFF

        val sourceIp = formatIp(buffer, 12)
        val destinationIp = formatIp(buffer, 16)

        var sourcePort = 0
        var destinationPort = 0
        var isDns = false
        var isHttp = false
        var dnsQueryDomain: String? = null
        var httpHost: String? = null

        val payloadOffset = ihl
        val payloadLength = length - payloadOffset

        if (payloadLength > 0) {
            when (protocol) {
                17 -> { // UDP
                    if (payloadLength >= 8) {
                        sourcePort = ((buffer[payloadOffset].toInt() and 0xFF) shl 8) or (buffer[payloadOffset + 1].toInt() and 0xFF)
                        destinationPort = ((buffer[payloadOffset + 2].toInt() and 0xFF) shl 8) or (buffer[payloadOffset + 3].toInt() and 0xFF)
                        if (destinationPort == 53 || sourcePort == 53) {
                            isDns = true
                            val udpPayloadOffset = payloadOffset + 8
                            val udpPayloadLen = payloadLength - 8
                            if (udpPayloadLen > 12) {
                                dnsQueryDomain = parseDnsQuestionDomain(buffer, udpPayloadOffset, udpPayloadLen)
                            }
                        }
                    }
                }
                6 -> { // TCP
                    if (payloadLength >= 20) {
                        sourcePort = ((buffer[payloadOffset].toInt() and 0xFF) shl 8) or (buffer[payloadOffset + 1].toInt() and 0xFF)
                        destinationPort = ((buffer[payloadOffset + 2].toInt() and 0xFF) shl 8) or (buffer[payloadOffset + 3].toInt() and 0xFF)
                        val dataOffset = ((buffer[payloadOffset + 12].toInt() and 0xFF and 0xF0) ushr 4) * 4
                        val tcpPayloadOffset = payloadOffset + dataOffset
                        val tcpPayloadLen = length - tcpPayloadOffset
                        if (destinationPort == 80 && tcpPayloadLen > 0) {
                            isHttp = true
                            httpHost = parseHttpHost(buffer, tcpPayloadOffset, tcpPayloadLen)
                        }
                    }
                }
            }
        }

        // Check if destination matches any configured Office Subnet
        val isOfficeSubnet = config.officeSubnets.any { subnet -> subnet.contains(destinationIp) }

        // Perform local security threat inspection on non-office traffic (or all if enabled)
        var threat: SecurityThreat? = null

        if (config.isLocalInspectionEnabled && !isOfficeSubnet) {
            // 1. IP Blocklist Inspection
            if (config.blockedIps.contains(destinationIp)) {
                threat = SecurityThreat(
                    threatType = ThreatType.BLOCKED_IP,
                    severity = ThreatSeverity.HIGH,
                    sourceIp = sourceIp,
                    destinationIp = destinationIp,
                    destinationPort = destinationPort,
                    description = "Connection attempt to blocked target IP: $destinationIp"
                )
            }

            // 2. DNS Blocklist Inspection
            if (threat == null && isDns && dnsQueryDomain != null) {
                val cleanDomain = dnsQueryDomain.lowercase()
                if (config.blockedDomains.any { cleanDomain.contains(it.lowercase()) }) {
                    threat = SecurityThreat(
                        threatType = ThreatType.MALICIOUS_DNS,
                        severity = ThreatSeverity.CRITICAL,
                        sourceIp = sourceIp,
                        destinationIp = destinationIp,
                        destinationPort = destinationPort,
                        description = "Blocked DNS query for malicious domain: $dnsQueryDomain"
                    )
                }
            }

            // 3. HTTP Plaintext / Unsafe domain Inspection
            if (threat == null && isHttp) {
                val host = httpHost?.lowercase()
                if (host != null && config.blockedDomains.any { host.contains(it.lowercase()) }) {
                    threat = SecurityThreat(
                        threatType = ThreatType.MALICIOUS_DNS,
                        severity = ThreatSeverity.HIGH,
                        sourceIp = sourceIp,
                        destinationIp = destinationIp,
                        destinationPort = destinationPort,
                        description = "Plaintext HTTP request to blocked host: $host"
                    )
                } else {
                    // Plaintext HTTP warning
                    threat = SecurityThreat(
                        threatType = ThreatType.PLAINTEXT_HTTP_ALERT,
                        severity = ThreatSeverity.LOW,
                        sourceIp = sourceIp,
                        destinationIp = destinationIp,
                        destinationPort = destinationPort,
                        description = "Unencrypted HTTP connection detected to $destinationIp (${host ?: "unknown host"})"
                    )
                }
            }

            // 4. Suspicious Port Inspection
            if (threat == null && destinationPort in listOf(23, 135, 139, 445, 1433, 3389)) {
                threat = SecurityThreat(
                    threatType = ThreatType.SUSPICIOUS_PORT,
                    severity = ThreatSeverity.MEDIUM,
                    sourceIp = sourceIp,
                    destinationIp = destinationIp,
                    destinationPort = destinationPort,
                    description = "Connection detected on sensitive/risky service port: $destinationPort"
                )
            }
        }

        return ParsedPacketInfo(
            version = version,
            protocol = protocol,
            sourceIp = sourceIp,
            destinationIp = destinationIp,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            totalLength = totalLength,
            isOfficeSubnet = isOfficeSubnet,
            isDns = isDns,
            isHttp = isHttp,
            dnsQueryDomain = dnsQueryDomain,
            httpHost = httpHost,
            detectedThreat = threat
        )
    }

    private fun formatIp(buffer: ByteArray, offset: Int): String {
        return "${buffer[offset].toInt() and 0xFF}.${buffer[offset + 1].toInt() and 0xFF}.${buffer[offset + 2].toInt() and 0xFF}.${buffer[offset + 3].toInt() and 0xFF}"
    }

    private fun parseDnsQuestionDomain(buffer: ByteArray, offset: Int, length: Int): String? {
        return try {
            // DNS header is 12 bytes. Question starts at offset + 12
            var pos = offset + 12
            val end = offset + length
            val sb = StringBuilder()

            while (pos < end) {
                val len = buffer[pos].toInt() and 0xFF
                if (len == 0) break // End of domain labels
                if (pos + 1 + len > end) break

                if (sb.isNotEmpty()) sb.append(".")
                val label = String(buffer, pos + 1, len, Charsets.US_ASCII)
                sb.append(label)
                pos += 1 + len
            }

            if (sb.isNotEmpty()) sb.toString() else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHttpHost(buffer: ByteArray, offset: Int, length: Int): String? {
        return try {
            val payloadStr = String(buffer, offset, minOf(length, 1024), Charsets.US_ASCII)
            val lines = payloadStr.split("\r\n", "\n")
            for (line in lines) {
                if (line.startsWith("Host:", ignoreCase = true)) {
                    return line.substring(5).trim().split(":")[0]
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
