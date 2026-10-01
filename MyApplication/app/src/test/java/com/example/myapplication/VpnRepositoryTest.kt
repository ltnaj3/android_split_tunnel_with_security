package com.example.myapplication

import com.example.myapplication.model.SecurityThreat
import com.example.myapplication.model.ThreatSeverity
import com.example.myapplication.model.ThreatType
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnState
import com.example.myapplication.repository.VpnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnRepositoryTest {

    private lateinit var repository: VpnRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = VpnRepository.getInstance()
        repository.resetMetrics()
        repository.clearLogs()
        repository.clearThreats()
        repository.updateState(VpnState.Disconnected)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStateTransition() {
        assertEquals(VpnState.Disconnected, repository.vpnState.value)

        repository.updateState(VpnState.Connecting)
        assertEquals(VpnState.Connecting, repository.vpnState.value)

        repository.updateState(VpnState.Connected)
        assertEquals(VpnState.Connected, repository.vpnState.value)

        assertTrue(repository.logEvents.value.isNotEmpty())
    }

    @Test
    fun testRecordOfficeTraffic() {
        repository.recordOfficeTraffic(100L, 200L, 2L)
        val metrics = repository.vpnMetrics.value

        assertEquals(100L, metrics.officeBytesSent)
        assertEquals(200L, metrics.officeBytesReceived)
        assertEquals(2L, metrics.officePacketsCount)
        assertEquals(300L, metrics.totalOfficeBytes)
    }

    @Test
    fun testRecordThreat() {
        val threat = SecurityThreat(
            threatType = ThreatType.MALICIOUS_DNS,
            severity = ThreatSeverity.CRITICAL,
            sourceIp = "10.8.0.2",
            destinationIp = "8.8.8.8",
            description = "Test malicious domain query"
        )

        repository.recordThreat(threat)

        assertEquals(1, repository.threatEvents.value.size)
        assertEquals(1L, repository.vpnMetrics.value.detectedSecurityThreats)
        assertEquals(1L, repository.vpnMetrics.value.blockedPacketsCount)
        assertEquals("Test malicious domain query", repository.vpnMetrics.value.lastThreatDescription)
    }

    @Test
    fun testUpdateConfig() {
        val newConfig = VpnConfig(officeGatewayIp = "10.0.0.254")
        repository.updateConfig(newConfig)

        assertEquals("10.0.0.254", repository.vpnConfig.value.officeGatewayIp)
    }
}
