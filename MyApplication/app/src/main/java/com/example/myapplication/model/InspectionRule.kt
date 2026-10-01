package com.example.myapplication.model

enum class InspectionRuleType {
    DNS_BLOCKLIST,
    IP_BLOCKLIST,
    HTTP_PLAINTEXT_INSPECTION,
    SUSPICIOUS_PORT_INSPECTION
}

data class InspectionRule(
    val id: String,
    val name: String,
    val description: String,
    val ruleType: InspectionRuleType,
    val isEnabled: Boolean = true
)
