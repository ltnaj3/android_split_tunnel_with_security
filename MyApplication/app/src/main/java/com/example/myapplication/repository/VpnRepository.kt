package com.example.myapplication.repository

import com.example.myapplication.model.DnsQueryLog
import com.example.myapplication.model.LogEvent
import com.example.myapplication.model.LogLevel
import com.example.myapplication.model.SecurityThreat
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnMetrics
import com.example.myapplication.model.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VpnRepository private constructor() {

    private val _vpnState = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _vpnConfig = MutableStateFlow(VpnConfig())
    val vpnConfig: StateFlow<VpnConfig> = _vpnConfig.asStateFlow()

    private val _vpnMetrics = MutableStateFlow(VpnMetrics())
    val vpnMetrics: StateFlow<VpnMetrics> = _vpnMetrics.asStateFlow()

    private val _logEvents = MutableStateFlow<List<LogEvent>>(emptyList())
    val logEvents: StateFlow<List<LogEvent>> = _logEvents.asStateFlow()

    private val _threatEvents = MutableStateFlow<List<SecurityThreat>>(emptyList())
    val threatEvents: StateFlow<List<SecurityThreat>> = _threatEvents.asStateFlow()

    private val _dnsQueryLogs = MutableStateFlow<List<DnsQueryLog>>(emptyList())
    val dnsQueryLogs: StateFlow<List<DnsQueryLog>> = _dnsQueryLogs.asStateFlow()

    fun updateState(newState: VpnState) {
        _vpnState.value = newState
        addLog(
            level = when (newState) {
                is VpnState.Error -> LogLevel.ERROR
                else -> LogLevel.INFO
            },
            message = "VPN State changed to: ${newState.name}"
        )
        if (newState is VpnState.Connected) {
            _vpnMetrics.update { it.copy(connectionStartTime = System.currentTimeMillis()) }
        } else if (newState is VpnState.Disconnected) {
            _vpnMetrics.update { it.copy(connectionStartTime = null) }
        }
    }

    fun updateConfig(newConfig: VpnConfig) {
        _vpnConfig.value = newConfig
        addLog(
            LogLevel.INFO,
            "VPN Configuration updated. Gateway: ${newConfig.officeGatewayIp}, Engine: ${newConfig.engineMode.displayName}"
        )
    }

    fun recordOfficeTraffic(bytesSent: Long, bytesReceived: Long, packets: Long = 1) {
        _vpnMetrics.update { current ->
            current.copy(
                officeBytesSent = current.officeBytesSent + bytesSent,
                officeBytesReceived = current.officeBytesReceived + bytesReceived,
                officePacketsCount = current.officePacketsCount + packets
            )
        }
    }

    fun recordInspectedPacket(bytes: Long, isDns: Boolean = false) {
        _vpnMetrics.update { current ->
            current.copy(
                inspectedNonOfficePackets = current.inspectedNonOfficePackets + 1,
                inspectedBytes = current.inspectedBytes + bytes,
                dnsQueriesInspected = if (isDns) current.dnsQueriesInspected + 1 else current.dnsQueriesInspected
            )
        }
    }

    fun recordDnsQuery(queryLog: DnsQueryLog) {
        _dnsQueryLogs.update { current -> (listOf(queryLog) + current).take(200) }
        _vpnMetrics.update { current ->
            current.copy(dnsQueriesInspected = current.dnsQueriesInspected + 1)
        }
        addLog(
            level = if (queryLog.isBlocked) LogLevel.THREAT else LogLevel.INFO,
            message = "DNS [${if (queryLog.isBlocked) "BLOCKED" else "ALLOWED"}]: ${queryLog.domain}",
            details = "Client: ${queryLog.clientIp}, Resolved: ${queryLog.resolvedIp ?: "N/A"}"
        )
    }

    fun recordThreat(threat: SecurityThreat) {
        _threatEvents.update { current -> listOf(threat) + current }
        _vpnMetrics.update { current ->
            current.copy(
                detectedSecurityThreats = current.detectedSecurityThreats + 1,
                blockedPacketsCount = current.blockedPacketsCount + 1,
                lastThreatDescription = threat.description
            )
        }
        addLog(
            level = LogLevel.THREAT,
            message = "THREAT DETECTED [${threat.threatType}]: ${threat.description}",
            details = "Src: ${threat.sourceIp}, Dst: ${threat.destinationIp}:${threat.destinationPort}"
        )
    }

    fun addLog(level: LogLevel, message: String, details: String? = null) {
        val event = LogEvent(level = level, message = message, details = details)
        _logEvents.update { current -> (listOf(event) + current).take(200) }
    }

    fun clearLogs() {
        _logEvents.value = emptyList()
    }

    fun clearThreats() {
        _threatEvents.value = emptyList()
    }

    fun clearDnsLogs() {
        _dnsQueryLogs.value = emptyList()
    }

    fun resetMetrics() {
        _vpnMetrics.value = VpnMetrics()
        _dnsQueryLogs.value = emptyList()
        addLog(LogLevel.INFO, "VPN Metrics and DNS query logs reset.")
    }

    companion object {
        @Volatile
        private var INSTANCE: VpnRepository? = null

        fun getInstance(): VpnRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VpnRepository().also { INSTANCE = it }
            }
        }
    }
}
