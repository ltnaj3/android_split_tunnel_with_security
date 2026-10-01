package com.example.myapplication

import com.example.myapplication.model.SubnetConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetConfigTest {

    @Test
    fun testSubnetContains_classA() {
        val subnet = SubnetConfig("10.0.0.0", 8)

        assertTrue(subnet.contains("10.0.0.1"))
        assertTrue(subnet.contains("10.255.255.254"))
        assertFalse(subnet.contains("11.0.0.1"))
        assertFalse(subnet.contains("192.168.1.1"))
    }

    @Test
    fun testSubnetContains_classC() {
        val subnet = SubnetConfig("192.168.1.0", 24)

        assertTrue(subnet.contains("192.168.1.1"))
        assertTrue(subnet.contains("192.168.1.254"))
        assertFalse(subnet.contains("192.168.2.1"))
        assertFalse(subnet.contains("10.0.0.1"))
    }

    @Test
    fun testSubnetContains_invalidIp() {
        val subnet = SubnetConfig("10.0.0.0", 8)
        assertFalse(subnet.contains("invalid.ip"))
    }
}
