package com.example.myapplication.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnMetrics
import com.example.myapplication.model.VpnState
import com.example.myapplication.repository.VpnRepository
import kotlinx.coroutines.flow.StateFlow

class VpnConnectionManager private constructor(
    private val repository: VpnRepository
) {

    val vpnState: StateFlow<VpnState> = repository.vpnState
    val vpnConfig: StateFlow<VpnConfig> = repository.vpnConfig
    val vpnMetrics: StateFlow<VpnMetrics> = repository.vpnMetrics

    /**
     * Returns an intent to prompt the user for VPN permission if needed, or null if already granted.
     */
    fun checkVpnPermission(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    /**
     * Starts the Office VPN Service.
     */
    fun startVpn(context: Context) {
        val prepareIntent = checkVpnPermission(context)
        if (prepareIntent == null) {
            OfficeVpnService.startVpn(context)
        } else {
            repository.updateState(VpnState.Error("VPN permission required. Prompt user with VpnService.prepare()"))
        }
    }

    /**
     * Stops the Office VPN Service.
     */
    fun stopVpn(context: Context) {
        OfficeVpnService.stopVpn(context)
    }

    /**
     * Updates the VPN configuration.
     */
    fun updateConfig(config: VpnConfig) {
        repository.updateConfig(config)
    }

    companion object {
        @Volatile
        private var INSTANCE: VpnConnectionManager? = null

        fun getInstance(): VpnConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VpnConnectionManager(VpnRepository.getInstance()).also { INSTANCE = it }
            }
        }
    }
}
