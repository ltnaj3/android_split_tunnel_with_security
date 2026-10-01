package com.example.myapplication.model

enum class VpnEngineMode(
    val displayName: String,
    val description: String
) {
    NATIVE_IKEV2_IPSEC(
        "Native IKEv2 / IPSec",
        "Android OS native IKEv2/IPSec hardware-accelerated tunnel engine"
    ),
    LOCAL_SECURITY_ENGINE(
        "Local Security Engine",
        "TUN packet filter, real-time DNS proxy, IP blocklist & inspection relay"
    ),
    HYBRID_SPLIT_TUNNEL(
        "Hybrid Split-Tunnel",
        "Office subnets routed via IPSec gateway + Internet traffic filtered locally"
    )
}

enum class Ikev2AuthType(
    val displayName: String
) {
    PSK("Pre-Shared Key (PSK)"),
    USERNAME_PASSWORD("Username & Password (EAP-MSCHAPv2)"),
    RSA_CERTIFICATE("RSA Digital Signature / Certificate")
}

data class VpnConfig(
    val engineMode: VpnEngineMode = VpnEngineMode.HYBRID_SPLIT_TUNNEL,
    val authType: Ikev2AuthType = Ikev2AuthType.PSK,
    val officeGatewayIp: String = "192.168.1.1",
    val officeSubnets: List<SubnetConfig> = listOf(
        SubnetConfig("10.0.0.0", 8),
        SubnetConfig("192.168.1.0", 24)
    ),
    val username: String = "office_user",
    val password: String = "secret_password_123",
    val preSharedKey: String = "secret_ikev2_psk_key",
    val ikev2Identity: String = "office.corp.internal",
    val serverIdentifier: String = "office.corp.internal",
    val userCertificateAlias: String = "",
    val serverCaCertificateAlias: String = "",
    val localTunAddress: String = "10.8.0.2",
    val localTunPrefixLength: Int = 24,
    val dnsServers: List<String> = listOf("8.8.8.8", "1.1.1.1"),
    val upstreamDns: String = "8.8.8.8",
    val blockedDomains: Set<String> = setOf(
        "malware.test",
        "phishing.example.com",
        "ads.tracker.net",
        "bad-domain.com"
    ),
    val blockedIps: Set<String> = setOf(
        "192.168.1.100",
        "203.0.113.50",
        "198.51.100.99"
    ),
    val inspectionRules: List<InspectionRule> = listOf(
        InspectionRule(
            id = "rule_dns_blocklist",
            name = "DNS Blocklist Inspection",
            description = "Blocks known malicious and tracking DNS queries",
            ruleType = InspectionRuleType.DNS_BLOCKLIST,
            isEnabled = true
        ),
        InspectionRule(
            id = "rule_ip_blocklist",
            name = "IP Blacklist Inspection",
            description = "Filters traffic destined for suspicious remote IPs",
            ruleType = InspectionRuleType.IP_BLOCKLIST,
            isEnabled = true
        ),
        InspectionRule(
            id = "rule_http_plaintext",
            name = "HTTP Plaintext Inspection",
            description = "Detects sensitive HTTP unencrypted communications",
            ruleType = InspectionRuleType.HTTP_PLAINTEXT_INSPECTION,
            isEnabled = true
        ),
        InspectionRule(
            id = "rule_suspicious_ports",
            name = "Suspicious Port Detector",
            description = "Inspects connections on non-standard high-risk ports",
            ruleType = InspectionRuleType.SUSPICIOUS_PORT_INSPECTION,
            isEnabled = true
        )
    ),
    val isLocalInspectionEnabled: Boolean = true
)
