package com.example.myapplication.model

import java.util.UUID

data class DnsQueryLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val domain: String,
    val queryType: String = "A",
    val isBlocked: Boolean,
    val resolvedIp: String? = null,
    val blockReason: String? = null
)
