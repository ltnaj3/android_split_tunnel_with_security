package com.example.myapplication

import com.example.myapplication.model.SubnetConfig
import com.example.myapplication.model.ThreatType
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.vpn.PacketParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketParserTest {

    private fun buildMockIPv4UdpPacket(
        srcIpStr: String,
        dstIpStr: String,
        srcPort: Int,
        dstPort: Int,
        udpPayload: ByteArray
    ): ByteArray {
        val ihl = 20
        val totalLen = ihl + 8 + udpPayload.size
        val packet = ByteArray(totalLen)

        packet[0] = 0x45.toByte() // IPv4, IHL = 5 (20 bytes)
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

        val udpLen = 8 + udpPayload.size
        packet[24] = ((udpLen shr 8) and 0xFF).toByte()
        packet[25] = (udpLen and 0xFF).toByte()

        System.arraycopy(udpPayload, 0, packet, 28, udpPayload.size)
        return packet
    }

    @Test
    fun testOfficeSubnetIdentification() {
        val config = VpnConfig(
            officeSubnets = listOf(
                SubnetConfig("10.0.0.0", 8),
                SubnetConfig("192.168.1.0", 24)
            )
        )

        val officePacket = buildMockIPv4UdpPacket("10.8.0.2", "10.1.2.3", 12345, 80, ByteArray(0))
        val parsedOffice = PacketParser.parseAndInspect(officePacket, officePacket.size, config)

        assertNotNull(parsedOffice)
        assertTrue(parsedOffice!!.isOfficeSubnet)
        assertEquals("10.1.2.3", parsedOffice.destinationIp)

        val nonOfficePacket = buildMockIPv4UdpPacket("10.8.0.2", "8.8.8.8", 12345, 53, ByteArray(0))
        val parsedNonOffice = PacketParser.parseAndInspect(nonOfficePacket, nonOfficePacket.size, config)

        assertNotNull(parsedNonOffice)
        assertTrue(!parsedNonOffice!!.isOfficeSubnet)
    }

    @Test
    fun testBlockedIpThreatDetection() {
        val config = VpnConfig(
            officeSubnets = listOf(SubnetConfig("10.0.0.0", 8)),
            blockedIps = setOf("203.0.113.50")
        )

        val packet = buildMockIPv4UdpPacket("10.8.0.2", "203.0.113.50", 12345, 80, ByteArray(0))
        val parsed = PacketParser.parseAndInspect(packet, packet.size, config)

        assertNotNull(parsed)
        assertNotNull(parsed!!.detectedThreat)
        assertEquals(ThreatType.BLOCKED_IP, parsed.detectedThreat!!.threatType)
    }
}
