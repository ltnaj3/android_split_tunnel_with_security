package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Domain
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.Ikev2AuthType
import com.example.myapplication.model.SubnetConfig
import com.example.myapplication.model.VpnConfig
import com.example.myapplication.model.VpnEngineMode
import com.example.myapplication.ui.theme.MyApplicationTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VpnConfigCard(
    vpnConfig: VpnConfig,
    onUpdateEngineMode: (VpnEngineMode) -> Unit,
    onUpdateAuthType: (Ikev2AuthType) -> Unit,
    onUpdateGatewayIp: (String) -> Unit,
    onUpdateUsername: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onUpdatePreSharedKey: (String) -> Unit,
    onUpdateIkev2Identity: (String) -> Unit,
    onUpdateServerIdentifier: (String) -> Unit,
    onUpdateUpstreamDns: (String) -> Unit,
    onAddSubnet: (String, Int) -> Boolean,
    onRemoveSubnet: (SubnetConfig) -> Unit,
    onAddBlockedDomain: (String) -> Unit,
    onRemoveBlockedDomain: (String) -> Unit,
    onAddBlockedIp: (String) -> Unit,
    onRemoveBlockedIp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var gatewayIpText by remember(vpnConfig.officeGatewayIp) { mutableStateOf(vpnConfig.officeGatewayIp) }
    var usernameText by remember(vpnConfig.username) { mutableStateOf(vpnConfig.username) }
    var passwordText by remember(vpnConfig.password) { mutableStateOf(vpnConfig.password) }
    var pskText by remember(vpnConfig.preSharedKey) { mutableStateOf(vpnConfig.preSharedKey) }
    var identityText by remember(vpnConfig.ikev2Identity) { mutableStateOf(vpnConfig.ikev2Identity) }
    var serverIdText by remember(vpnConfig.serverIdentifier) { mutableStateOf(vpnConfig.serverIdentifier) }
    var upstreamDnsText by remember(vpnConfig.upstreamDns) { mutableStateOf(vpnConfig.upstreamDns) }

    var showPsk by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    var showAddSubnetDialog by remember { mutableStateOf(false) }
    var showAddDomainDialog by remember { mutableStateOf(false) }
    var showAddIpDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "IPSec Gateway & Tunnel Mode Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure Engine, Authentication, Subnets & Upstream DNS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // VPN Engine Mode Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "VPN Engine / Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VpnEngineMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vpnConfig.engineMode == mode,
                            onClick = { onUpdateEngineMode(mode) },
                            label = { Text(mode.displayName, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Authentication Type Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "IKEv2 Authentication Method",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Ikev2AuthType.entries.forEach { auth ->
                        FilterChip(
                            selected = vpnConfig.authType == auth,
                            onClick = { onUpdateAuthType(auth) },
                            label = { Text(auth.displayName, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Office Gateway IP & Upstream DNS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = gatewayIpText,
                    onValueChange = {
                        gatewayIpText = it
                        onUpdateGatewayIp(it)
                    },
                    label = { Text("Gateway IP / Host") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Public, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = upstreamDnsText,
                    onValueChange = {
                        upstreamDnsText = it
                        onUpdateUpstreamDns(it)
                    },
                    label = { Text("Upstream DNS") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Dns, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Username & Password Row
            if (vpnConfig.authType == Ikev2AuthType.USERNAME_PASSWORD) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = usernameText,
                        onValueChange = {
                            usernameText = it
                            onUpdateUsername(it)
                        },
                        label = { Text("Username") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Person, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = {
                            passwordText = it
                            onUpdatePassword(it)
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.Key else Icons.Rounded.VpnKey,
                                    contentDescription = "Toggle Password Visibility"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Pre-Shared Key (PSK) Field
            if (vpnConfig.authType == Ikev2AuthType.PSK || vpnConfig.authType == Ikev2AuthType.RSA_CERTIFICATE) {
                OutlinedTextField(
                    value = pskText,
                    onValueChange = {
                        pskText = it
                        onUpdatePreSharedKey(it)
                    },
                    label = { Text("Pre-Shared Key (PSK)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Key, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPsk = !showPsk }) {
                            Icon(
                                imageVector = if (showPsk) Icons.Rounded.Key else Icons.Rounded.VpnKey,
                                contentDescription = "Toggle PSK Visibility"
                            )
                        }
                    },
                    visualTransformation = if (showPsk) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // IKEv2 Remote Identifier & Server Identifier
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = identityText,
                    onValueChange = {
                        identityText = it
                        onUpdateIkev2Identity(it)
                    },
                    label = { Text("User / Remote Identity") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Badge, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = serverIdText,
                    onValueChange = {
                        serverIdText = it
                        onUpdateServerIdentifier(it)
                    },
                    label = { Text("Server Identifier") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Domain, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Office Subnet CIDRs Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Office Subnet CIDRs (${vpnConfig.officeSubnets.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(onClick = { showAddSubnetDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Subnet")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vpnConfig.officeSubnets.forEach { subnet ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text("${subnet.address}/${subnet.prefixLength}") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveSubnet(subnet) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove Subnet"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Blocked Domains Manager Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Blocked Domains (${vpnConfig.blockedDomains.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(onClick = { showAddDomainDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Domain")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vpnConfig.blockedDomains.forEach { domain ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(domain) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveBlockedDomain(domain) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove Domain"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Blocked IPs Manager Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Blocked IP Blacklist (${vpnConfig.blockedIps.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(onClick = { showAddIpDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add IP")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vpnConfig.blockedIps.forEach { ip ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(ip) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveBlockedIp(ip) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove IP"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }

    // Add Subnet Dialog
    if (showAddSubnetDialog) {
        AddSubnetDialog(
            onDismiss = { showAddSubnetDialog = false },
            onAdd = { address, prefix ->
                val success = onAddSubnet(address, prefix)
                if (success) showAddSubnetDialog = false
            }
        )
    }

    // Add Domain Dialog
    if (showAddDomainDialog) {
        AddTextEntryDialog(
            title = "Add Blocked Domain",
            label = "Domain Name (e.g. tracking.site.com)",
            onDismiss = { showAddDomainDialog = false },
            onAdd = {
                onAddBlockedDomain(it)
                showAddDomainDialog = false
            }
        )
    }

    // Add IP Dialog
    if (showAddIpDialog) {
        AddTextEntryDialog(
            title = "Add Blocked IP Address",
            label = "IPv4 Address (e.g. 198.51.100.99)",
            onDismiss = { showAddIpDialog = false },
            onAdd = {
                onAddBlockedIp(it)
                showAddIpDialog = false
            }
        )
    }
}

@Composable
private fun AddSubnetDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var addressText by remember { mutableStateOf("172.16.0.0") }
    var prefixText by remember { mutableStateOf("12") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Office Subnet Route") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    label = { Text("Subnet Address (e.g. 172.16.0.0)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = prefixText,
                    onValueChange = { prefixText = it },
                    label = { Text("Prefix Length (e.g. 12)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prefix = prefixText.toIntOrNull() ?: 24
                    onAdd(addressText, prefix)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddTextEntryDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var entryText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = entryText,
                onValueChange = { entryText = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (entryText.isNotBlank()) {
                        onAdd(entryText)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun VpnConfigCardPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            VpnConfigCard(
                vpnConfig = VpnConfig(),
                onUpdateEngineMode = {},
                onUpdateAuthType = {},
                onUpdateGatewayIp = {},
                onUpdateUsername = {},
                onUpdatePassword = {},
                onUpdatePreSharedKey = {},
                onUpdateIkev2Identity = {},
                onUpdateServerIdentifier = {},
                onUpdateUpstreamDns = {},
                onAddSubnet = { _, _ -> true },
                onRemoveSubnet = {},
                onAddBlockedDomain = {},
                onRemoveBlockedDomain = {},
                onAddBlockedIp = {},
                onRemoveBlockedIp = {}
            )
        }
    }
}
