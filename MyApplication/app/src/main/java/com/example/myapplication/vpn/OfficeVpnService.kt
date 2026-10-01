package com.example.myapplication.vpn

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.model.LogLevel
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnEngineMode
import com.example.myapplication.model.VpnState
import com.example.myapplication.repository.VpnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class OfficeVpnService : VpnService() {

    private val repository = VpnRepository.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetLoopJob: Job? = null
    private var isRunning = false
    private var officeTunnel: OfficeTunnelEngine? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT

        when (action) {
            ACTION_CONNECT -> startVpnSession()
            ACTION_DISCONNECT -> stopVpnSession()
        }

        return START_STICKY
    }

    private fun startVpnSession() {
        if (isRunning) return
        isRunning = true

        repository.updateState(VpnState.Connecting)
        val config = repository.vpnConfig.value

        startForegroundServiceNotification(config)

        serviceScope.launch {
            try {
                val pfd = establishVpnInterface(config)
                if (pfd == null) {
                    repository.updateState(VpnState.Error("Failed to establish VPN TUN interface"))
                    stopSelf()
                    return@launch
                }
                vpnInterface = pfd
                repository.updateState(VpnState.Connected)
                repository.addLog(
                    LogLevel.INFO,
                    "Office VPN Service connected [Engine: ${config.engineMode.displayName}]"
                )

                // Negotiate the office IKEv2/IPsec control-plane session using Android's
                // built-in IKE library (android.net.ipsec.ike). This OfficeVpnService TUN
                // remains the single, app-owned VPN interface on the device -- we intentionally
                // do NOT provision/start a VpnManager-owned Ikev2VpnProfile (see Ikev2Manager),
                // since that would create a second, competing VPN tunnel owner and would remove
                // this app's ability to inspect non-office traffic.
                if (config.engineMode == VpnEngineMode.NATIVE_IKEV2_IPSEC ||
                    config.engineMode == VpnEngineMode.HYBRID_SPLIT_TUNNEL
                ) {
                    startOfficeTunnel(config)
                }

                startPacketLoop(pfd, config)
            } catch (e: Exception) {
                e.printStackTrace()
                repository.updateState(VpnState.Error(e.message ?: "VPN connection error"))
                stopVpnSession()
            }
        }
    }

    private fun startOfficeTunnel(config: VpnConfig) {
        if (officeTunnel != null) return
        val tunnel = IpsecOfficeTunnel(applicationContext, repository)
        officeTunnel = tunnel
        tunnel.start(config)
    }

    private fun establishVpnInterface(config: VpnConfig): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("OfficeVpnService")
            .setMtu(1500)
            .addAddress(config.localTunAddress, config.localTunPrefixLength)

        // Add Office Subnet routes (Split tunneling rule: Route office subnets through VPN)
        config.officeSubnets.forEach { subnet ->
            try {
                builder.addRoute(subnet.address, subnet.prefixLength)
                repository.addLog(LogLevel.INFO, "Added Office Subnet route: ${subnet.address}/${subnet.prefixLength}")
            } catch (e: Exception) {
                repository.addLog(LogLevel.WARN, "Failed to add route ${subnet.address}/${subnet.prefixLength}: ${e.message}")
            }
        }

        // Add default split-routes for non-office traffic in local security or hybrid mode to inspect/proxy
        if (config.engineMode != VpnEngineMode.NATIVE_IKEV2_IPSEC) {
            try {
                builder.addRoute("0.0.0.0", 1)
                builder.addRoute("128.0.0.0", 1)
            } catch (e: Exception) {
                repository.addLog(LogLevel.WARN, "Failed to set default split routes: ${e.message}")
            }
        }

        // Configure Upstream DNS servers
        val dnsToUse = if (config.upstreamDns.isNotBlank()) config.upstreamDns else config.dnsServers.firstOrNull() ?: "8.8.8.8"
        try {
            builder.addDnsServer(dnsToUse)
            repository.addLog(LogLevel.INFO, "DNS Server configured: $dnsToUse")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Disallow self package to prevent loopbacks
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return builder.establish()
    }

    private fun startPacketLoop(pfd: ParcelFileDescriptor, config: VpnConfig) {
        packetLoopJob = serviceScope.launch {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(32767)

            repository.addLog(LogLevel.INFO, "Live Network Traffic Processing & Security Proxy Active")

            while (isActive && isRunning) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val parsed = PacketParser.parseAndInspect(buffer, bytesRead, config)
                        if (parsed != null) {
                            if (parsed.isDns) {
                                // Real DNS Request Forwarding & Security Inspection
                                val dnsResult = DnsProxy.processDnsQuery(
                                    rawIpPacket = buffer,
                                    packetLen = bytesRead,
                                    srcIp = parsed.sourceIp,
                                    srcPort = parsed.sourcePort,
                                    dstIp = parsed.destinationIp,
                                    config = config,
                                    vpnService = this@OfficeVpnService
                                )

                                if (dnsResult != null) {
                                    // Write DNS Response packet back to TUN interface
                                    try {
                                        outputStream.write(dnsResult.responsePacket)
                                        outputStream.flush()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    // Record DNS query log and threat if any
                                    repository.recordDnsQuery(dnsResult.queryLog)
                                    if (dnsResult.threat != null) {
                                        repository.recordThreat(dnsResult.threat)
                                    }

                                    repository.recordInspectedPacket(
                                        bytes = bytesRead.toLong(),
                                        isDns = true
                                    )
                                }
                            } else {
                                // Real Non-DNS TCP/UDP & Office Packet Processing
                                val forwardResult = PacketForwarder.processPacket(
                                    buffer = buffer,
                                    length = bytesRead,
                                    parsedInfo = parsed,
                                    config = config,
                                    vpnService = this@OfficeVpnService,
                                    officeTunnel = officeTunnel
                                )

                                if (forwardResult.isOfficeTraffic) {
                                    if (forwardResult.officeTunnelRouted) {
                                        repository.recordOfficeTraffic(
                                            bytesSent = bytesRead.toLong(),
                                            bytesReceived = 0L,
                                            packets = 1L
                                        )
                                    } else {
                                        repository.recordOfficeTrafficDropped(
                                            bytes = bytesRead.toLong(),
                                            reason = forwardResult.officeTunnelDropReason
                                                ?: "Office packet not forwarded (unknown reason)"
                                        )
                                    }
                                } else {
                                    repository.recordInspectedPacket(
                                        bytes = bytesRead.toLong(),
                                        isDns = false
                                    )

                                    if (forwardResult.threat != null) {
                                        repository.recordThreat(forwardResult.threat)
                                    } else if (parsed.detectedThreat != null) {
                                        repository.recordThreat(parsed.detectedThreat)
                                    }

                                    if (forwardResult.responsePacket != null) {
                                        try {
                                            outputStream.write(forwardResult.responsePacket)
                                            outputStream.flush()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (isRunning) {
                        repository.addLog(LogLevel.WARN, "TUN IO Exception: ${e.message}")
                    }
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun stopVpnSession() {
        if (!isRunning) return
        isRunning = false

        repository.updateState(VpnState.Disconnecting)
        repository.addLog(LogLevel.INFO, "Disconnecting VPN service...")

        packetLoopJob?.cancel()
        packetLoopJob = null

        officeTunnel?.stop()
        officeTunnel = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        repository.updateState(VpnState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Office VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN tunnel and local network security service"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification(config: VpnConfig) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Office VPN Active")
            .setContentText("Engine: ${config.engineMode.displayName} | DNS Filtering Active")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        stopVpnSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.example.myapplication.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.myapplication.vpn.DISCONNECT"
        private const val CHANNEL_ID = "office_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        fun startVpn(context: Context) {
            val intent = Intent(context, OfficeVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, OfficeVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
