package com.example.myapplication.model

import java.util.UUID

enum class ThreatType {
    MALICIOUS_DNS,
    BLOCKED_IP,
    PLAINTEXT_HTTP_ALERT,
    SUSPICIOUS_PORT
}

enum class ThreatSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class SecurityThreat(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val threatType: ThreatType,
    val severity: ThreatSeverity,
    val sourceIp: String,
    val destinationIp: String,
    val destinationPort: Int = 0,
    val description: String,
    val rawPacketSnippet: String? = null
)
