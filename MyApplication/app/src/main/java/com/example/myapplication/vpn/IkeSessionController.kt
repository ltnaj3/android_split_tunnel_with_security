package com.example.myapplication.vpn

import android.content.Context
import android.net.InetAddresses
import android.net.IpSecTransform
import android.net.eap.EapSessionConfig
import android.net.ipsec.ike.ChildSaProposal
import android.net.ipsec.ike.ChildSessionCallback
import android.net.ipsec.ike.ChildSessionConfiguration
import android.net.ipsec.ike.IkeFqdnIdentification
import android.net.ipsec.ike.IkeIdentification
import android.net.ipsec.ike.IkeIpv4AddrIdentification
import android.net.ipsec.ike.IkeSaProposal
import android.net.ipsec.ike.IkeSession
import android.net.ipsec.ike.IkeSessionCallback
import android.net.ipsec.ike.IkeSessionConfiguration
import android.net.ipsec.ike.IkeSessionParams
import android.net.ipsec.ike.IkeTrafficSelector
import android.net.ipsec.ike.SaProposal
import android.net.ipsec.ike.TransportModeChildSessionParams
import android.net.ipsec.ike.exceptions.IkeException
import android.net.ipsec.ike.exceptions.IkeProtocolException
import android.os.Build
import com.example.myapplication.model.Ikev2AuthType
import com.example.myapplication.model.SubnetConfig
import com.example.myapplication.model.VpnConfig
import java.net.Inet4Address
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Lifecycle/state events emitted while negotiating the office IKEv2/IPsec control-plane session.
 */
sealed class IkeControlPlaneEvent {
    object Connecting : IkeControlPlaneEvent()
    data class IkeSessionUp(val remoteAddress: String) : IkeControlPlaneEvent()
    object ChildSaUp : IkeControlPlaneEvent()
    data class ChildSaDown(val reason: String) : IkeControlPlaneEvent()
    data class Closed(val reason: String?) : IkeControlPlaneEvent()
    data class Error(val reason: String, val code: Int? = null) : IkeControlPlaneEvent()
}

/**
 * Negotiates an IKEv2 IKE SA + Child SA against the configured office gateway using Android's
 * built-in `android.net.ipsec.ike` control-plane library (public API since Android 12 / API 31).
 *
 * This class deliberately does NOT use `VpnManager`/`Ikev2VpnProfile`
 * ([Ikev2Manager], deprecated) because that API hands the entire device tunnel interface to the
 * OS, which conflicts with this app's single-owner [OfficeVpnService] TUN model. Instead, this
 * controller only performs key management/negotiation; [OfficeVpnService] remains the one and
 * only active VPN interface on the device.
 *
 * KNOWN LIMITATION: Real kernel-assisted tunnel-mode ESP forwarding
 * (`IpSecManager.IpSecTunnelInterface`) requires the system-only `MANAGE_IPSEC_TUNNELS`
 * permission, which is unavailable to this (non-privileged) third-party app. This controller
 * therefore negotiates a genuine Child SA using a *transport mode* proposal scoped to the
 * configured office subnets (proving real IKE/Check-Point interop end-to-end), but does not
 * itself perform in-process ESP packet encapsulation/decapsulation. See [IpsecOfficeTunnel] for
 * how the dataplane gap is explicitly surfaced rather than silently papered over.
 */
class IkeSessionController(
    private val context: Context,
    private val listener: (IkeControlPlaneEvent) -> Unit
) {
    private var ikeSession: IkeSession? = null
    private val executor: Executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var lastChildTransform: IpSecTransform? = null

    val negotiatedChildTransform: IpSecTransform?
        get() = lastChildTransform

    /**
     * Starts IKE negotiation. Safe to call only once per instance; create a new controller for a
     * fresh session.
     */
    fun start(config: VpnConfig) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            listener(
                IkeControlPlaneEvent.Error(
                    "Built-in android.net.ipsec.ike library requires Android 12 (API 31)+; " +
                        "this device is API ${Build.VERSION.SDK_INT}. IKE negotiation skipped."
                )
            )
            return
        }
        if (ikeSession != null) {
            return
        }

        try {
            val ikeParams = buildIkeSessionParams(config)
            val childParams = buildChildSessionParams(config)

            listener(IkeControlPlaneEvent.Connecting)

            ikeSession = IkeSession(
                context,
                ikeParams,
                childParams,
                executor,
                ikeSessionCallback,
                childSessionCallback
            )
        } catch (e: UnsupportedOperationException) {
            listener(IkeControlPlaneEvent.Error(e.message ?: "Unsupported IKE configuration"))
        } catch (e: IllegalArgumentException) {
            listener(IkeControlPlaneEvent.Error("Invalid IKE configuration: ${e.message}"))
        } catch (e: Exception) {
            listener(IkeControlPlaneEvent.Error("Failed to start IKE session: ${e.message}"))
        }
    }

    /** Gracefully tears down the IKE session (and any Child SAs under it), if running. */
    fun stop() {
        try {
            ikeSession?.close()
        } catch (e: Exception) {
            // Best-effort teardown; the session may already be closed/killed.
        } finally {
            ikeSession = null
            lastChildTransform = null
        }
    }

    private fun buildIkeSessionParams(config: VpnConfig): IkeSessionParams {
        val builder = IkeSessionParams.Builder()
            .setServerHostname(config.officeGatewayIp)
            .addIkeSaProposal(defaultIkeSaProposal())
            .setLocalIdentification(identityFor(config.ikev2Identity.ifBlank { config.username }))
            .setRemoteIdentification(identityFor(config.serverIdentifier.ifBlank { config.officeGatewayIp }))

        when (config.authType) {
            Ikev2AuthType.PSK -> {
                builder.setAuthPsk(config.preSharedKey.toByteArray(StandardCharsets.UTF_8))
            }
            Ikev2AuthType.USERNAME_PASSWORD -> {
                val eapConfig = EapSessionConfig.Builder()
                    .setEapIdentity(config.username.toByteArray(StandardCharsets.UTF_8))
                    .setEapMsChapV2Config(config.username, config.password)
                    .build()
                builder.setAuthEap(null, eapConfig)
            }
            Ikev2AuthType.RSA_CERTIFICATE -> {
                // TODO: Certificate-based IKEv2 auth (setAuthDigitalSignature) requires loading
                // the end-entity cert/private key and optional CA cert from config.
                // userCertificateAlias/serverCaCertificateAlias are reserved for this purpose but
                // not yet implemented in this pass. Fail loudly instead of silently falling back.
                throw UnsupportedOperationException(
                    "Certificate-based IKEv2 authentication is not implemented yet. " +
                        "Select PSK or Username/Password authentication."
                )
            }
        }

        return builder.build()
    }

    private fun buildChildSessionParams(config: VpnConfig): TransportModeChildSessionParams {
        val builder = TransportModeChildSessionParams.Builder()
            .addChildSaProposal(defaultChildSaProposal())

        config.officeSubnets.forEach { subnet ->
            trafficSelectorFor(subnet)?.let { ts ->
                builder.addInboundTrafficSelectors(ts)
                builder.addOutboundTrafficSelectors(ts)
            }
        }

        return builder.build()
    }

    private fun defaultIkeSaProposal(): IkeSaProposal {
        return IkeSaProposal.Builder()
            .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_256)
            .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128)
            .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
            .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96)
            .addPseudorandomFunction(SaProposal.PSEUDORANDOM_FUNCTION_SHA2_256)
            .addDhGroup(SaProposal.DH_GROUP_2048_BIT_MODP)
            .build()
    }

    private fun defaultChildSaProposal(): ChildSaProposal {
        return ChildSaProposal.Builder()
            .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_256)
            .addEncryptionAlgorithm(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC, SaProposal.KEY_LEN_AES_128)
            .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA2_256_128)
            .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96)
            .build()
    }

    private fun identityFor(value: String): IkeIdentification {
        return try {
            when (val addr = InetAddresses.parseNumericAddress(value)) {
                is Inet4Address -> IkeIpv4AddrIdentification(addr)
                else -> IkeFqdnIdentification(value)
            }
        } catch (e: IllegalArgumentException) {
            IkeFqdnIdentification(value)
        }
    }

    private fun trafficSelectorFor(subnet: SubnetConfig): IkeTrafficSelector? {
        return try {
            val (startBytes, endBytes) = subnetAddressRange(subnet)
            IkeTrafficSelector(
                0,
                65535,
                Inet4Address.getByAddress(startBytes) as Inet4Address,
                Inet4Address.getByAddress(endBytes) as Inet4Address
            )
        } catch (e: Exception) {
            listener(
                IkeControlPlaneEvent.Error(
                    "Failed to build traffic selector for office subnet ${subnet.address}/${subnet.prefixLength}: ${e.message}"
                )
            )
            null
        }
    }

    private fun subnetAddressRange(subnet: SubnetConfig): Pair<ByteArray, ByteArray> {
        val parts = subnet.address.split(".").map { it.toInt() }
        require(parts.size == 4) { "Invalid subnet address: ${subnet.address}" }
        val base = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val mask = if (subnet.prefixLength == 0) 0 else (0xFFFFFFFF.toInt() shl (32 - subnet.prefixLength))
        val network = base and mask
        val broadcast = network or mask.inv()
        return intToBytes(network) to intToBytes(broadcast)
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun describe(exception: IkeException): String {
        return if (exception is IkeProtocolException) {
            "IKE protocol error type=${exception.errorType}: ${exception.message ?: exception.javaClass.simpleName}"
        } else {
            exception.message ?: exception.javaClass.simpleName
        }
    }

    private val ikeSessionCallback = object : IkeSessionCallback {
        override fun onOpened(sessionConfiguration: IkeSessionConfiguration) {
            val remote = sessionConfiguration.ikeSessionConnectionInfo.remoteAddress.hostAddress
                ?: "unknown"
            listener(IkeControlPlaneEvent.IkeSessionUp(remote))
        }

        override fun onClosed() {
            lastChildTransform = null
            listener(IkeControlPlaneEvent.Closed(null))
        }

        override fun onClosedWithException(exception: IkeException) {
            lastChildTransform = null
            listener(IkeControlPlaneEvent.Error(describe(exception)))
        }

        override fun onError(exception: IkeException) {
            listener(IkeControlPlaneEvent.Error(describe(exception)))
        }
    }

    private val childSessionCallback = object : ChildSessionCallback {
        override fun onOpened(sessionConfiguration: ChildSessionConfiguration) {
            listener(IkeControlPlaneEvent.ChildSaUp)
        }

        override fun onClosed() {
            lastChildTransform = null
            listener(IkeControlPlaneEvent.ChildSaDown("Child SA closed"))
        }

        override fun onClosedWithException(exception: IkeException) {
            lastChildTransform = null
            listener(IkeControlPlaneEvent.ChildSaDown(describe(exception)))
        }

        override fun onIpSecTransformCreated(ipSecTransform: IpSecTransform, direction: Int) {
            // Kept for observability/future dataplane use. Kernel-applied tunnel-mode offload is
            // unavailable to this app (see class doc); the transform is not applied anywhere yet.
            lastChildTransform = ipSecTransform
        }

        override fun onIpSecTransformDeleted(ipSecTransform: IpSecTransform, direction: Int) {
            if (lastChildTransform == ipSecTransform) {
                lastChildTransform = null
            }
        }
    }
}
