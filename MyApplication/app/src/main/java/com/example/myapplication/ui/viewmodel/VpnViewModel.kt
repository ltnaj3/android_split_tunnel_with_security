package com.example.myapplication.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.model.DnsQueryLog
import com.example.myapplication.model.Ikev2AuthType
import com.example.myapplication.model.LogEvent
import com.example.myapplication.model.LogLevel
import com.example.myapplication.model.SecurityThreat
import com.example.myapplication.model.SubnetConfig
import com.example.myapplication.model.ThreatSeverity
import com.example.myapplication.model.ThreatType
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnEngineMode
import com.example.myapplication.model.VpnMetrics
import com.example.myapplication.model.VpnState
import com.example.myapplication.repository.VpnRepository
import com.example.myapplication.ui.utils.FormatUtils
import com.example.myapplication.vpn.VpnConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class VpnViewModel(
    private val vpnConnectionManager: VpnConnectionManager = VpnConnectionManager.getInstance(),
    private val repository: VpnRepository = VpnRepository.getInstance()
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    private val _isSimulatingTraffic = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedLogLevelFilter = MutableStateFlow<LogLevel?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _formattedUptime = MutableStateFlow("--:--")

    private var uptimeJob: Job? = null
    private var simulationJob: Job? = null

    val uiState: StateFlow<VpnUiState> = combine(
        repository.vpnState,
        repository.vpnConfig,
        repository.vpnMetrics,
        repository.threatEvents,
        repository.logEvents,
        repository.dnsQueryLogs,
        _selectedTab,
        _isSimulatingTraffic,
        _formattedUptime,
        _searchQuery,
        _selectedLogLevelFilter,
        _errorMessage
    ) { values ->
        val vpnState = values[0] as VpnState
        val vpnConfig = values[1] as VpnConfig
        val vpnMetrics = values[2] as VpnMetrics
        @Suppress("UNCHECKED_CAST")
        val threatEvents = values[3] as List<SecurityThreat>
        @Suppress("UNCHECKED_CAST")
        val logEvents = values[4] as List<LogEvent>
        @Suppress("UNCHECKED_CAST")
        val dnsQueryLogs = values[5] as List<DnsQueryLog>
        val selectedTab = values[6] as Int
        val isSimulatingTraffic = values[7] as Boolean
        val formattedUptime = values[8] as String
        val searchQuery = values[9] as String
        val selectedLogLevelFilter = values[10] as LogLevel?
        val errorMessage = values[11] as String?

        VpnUiState(
            vpnState = vpnState,
            vpnConfig = vpnConfig,
            vpnMetrics = vpnMetrics,
            threatEvents = threatEvents,
            logEvents = logEvents,
            dnsQueryLogs = dnsQueryLogs,
            selectedTab = selectedTab,
            isSimulatingTraffic = isSimulatingTraffic,
            formattedUptime = formattedUptime,
            searchQuery = searchQuery,
            selectedLogLevelFilter = selectedLogLevelFilter,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = VpnUiState()
    )

    init {
        // Monitor VPN state for uptime timer
        viewModelScope.launch {
            repository.vpnState.collect { state ->
                when (state) {
                    is VpnState.Connected -> {
                        startUptimeTimer()
                    }
                    else -> {
                        stopUptimeTimer()
                        _formattedUptime.value = "--:--"
                    }
                }
            }
        }
    }

    private fun startUptimeTimer() {
        uptimeJob?.cancel()
        uptimeJob = viewModelScope.launch {
            while (isActive) {
                val startTime = repository.vpnMetrics.value.connectionStartTime
                if (startTime != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    _formattedUptime.value = FormatUtils.formatDuration(elapsed)
                }
                delay(1000)
            }
        }
    }

    private fun stopUptimeTimer() {
        uptimeJob?.cancel()
        uptimeJob = null
    }

    // Actions
    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun toggleVpn(context: Context) {
        when (repository.vpnState.value) {
            is VpnState.Connected, is VpnState.Connecting -> {
                disconnectVpn(context)
            }
            else -> {
                connectVpn(context)
            }
        }
    }

    fun connectVpn(context: Context) {
        _errorMessage.value = null
        val prepareIntent = vpnConnectionManager.checkVpnPermission(context)
        if (prepareIntent == null) {
            vpnConnectionManager.startVpn(context)
        } else {
            _errorMessage.value = "VPN permission required. Requesting permission..."
            repository.addLog(LogLevel.WARN, "VPN permission required by OS")
        }
    }

    fun disconnectVpn(context: Context) {
        vpnConnectionManager.stopVpn(context)
        if (_isSimulatingTraffic.value) {
            toggleTrafficSimulation()
        }
    }

    fun toggleTrafficSimulation() {
        val nextState = !_isSimulatingTraffic.value
        _isSimulatingTraffic.value = nextState
        simulationJob?.cancel()

        if (nextState) {
            repository.addLog(LogLevel.INFO, "Traffic Simulation started")
            simulationJob = viewModelScope.launch {
                val sampleDomains = listOf("malware.test", "phishing.example.com", "ads.tracker.net", "suspicious.site", "api.office.internal", "google.com", "github.com")
                val sampleIps = listOf("203.0.113.50", "198.51.100.99", "10.0.0.15", "192.168.1.50", "142.250.190.46")
                var step = 0

                while (isActive) {
                    delay(1200)
                    step++
                    // Record simulated office traffic
                    val officeSent = Random.nextLong(1200, 8500)
                    val officeRecv = Random.nextLong(2500, 24000)
                    repository.recordOfficeTraffic(bytesSent = officeSent, bytesReceived = officeRecv, packets = Random.nextLong(1, 4))

                    // Record simulated non-office inspected traffic
                    val inspectedBytes = Random.nextLong(800, 4200)
                    val isDns = Random.nextBoolean()
                    repository.recordInspectedPacket(bytes = inspectedBytes, isDns = isDns)

                    // Record simulated DNS query
                    val domain = sampleDomains.random()
                    val targetIp = sampleIps.random()
                    val isBlockedDomain = repository.vpnConfig.value.blockedDomains.any { domain.contains(it) }

                    val dnsLog = DnsQueryLog(
                        clientIp = "10.8.0.2",
                        domain = domain,
                        queryType = "A",
                        isBlocked = isBlockedDomain,
                        resolvedIp = if (isBlockedDomain) "0.0.0.0" else targetIp,
                        blockReason = if (isBlockedDomain) "Matched threat domain blocklist" else null
                    )
                    repository.recordDnsQuery(dnsLog)

                    // Trigger occasional threat detection
                    if (step % 5 == 0) {
                        val threatType = ThreatType.entries.random()
                        val severity = ThreatSeverity.entries.random()
                        val threat = SecurityThreat(
                            threatType = threatType,
                            severity = severity,
                            sourceIp = "10.8.0.2",
                            destinationIp = targetIp,
                            destinationPort = listOf(80, 443, 53, 8080, 22).random(),
                            description = "Simulated security alert: Blocked $threatType request to $domain ($targetIp)",
                            rawPacketSnippet = "SIMULATED PACKET DATA [Type: $threatType, Domain: $domain, Target: $targetIp]"
                        )
                        repository.recordThreat(threat)
                    }
                }
            }
        } else {
            repository.addLog(LogLevel.INFO, "Traffic Simulation stopped")
        }
    }

    fun updateConfig(config: VpnConfig) {
        vpnConnectionManager.updateConfig(config)
    }

    fun updateEngineMode(engineMode: VpnEngineMode) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(engineMode = engineMode))
    }

    fun updateAuthType(authType: Ikev2AuthType) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(authType = authType))
    }

    fun updateOfficeGatewayIp(ip: String) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(officeGatewayIp = ip))
    }

    fun updateUsername(username: String) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(username = username))
    }

    fun updatePassword(password: String) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(password = password))
    }

    fun updatePreSharedKey(psk: String) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(preSharedKey = psk))
    }

    fun updateIkev2Identity(identity: String) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(ikev2Identity = identity))
    }

    fun updateServerIdentifier(identifier: String) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(serverIdentifier = identifier))
    }

    fun updateUpstreamDns(dns: String) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(upstreamDns = dns))
    }

    fun addOfficeSubnet(address: String, prefixLength: Int): Boolean {
        return try {
            val subnet = SubnetConfig(address.trim(), prefixLength)
            val current = repository.vpnConfig.value
            if (!current.officeSubnets.contains(subnet)) {
                val updatedSubnets = current.officeSubnets + subnet
                updateConfig(current.copy(officeSubnets = updatedSubnets))
                true
            } else false
        } catch (e: Exception) {
            _errorMessage.value = "Invalid Subnet format: ${e.message}"
            false
        }
    }

    fun removeOfficeSubnet(subnet: SubnetConfig) {
        val current = repository.vpnConfig.value
        val updatedSubnets = current.officeSubnets.filter { it != subnet }
        updateConfig(current.copy(officeSubnets = updatedSubnets))
    }

    fun addBlockedDomain(domain: String) {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isNotBlank()) {
            val current = repository.vpnConfig.value
            val updated = current.blockedDomains + cleanDomain
            updateConfig(current.copy(blockedDomains = updated))
        }
    }

    fun removeBlockedDomain(domain: String) {
        val current = repository.vpnConfig.value
        val updated = current.blockedDomains - domain
        updateConfig(current.copy(blockedDomains = updated))
    }

    fun addBlockedIp(ip: String) {
        val cleanIp = ip.trim()
        if (cleanIp.isNotBlank()) {
            val current = repository.vpnConfig.value
            val updated = current.blockedIps + cleanIp
            updateConfig(current.copy(blockedIps = updated))
        }
    }

    fun removeBlockedIp(ip: String) {
        val current = repository.vpnConfig.value
        val updated = current.blockedIps - ip
        updateConfig(current.copy(blockedIps = updated))
    }

    fun toggleInspectionRule(ruleId: String) {
        val current = repository.vpnConfig.value
        val updatedRules = current.inspectionRules.map { rule ->
            if (rule.id == ruleId) {
                rule.copy(isEnabled = !rule.isEnabled)
            } else {
                rule
            }
        }
        updateConfig(current.copy(inspectionRules = updatedRules))
    }

    fun toggleLocalInspection(enabled: Boolean) {
        val current = repository.vpnConfig.value
        updateConfig(current.copy(isLocalInspectionEnabled = enabled))
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLogLevelFilter(level: LogLevel?) {
        _selectedLogLevelFilter.value = level
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    fun clearThreats() {
        repository.clearThreats()
    }

    fun clearDnsLogs() {
        repository.clearDnsLogs()
    }

    fun resetMetrics() {
        repository.resetMetrics()
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        stopUptimeTimer()
        simulationJob?.cancel()
        super.onCleared()
    }
}
