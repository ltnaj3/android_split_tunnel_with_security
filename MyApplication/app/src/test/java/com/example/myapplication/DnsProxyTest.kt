package com.example.myapplication

import android.net.VpnService
import android.os.Build
import com.example.myapplication.model.Ikev2AuthType
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnEngineMode
import com.example.myapplication.vpn.DnsProxy
import com.example.myapplication.vpn.Ikev2Manager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProxyTest {

    private fun buildMockIPv4DnsQueryPacket(
        srcIpStr: String,
        dstIpStr: String,
        srcPort: Int,
        dstPort: Int,
        domain: String
    ): ByteArray {
        val domainLabels = domain.split(".")
        val dnsQuestionSize = domainLabels.sumOf { 1 + it.length } + 1 + 2 + 2
        val dnsHeaderSize = 12
        val udpHeaderSize = 8
        val ipHeaderSize = 20
        val totalLen = ipHeaderSize + udpHeaderSize + dnsHeaderSize + dnsQuestionSize

        val packet = ByteArray(totalLen)
        packet[0] = 0x45.toByte() // IPv4, IHL = 5
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[9] = 17.toByte() // UDP

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

        // UDP Header
        packet[20] = ((srcPort shr 8) and 0xFF).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = ((dstPort shr 8) and 0xFF).toByte()
        packet[23] = (dstPort and 0xFF).toByte()

        val udpLen = udpHeaderSize + dnsHeaderSize + dnsQuestionSize
        packet[24] = ((udpLen shr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()

        // DNS Header (Standard Query)
        val dnsOffset = 28
        packet[dnsOffset] = 0x12.toByte() // ID
        packet[dnsOffset + 1] = 0x34.toByte()
        packet[dnsOffset + 2] = 0x01.toByte() // Standard Query
        packet[dnsOffset + 3] = 0x00.toByte()
        packet[dnsOffset + 4] = 0x00.toByte()
        packet[dnsOffset + 5] = 0x01.toByte() // 1 Question

        // Question Domain
        var pos = dnsOffset + 12
        for (label in domainLabels) {
            packet[pos++] = label.length.toByte()
            for (ch in label) {
                packet[pos++] = ch.code.toByte()
            }
        }
        packet[pos++] = 0.toByte() // End of domain labels

        // QTYPE = A (1)
        packet[pos++] = 0.toByte()
        packet[pos++] = 1.toByte()

        // QCLASS = IN (1)
        packet[pos] = 0.toByte()
        packet[pos + 1] = 1.toByte()

        return packet
    }

    @Test
    fun testBlockedDnsQueryProcessing() {
        val config = VpnConfig(
            blockedDomains = setOf("malware.test", "phishing.example.com")
        )
        val dummyVpnService = object : VpnService() {}

        val packet = buildMockIPv4DnsQueryPacket("10.8.0.2", "8.8.8.8", 12345, 53, "malware.test")
        val result = DnsProxy.processDnsQuery(
            rawIpPacket = packet,
            packetLen = packet.size,
            srcIp = "10.8.0.2",
            srcPort = 12345,
            dstIp = "8.8.8.8",
            config = config,
            vpnService = dummyVpnService
        )

        assertNotNull(result)
        assertTrue(result!!.queryLog.isBlocked)
        assertEquals("malware.test", result.queryLog.domain)
        assertEquals("0.0.0.0", result.queryLog.resolvedIp)
        assertNotNull(result.threat)
        assertTrue(result.responsePacket.size > 28)
    }

    @Test
    fun testIkev2ManagerProfileConfiguration() {
        val pskConfig = VpnConfig(
            engineMode = VpnEngineMode.NATIVE_IKEV2_IPSEC,
            authType = Ikev2AuthType.PSK,
            officeGatewayIp = "192.168.1.1",
            preSharedKey = "test_psk_key_123"
        )
        val profilePsk = Ikev2Manager.createIkev2Profile(pskConfig)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertNotNull(profilePsk)
        }

        val eapConfig = VpnConfig(
            engineMode = VpnEngineMode.HYBRID_SPLIT_TUNNEL,
            authType = Ikev2AuthType.USERNAME_PASSWORD,
            officeGatewayIp = "192.168.1.1",
            username = "admin",
            password = "password123"
        )
        val profileEap = Ikev2Manager.createIkev2Profile(eapConfig)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertNotNull(profileEap)
        }
    }
}
