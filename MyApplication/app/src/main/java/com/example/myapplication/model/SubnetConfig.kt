package com.example.myapplication.model

data class SubnetConfig(
    val address: String,
    val prefixLength: Int
) {
    /**
     * Checks if a given IPv4 string (e.g. "10.1.2.3") falls within this subnet.
     */
    fun contains(ipAddress: String): Boolean {
        return try {
            val targetIp = ipToInt(ipAddress)
            val subnetIp = ipToInt(address)
            val mask = if (prefixLength == 0) 0 else (0xFFFFFFFF.toInt() shl (32 - prefixLength))
            (targetIp and mask) == (subnetIp and mask)
        } catch (e: Exception) {
            false
        }
    }

    private fun ipToInt(ip: String): Int {
        val parts = ip.split(".")
        require(parts.size == 4) { "Invalid IP address: $ip" }
        return (parts[0].toInt() shl 24) or
                (parts[1].toInt() shl 16) or
                (parts[2].toInt() shl 8) or
                parts[3].toInt()
    }

    override fun toString(): String = "$address/$prefixLength"
}
