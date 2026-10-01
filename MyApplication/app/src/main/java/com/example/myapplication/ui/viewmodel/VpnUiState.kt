package com.example.myapplication.ui.viewmodel

import com.example.myapplication.model.DnsQueryLog
import com.example.myapplication.model.LogEvent
import com.example.myapplication.model.LogLevel
import com.example.myapplication.model.OfficeTunnelState
import com.example.myapplication.model.SecurityThreat
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnMetrics
import com.example.myapplication.model.VpnState

data class VpnUiState(
    val vpnState: VpnState = VpnState.Disconnected,
    val vpnConfig: VpnConfig = VpnConfig(),
    val vpnMetrics: VpnMetrics = VpnMetrics(),
    val officeTunnelState: OfficeTunnelState = OfficeTunnelState.Idle,
    val threatEvents: List<SecurityThreat> = emptyList(),
    val logEvents: List<LogEvent> = emptyList(),
    val dnsQueryLogs: List<DnsQueryLog> = emptyList(),
    val selectedTab: Int = 0,
    val isSimulatingTraffic: Boolean = false,
    val formattedUptime: String = "--:--",
    val searchQuery: String = "",
    val selectedLogLevelFilter: LogLevel? = null,
    val errorMessage: String? = null
) {
    val filteredLogs: List<LogEvent>
        get() = logEvents.filter { event ->
            val matchesLevel = selectedLogLevelFilter == null || event.level == selectedLogLevelFilter
            val matchesSearch = searchQuery.isBlank() ||
                    event.message.contains(searchQuery, ignoreCase = true) ||
                    (event.details?.contains(searchQuery, ignoreCase = true) == true)
            matchesLevel && matchesSearch
        }

    val filteredDnsLogs: List<DnsQueryLog>
        get() = dnsQueryLogs.filter { log ->
            searchQuery.isBlank() ||
                    log.domain.contains(searchQuery, ignoreCase = true) ||
                    log.clientIp.contains(searchQuery, ignoreCase = true) ||
                    (log.resolvedIp?.contains(searchQuery, ignoreCase = true) == true)
        }
}
