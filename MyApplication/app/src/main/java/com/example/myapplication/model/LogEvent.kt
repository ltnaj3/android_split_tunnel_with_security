package com.example.myapplication.model

import java.util.UUID

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    THREAT
}

data class LogEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val message: String,
    val details: String? = null
)
