package com.example.myapplication.model

data class VpnMetrics(
    val officeBytesSent: Long = 0L,
    val officeBytesReceived: Long = 0L,
    val officePacketsCount: Long = 0L,
    val inspectedNonOfficePackets: Long = 0L,
    val inspectedBytes: Long = 0L,
    val detectedSecurityThreats: Long = 0L,
    val dnsQueriesInspected: Long = 0L,
    val blockedPacketsCount: Long = 0L,
    val lastThreatDescription: String? = null,
    val connectionStartTime: Long? = null
) {
    val totalOfficeBytes: Long get() = officeBytesSent + officeBytesReceived
    val totalProcessedBytes: Long get() = totalOfficeBytes + inspectedBytes
    val totalPackets: Long get() = officePacketsCount + inspectedNonOfficePackets
}
