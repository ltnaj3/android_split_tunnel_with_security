package com.example.myapplication.vpn

import android.content.Context
import android.content.Intent
import android.net.Ikev2VpnProfile
import android.net.PlatformVpnProfile
import android.net.VpnManager
import android.net.VpnService
import android.os.Build
import com.example.myapplication.model.Ikev2AuthType
import com.example.myapplication.model.VpnConfig
import java.nio.charset.StandardCharsets

/**
 * DEPRECATED RUNTIME PATH -- NOT CALLED by [OfficeVpnService] anymore.
 *
 * [VpnManager.provisionVpnProfile] + [VpnManager.startProvisionedVpnProfileSession] hand the
 * *entire device VPN tunnel* to an OS-owned profile. That conflicts with this app's
 * single-owner architecture, where [OfficeVpnService] (an app-owned `VpnService`) is meant to be
 * the one and only active VPN interface so that non-office traffic can still be locally
 * inspected/blocked. Starting a provisioned VpnManager session here would silently create a
 * second, competing VPN owner and would remove visibility into non-office packets entirely.
 *
 * The office IKEv2/IPsec control-plane negotiation now lives in [IkeSessionController] /
 * [IpsecOfficeTunnel], which use the lower-level `android.net.ipsec.ike` library instead and do
 * not take ownership of the TUN interface.
 *
 * This object is retained only for [createIkev2Profile]/[checkVpnPermission], which may still be
 * useful for a possible future "full-tunnel, no local inspection" fallback mode. Its
 * [provisionProfile]/[startProvisionedSession] functions MUST NOT be wired into the active
 * runtime connect path.
 */
@Deprecated("VpnManager-owned profile sessions conflict with the single app-owned VpnService architecture. Use IkeSessionController/IpsecOfficeTunnel instead.")
object Ikev2Manager {

    /**
     * Builds an Ikev2VpnProfile for the given configuration if Android version >= R (API 30).
     * Supports PSK, Username/Password EAP, and Certificate digital signatures.
     */
    fun createIkev2Profile(config: VpnConfig): PlatformVpnProfile? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        return try {
            val serverAddress = if (config.officeGatewayIp.isNotBlank()) config.officeGatewayIp else "127.0.0.1"
            val userIdentity = if (config.ikev2Identity.isNotBlank()) config.ikev2Identity else config.username

            val builder = Ikev2VpnProfile.Builder(
                serverAddress,
                userIdentity
            )

            // Configure Authentication Method
            when (config.authType) {
                Ikev2AuthType.PSK -> {
                    val pskBytes = config.preSharedKey.toByteArray(StandardCharsets.UTF_8)
                    builder.setAuthPsk(pskBytes)
                }
                Ikev2AuthType.USERNAME_PASSWORD -> {
                    builder.setAuthUsernamePassword(
                        config.username,
                        config.password,
                        null // Optional Root CA certificate
                    )
                }
                Ikev2AuthType.RSA_CERTIFICATE -> {
                    // Fallback to PSK if certificate objects are not supplied
                    val pskBytes = config.preSharedKey.toByteArray(StandardCharsets.UTF_8)
                    builder.setAuthPsk(pskBytes)
                }
            }

            builder.setBypassable(true)
            builder.setMetered(false)

            builder.build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Provisions the IKEv2 VPN profile with Android VpnManager if supported (API 30+).
     */
    fun provisionProfile(context: Context, config: VpnConfig): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val vpnManager = context.getSystemService(Context.VPN_MANAGEMENT_SERVICE) as? VpnManager
                val profile = createIkev2Profile(config)
                if (vpnManager != null && profile != null) {
                    vpnManager.provisionVpnProfile(profile)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        return false
    }

    /**
     * Starts provisioned VPN session via VpnManager if available (API 33+).
     */
    fun startProvisionedSession(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return try {
                val vpnManager = context.getSystemService(Context.VPN_MANAGEMENT_SERVICE) as? VpnManager
                vpnManager?.startProvisionedVpnProfileSession()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        return false
    }

    /**
     * Checks if OS VPN permission is granted or returns the preparation intent.
     */
    fun checkVpnPermission(context: Context): Intent? {
        return VpnService.prepare(context)
    }
}
