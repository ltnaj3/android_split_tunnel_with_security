package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.components.DnsQueryLogCard
import com.example.myapplication.ui.components.SecurityFilterCard
import com.example.myapplication.ui.components.SplitTunnelingCard
import com.example.myapplication.ui.components.ThreatListCard
import com.example.myapplication.ui.components.VpnConfigCard
import com.example.myapplication.ui.components.VpnConnectionCard
import com.example.myapplication.ui.components.VpnLogCard
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.viewmodel.VpnUiState
import com.example.myapplication.ui.viewmodel.VpnViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnDashboardScreen(
    viewModel: VpnViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 700.dp

        if (isWideScreen) {
            // Adaptive Tablet / Desktop Layout
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        icon = { Icon(imageVector = Icons.Rounded.Dashboard, contentDescription = null) },
                        label = { Text("Overview") }
                    )
                    NavigationRailItem(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        icon = { Icon(imageVector = Icons.Rounded.Router, contentDescription = null) },
                        label = { Text("Split Route") }
                    )
                    NavigationRailItem(
                        selected = uiState.selectedTab == 2,
                        onClick = { viewModel.setSelectedTab(2) },
                        icon = { Icon(imageVector = Icons.Rounded.Security, contentDescription = null) },
                        label = { Text("Security") }
                    )
                    NavigationRailItem(
                        selected = uiState.selectedTab == 3,
                        onClick = { viewModel.setSelectedTab(3) },
                        icon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Config") }
                    )
                    NavigationRailItem(
                        selected = uiState.selectedTab == 4,
                        onClick = { viewModel.setSelectedTab(4) },
                        icon = { Icon(imageVector = Icons.AutoMirrored.Rounded.ListAlt, contentDescription = null) },
                        label = { Text("Logs") }
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    text = "Office VPN & Security Filter",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        WideScreenContent(
                            uiState = uiState,
                            context = context,
                            viewModel = viewModel
                        )
                    }
                }
            }
        } else {
            // Phone / Compact Screen Layout
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    text = "Office VPN Dashboard",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        ScrollableTabRow(
                            selectedTabIndex = uiState.selectedTab,
                            edgePadding = 16.dp,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Tab(
                                selected = uiState.selectedTab == 0,
                                onClick = { viewModel.setSelectedTab(0) },
                                text = { Text("Overview") }
                            )
                            Tab(
                                selected = uiState.selectedTab == 1,
                                onClick = { viewModel.setSelectedTab(1) },
                                text = { Text("Split Route") }
                            )
                            Tab(
                                selected = uiState.selectedTab == 2,
                                onClick = { viewModel.setSelectedTab(2) },
                                text = { Text("Security") }
                            )
                            Tab(
                                selected = uiState.selectedTab == 3,
                                onClick = { viewModel.setSelectedTab(3) },
                                text = { Text("Config") }
                            )
                            Tab(
                                selected = uiState.selectedTab == 4,
                                onClick = { viewModel.setSelectedTab(4) },
                                text = { Text("Logs") }
                            )
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        NavigationBarItem(
                            selected = uiState.selectedTab == 0,
                            onClick = { viewModel.setSelectedTab(0) },
                            icon = { Icon(imageVector = Icons.Rounded.Dashboard, contentDescription = null) },
                            label = { Text("Overview") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 1,
                            onClick = { viewModel.setSelectedTab(1) },
                            icon = { Icon(imageVector = Icons.Rounded.Router, contentDescription = null) },
                            label = { Text("Split Route") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 2,
                            onClick = { viewModel.setSelectedTab(2) },
                            icon = { Icon(imageVector = Icons.Rounded.Security, contentDescription = null) },
                            label = { Text("Security") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 3,
                            onClick = { viewModel.setSelectedTab(3) },
                            icon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Config") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 4,
                            onClick = { viewModel.setSelectedTab(4) },
                            icon = { Icon(imageVector = Icons.AutoMirrored.Rounded.ListAlt, contentDescription = null) },
                            label = { Text("Logs") }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                ) {
                    PhoneScreenContent(
                        uiState = uiState,
                        context = context,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneScreenContent(
    uiState: VpnUiState,
    context: Context,
    viewModel: VpnViewModel
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (uiState.selectedTab) {
            0 -> {
                // Overview Tab
                VpnConnectionCard(
                    vpnState = uiState.vpnState,
                    vpnConfig = uiState.vpnConfig,
                    vpnMetrics = uiState.vpnMetrics,
                    formattedUptime = uiState.formattedUptime,
                    isSimulatingTraffic = uiState.isSimulatingTraffic,
                    onToggleVpn = { viewModel.toggleVpn(context) },
                    onToggleSimulation = { viewModel.toggleTrafficSimulation() }
                )

                SplitTunnelingCard(
                    vpnConfig = uiState.vpnConfig,
                    vpnMetrics = uiState.vpnMetrics
                )

                SecurityFilterCard(
                    vpnConfig = uiState.vpnConfig,
                    vpnMetrics = uiState.vpnMetrics,
                    threatEvents = uiState.threatEvents,
                    onToggleRule = { viewModel.toggleInspectionRule(it) },
                    onToggleLocalInspection = { viewModel.toggleLocalInspection(it) },
                    onClearThreats = { viewModel.clearThreats() }
                )

                DnsQueryLogCard(
                    dnsLogs = uiState.dnsQueryLogs,
                    onClearLogs = { viewModel.clearDnsLogs() }
                )
            }

            1 -> {
                // Split Route Tab
                SplitTunnelingCard(
                    vpnConfig = uiState.vpnConfig,
                    vpnMetrics = uiState.vpnMetrics
                )
            }

            2 -> {
                // Security Tab
                SecurityFilterCard(
                    vpnConfig = uiState.vpnConfig,
                    vpnMetrics = uiState.vpnMetrics,
                    threatEvents = uiState.threatEvents,
                    onToggleRule = { viewModel.toggleInspectionRule(it) },
                    onToggleLocalInspection = { viewModel.toggleLocalInspection(it) },
                    onClearThreats = { viewModel.clearThreats() }
                )

                DnsQueryLogCard(
                    dnsLogs = uiState.dnsQueryLogs,
                    onClearLogs = { viewModel.clearDnsLogs() }
                )

                ThreatListCard(
                    threatEvents = uiState.threatEvents,
                    onClearThreats = { viewModel.clearThreats() }
                )
            }

            3 -> {
                // Configuration Tab
                VpnConfigCard(
                    vpnConfig = uiState.vpnConfig,
                    onUpdateEngineMode = { viewModel.updateEngineMode(it) },
                    onUpdateAuthType = { viewModel.updateAuthType(it) },
                    onUpdateGatewayIp = { viewModel.updateOfficeGatewayIp(it) },
                    onUpdateUsername = { viewModel.updateUsername(it) },
                    onUpdatePassword = { viewModel.updatePassword(it) },
                    onUpdatePreSharedKey = { viewModel.updatePreSharedKey(it) },
                    onUpdateIkev2Identity = { viewModel.updateIkev2Identity(it) },
                    onUpdateServerIdentifier = { viewModel.updateServerIdentifier(it) },
                    onUpdateUpstreamDns = { viewModel.updateUpstreamDns(it) },
                    onAddSubnet = { addr, prefix -> viewModel.addOfficeSubnet(addr, prefix) },
                    onRemoveSubnet = { viewModel.removeOfficeSubnet(it) },
                    onAddBlockedDomain = { viewModel.addBlockedDomain(it) },
                    onRemoveBlockedDomain = { viewModel.removeBlockedDomain(it) },
                    onAddBlockedIp = { viewModel.addBlockedIp(it) },
                    onRemoveBlockedIp = { viewModel.removeBlockedIp(it) }
                )
            }

            4 -> {
                // Activity Logs Tab
                VpnLogCard(
                    logEvents = uiState.logEvents,
                    filteredLogs = uiState.filteredLogs,
                    searchQuery = uiState.searchQuery,
                    selectedLogLevelFilter = uiState.selectedLogLevelFilter,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onLogLevelFilterChange = { viewModel.setLogLevelFilter(it) },
                    onClearLogs = { viewModel.clearLogs() }
                )
            }
        }
    }
}

@Composable
private fun WideScreenContent(
    uiState: VpnUiState,
    context: Context,
    viewModel: VpnViewModel
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (uiState.selectedTab) {
            0 -> {
                // Overview Side-by-Side Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        VpnConnectionCard(
                            vpnState = uiState.vpnState,
                            vpnConfig = uiState.vpnConfig,
                            vpnMetrics = uiState.vpnMetrics,
                            formattedUptime = uiState.formattedUptime,
                            isSimulatingTraffic = uiState.isSimulatingTraffic,
                            onToggleVpn = { viewModel.toggleVpn(context) },
                            onToggleSimulation = { viewModel.toggleTrafficSimulation() }
                        )

                        SplitTunnelingCard(
                            vpnConfig = uiState.vpnConfig,
                            vpnMetrics = uiState.vpnMetrics
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SecurityFilterCard(
                            vpnConfig = uiState.vpnConfig,
                            vpnMetrics = uiState.vpnMetrics,
                            threatEvents = uiState.threatEvents,
                            onToggleRule = { viewModel.toggleInspectionRule(it) },
                            onToggleLocalInspection = { viewModel.toggleLocalInspection(it) },
                            onClearThreats = { viewModel.clearThreats() }
                        )

                        DnsQueryLogCard(
                            dnsLogs = uiState.dnsQueryLogs,
                            onClearLogs = { viewModel.clearDnsLogs() }
                        )
                    }
                }
            }

            1 -> {
                SplitTunnelingCard(
                    vpnConfig = uiState.vpnConfig,
                    vpnMetrics = uiState.vpnMetrics
                )
            }

            2 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SecurityFilterCard(
                            vpnConfig = uiState.vpnConfig,
                            vpnMetrics = uiState.vpnMetrics,
                            threatEvents = uiState.threatEvents,
                            onToggleRule = { viewModel.toggleInspectionRule(it) },
                            onToggleLocalInspection = { viewModel.toggleLocalInspection(it) },
                            onClearThreats = { viewModel.clearThreats() }
                        )

                        DnsQueryLogCard(
                            dnsLogs = uiState.dnsQueryLogs,
                            onClearLogs = { viewModel.clearDnsLogs() }
                        )
                    }

                    ThreatListCard(
                        threatEvents = uiState.threatEvents,
                        onClearThreats = { viewModel.clearThreats() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            3 -> {
                VpnConfigCard(
                    vpnConfig = uiState.vpnConfig,
                    onUpdateEngineMode = { viewModel.updateEngineMode(it) },
                    onUpdateAuthType = { viewModel.updateAuthType(it) },
                    onUpdateGatewayIp = { viewModel.updateOfficeGatewayIp(it) },
                    onUpdateUsername = { viewModel.updateUsername(it) },
                    onUpdatePassword = { viewModel.updatePassword(it) },
                    onUpdatePreSharedKey = { viewModel.updatePreSharedKey(it) },
                    onUpdateIkev2Identity = { viewModel.updateIkev2Identity(it) },
                    onUpdateServerIdentifier = { viewModel.updateServerIdentifier(it) },
                    onUpdateUpstreamDns = { viewModel.updateUpstreamDns(it) },
                    onAddSubnet = { addr, prefix -> viewModel.addOfficeSubnet(addr, prefix) },
                    onRemoveSubnet = { viewModel.removeOfficeSubnet(it) },
                    onAddBlockedDomain = { viewModel.addBlockedDomain(it) },
                    onRemoveBlockedDomain = { viewModel.removeBlockedDomain(it) },
                    onAddBlockedIp = { viewModel.addBlockedIp(it) },
                    onRemoveBlockedIp = { viewModel.removeBlockedIp(it) }
                )
            }

            4 -> {
                VpnLogCard(
                    logEvents = uiState.logEvents,
                    filteredLogs = uiState.filteredLogs,
                    searchQuery = uiState.searchQuery,
                    selectedLogLevelFilter = uiState.selectedLogLevelFilter,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onLogLevelFilterChange = { viewModel.setLogLevelFilter(it) },
                    onClearLogs = { viewModel.clearLogs() }
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun VpnDashboardScreenTabletPreview() {
    MyApplicationTheme {
        VpnDashboardScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun VpnDashboardScreenPhonePreview() {
    MyApplicationTheme {
        VpnDashboardScreen()
    }
}
