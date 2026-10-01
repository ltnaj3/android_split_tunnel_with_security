# Project Plan

A VPN application test scenario/app demonstrating the use of Android's built-in IPSec stack (IKEv2/IPSec via VpnService or Ikev2VpnProfile), establishing VPN connections to IPSec gateway devices, with split tunneling configured so that designated 'office' destination traffic routes through the IPSec VPN, while all non-office (internet/other) traffic is intercepted and inspected locally by the VPN app for phone protection / security filtering.

## Project Brief

# Project Brief: IPSec & Local Security Filtering VPN

## Features
1. **IKEv2/IPSec VPN Connection Management**: Establish and manage secure IKEv2/IPSec VPN connections to remote IPSec gateway devices leveraging Android's native `VpnService` and `Ikev2VpnProfile` APIs.
2. **Split Tunneling Routing**: Route designated office/enterprise network subnets directly through the IPSec VPN tunnel while directing external traffic to local inspection.
3. **Local Security Filtering & Threat Inspection**: Intercept and analyze non-office (internet) traffic locally on the device via `VpnService` to provide real-time device protection and security filtering.
4. **VPN Status & Control Dashboard**: Real-time status UI displaying connection state (Disconnected, Connecting, Connected), active routing details, and quick toggle controls.

## High-Level Tech Stack
* **Language**: Kotlin
* **UI Toolkit**: Jetpack Compose
* **Navigation**: Jetpack Navigation 3 (state-driven)
* **Adaptive Strategy**: Compose Material Adaptive library
* **Asynchronous Programming**: Kotlin Coroutines & Flow
* **Architecture**: MVVM with `ViewModel` and `StateFlow`
* **VPN & Networking Stack**: Android Platform `VpnService` & IKEv2/IPSec API (`Ikev2VpnProfile`, `IpSecManager`)

## Implementation Steps
**Total Duration:** 18m 29s

### Task_1_CoreVpnAndNetworking: Implement VpnService, IKEv2/IPSec setup using Ikev2VpnProfile, split tunneling subnet routing for office traffic, and local packet security filtering interceptor.
- **Status:** COMPLETED
- **Updates:** Successfully implemented VpnService, Ikev2Manager, split tunneling subnet routing, local packet security parser and inspector, VpnRepository, VpnConnectionManager, AndroidManifest permissions, and verified unit tests build clean.
- **Acceptance Criteria:**
  - VpnService and IKEv2/IPSec configuration implemented
  - Split tunneling and local packet inspection logic created
  - build pass
- **Duration:** 9m 3s

### Task_2_DashboardUiAndState: Develop Jetpack Compose VPN Dashboard UI, state management via ViewModel, connection status toggle controls, active routing metrics, and security threat logs display.
- **Status:** COMPLETED
- **Updates:** Successfully implemented VpnViewModel, VpnDashboardScreen with Material3 Jetpack Compose UI, responsive phone/tablet support, VpnConnectionCard, SplitTunnelingCard, SecurityFilterCard, VpnConfigCard, VpnLogCard, ThreatListCard, and MainActivity. All unit tests passed cleanly.
- **Acceptance Criteria:**
  - Dashboard UI built with Jetpack Compose and state management
  - Toggle control and connection state indicators functional
  - build pass
- **Duration:** 9m 26s

### Task_3_RealIkev2AndSocketForwarding: Implement real IKEv2/IPSec VPN profile integration via VpnManager/Ikev2VpnProfile, real NIO socket forwarding with VpnService.protect() for live non-office traffic, and real IP/TCP/UDP packet processing.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Real IKEv2/IPSec VPN profile configuration implemented with VpnManager
  - Real socket forwarding using protected sockets and packet reconstruction functional
  - build pass
- **StartTime:** 2026-09-30 12:33:24 CDT

### Task_4_RunAndVerify: Run and verify application stability, instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** PENDING
- **Acceptance Criteria:**
  - critic_agent verified application stability with no crashes
  - confirm alignment with user requirements and report critical UI issues
  - make sure all existing tests pass
  - build pass
  - app does not crash

