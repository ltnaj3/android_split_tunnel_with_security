package com.example.myapplication

import com.example.myapplication.model.LogLevel
import com.example.myapplication.model.SubnetConfig
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnState
import com.example.myapplication.repository.VpnRepository
import com.example.myapplication.ui.viewmodel.VpnViewModel
import com.example.myapplication.vpn.VpnConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: VpnRepository
    private lateinit var viewModel: VpnViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = VpnRepository.getInstance()
        repository.updateConfig(VpnConfig())
        repository.updateState(VpnState.Disconnected)
        repository.clearLogs()
        repository.clearThreats()
        repository.resetMetrics()

        viewModel = VpnViewModel(
            vpnConnectionManager = VpnConnectionManager.getInstance(),
            repository = repository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest {
        val state = viewModel.uiState.value
        assertEquals(VpnState.Disconnected, state.vpnState)
        assertEquals("192.168.1.1", state.vpnConfig.officeGatewayIp)
        assertEquals(0, state.selectedTab)
        assertFalse(state.isSimulatingTraffic)
    }

    @Test
    fun testTabSelection() = runTest {
        viewModel.setSelectedTab(2)
        assertEquals(2, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun testGatewayIpUpdate() = runTest {
        viewModel.updateOfficeGatewayIp("10.10.10.1")
        assertEquals("10.10.10.1", viewModel.uiState.value.vpnConfig.officeGatewayIp)
    }

    @Test
    fun testCredentialsUpdate() = runTest {
        viewModel.updateUsername("admin_user")
        viewModel.updatePreSharedKey("new_secret_psk")
        viewModel.updateIkev2Identity("corporate.vpn.org")

        val config = viewModel.uiState.value.vpnConfig
        assertEquals("admin_user", config.username)
        assertEquals("new_secret_psk", config.preSharedKey)
        assertEquals("corporate.vpn.org", config.ikev2Identity)
    }

    @Test
    fun testOfficeSubnetManagement() = runTest {
        val added = viewModel.addOfficeSubnet("172.16.0.0", 12)
        assertTrue(added)
        assertTrue(viewModel.uiState.value.vpnConfig.officeSubnets.contains(SubnetConfig("172.16.0.0", 12)))

        viewModel.removeOfficeSubnet(SubnetConfig("172.16.0.0", 12))
        assertFalse(viewModel.uiState.value.vpnConfig.officeSubnets.contains(SubnetConfig("172.16.0.0", 12)))
    }

    @Test
    fun testBlockedDomainsManagement() = runTest {
        viewModel.addBlockedDomain("malware-site.test")
        assertTrue(viewModel.uiState.value.vpnConfig.blockedDomains.contains("malware-site.test"))

        viewModel.removeBlockedDomain("malware-site.test")
        assertFalse(viewModel.uiState.value.vpnConfig.blockedDomains.contains("malware-site.test"))
    }

    @Test
    fun testInspectionRulesToggle() = runTest {
        val ruleId = "rule_dns_blocklist"
        val initialStatus = viewModel.uiState.value.vpnConfig.inspectionRules.first { it.id == ruleId }.isEnabled

        viewModel.toggleInspectionRule(ruleId)
        val toggledStatus = viewModel.uiState.value.vpnConfig.inspectionRules.first { it.id == ruleId }.isEnabled
        assertEquals(!initialStatus, toggledStatus)
    }

    @Test
    fun testLogFiltering() = runTest {
        repository.addLog(LogLevel.INFO, "Connected to gateway")
        repository.addLog(LogLevel.ERROR, "Connection timed out")

        viewModel.setLogLevelFilter(LogLevel.ERROR)
        val filtered = viewModel.uiState.value.filteredLogs
        assertEquals(1, filtered.size)
        assertEquals(LogLevel.ERROR, filtered.first().level)

        viewModel.setSearchQuery("timed out")
        assertEquals(1, viewModel.uiState.value.filteredLogs.size)

        viewModel.setSearchQuery("nonexistent")
        assertEquals(0, viewModel.uiState.value.filteredLogs.size)
    }
}
