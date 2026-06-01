# Architecture

## Overview

AonicRAT is split into two separate components that communicate over TCP:

```
AonicRat/
├── src/                  ← Controller (operator's dashboard)
└── SimpleServer/         ← Agent (runs on the target machine)
```

| Component | Description |
|-----------|-------------|
| **Controller** | Java Swing GUI. The operator's dashboard. Manages all connected devices, sends commands, receives and displays data. |
| **Agent (SimpleServer)** | Lightweight Java process. Deployed on the target machine. Connects back to the controller, executes commands, and streams results. |

---

## Reverse Connection Model

The agent uses a **reverse TCP connection** — it initiates the connection to the controller, not the other way around.

```
[ Agent / SimpleServer ]  ──── TCP connect ────▶  [ Controller / AonicRat ]
   Windows / Linux / macOS                            Windows / Linux / macOS
      Target Machine                                    Operator Machine
   (calls back on startup)                           (listens on port 5555)
```

### Why Reverse Connection?

| Approach | Description |
|----------|-------------|
| **Forward connection** | Controller connects to the agent. Requires the target to have an open inbound port — usually blocked by firewalls and NAT. |
| **Reverse connection** ✅ | Agent connects to the controller. Outbound connections are almost never blocked. Works through NAT, corporate firewalls, and home routers without any port forwarding. |

This is the same model used by:

- **Metasploit Meterpreter** (`reverse_tcp` payload)
- **Cobalt Strike Beacon** (calls home to team server)
- **Enterprise MDM agents** (ManageEngine, Intune device agents)
- **PDQ Deploy agents**

---

## Communication Protocol

All communication between the agent and controller happens over a **persistent TCP socket**. Commands are sent as structured messages and responses are streamed back in real time.

```
Controller                          Agent
    │                                 │
    │──── connect notification ───────│
    │                                 │
    │──── LIST_FILES /home ──────────▶│
    │◀─── file entries ───────────────│
    │                                 │
    │──── SHELL_INPUT "ls -la" ──────▶│
    │◀─── shell output stream ────────│
    │                                 │
    │──── SCREEN_CAPTURE ────────────▶│
    │◀─── JPEG frame bytes ───────────│
    │                                 │
```

---

## Project Structure

```
AonicRat/
├── src/main/java/com/webaon/aonicrat/
│   ├── AonMain.java                ← Main window, all tab wiring, device management
│   ├── ServerManager.java          ← TCP server, protocol handling, all device listeners
│   ├── RemoteDevice.java           ← Model for a connected device
│   ├── ClientGeneratorPanel.java   ← Agent builder (Windows EXE / Linux / macOS)
│   ├── RemoteDesktopPanel.java     ← Remote desktop screen viewer
│   ├── NetworkControlPanel.java    ← Packet capture, hosts editor, interfaces
│   ├── TerminalPanel.java          ← Interactive terminal UI component
│   ├── AdvancedPanel.java          ← Power controls, startup manager, message box
│   ├── OthersPanel.java            ← System info, process manager, clipboard, keylogger
│   ├── FileViewerWindow.java       ← Remote file content viewer and editor
│   ├── TerminalDocumentFilter.java ← Protects terminal prompt from editing
│   ├── IntegerVerifier.java        ← Input validation for numeric fields
│   └── images/                     ← Toolbar icons (PNG)
│
└── SimpleServer/                   ← Agent source code (calls back to controller)
```

---

## Key Design Decisions

### Java + One Native Dependency (Packet Capture Only)

Both components are written in Java and run on Windows, Linux, and macOS with a compatible JVM.

**Almost everything is pure Java — no dependencies, no installs, no setup:**

| Feature | Dependencies Required |
|---------|----------------------|
| File System | None — pure Java |
| Command Line / Terminal | None — pure Java |
| GUI Remote / Remote Desktop | None — pure Java |
| Hosts Editor | None — pure Java |
| Network Interfaces | None — pure Java |
| System Info | None — pure Java |
| Process Manager | None — pure Java |
| Clipboard | None — pure Java |
| Keylogger | None — pure Java (uses OS APIs available to the JVM) |
| Power Controls | None — pure Java |
| Startup Manager | None — pure Java |
| Message Box | None — pure Java |
| Client Generator | None — pure Java (Launch4j auto-downloaded on first use) |
| **Packet Capture** | ⚠️ Requires **Npcap** (Windows) or **libpcap** (Linux/macOS) on the target machine |

The **only exception** is packet capture — it requires the native library **Npcap** (Windows) or **libpcap** (Linux/macOS) to be present on the **target/agent machine**. The Java wrapper (`pcap4j-core`) is already bundled in the fat JAR. On Windows, Npcap is auto-installed silently by the agent on first use.

The controller has zero native dependencies — pure Java Swing throughout.

See [Dependencies](dependencies.md) for the full breakdown.

### Fat JAR
Both components are packaged as fat JARs (all Java dependencies bundled via `maven-shade-plugin`). This makes deployment simple — copy one file and run it. The only thing **not** bundled is the native packet capture library (Npcap/libpcap), which operates at the OS level and cannot be bundled into a JAR.

### Agent Config Injection
The controller's IP and port are not hardcoded in the agent source. They are injected into `aon.properties` inside the agent JAR at generation time by the Client Generator. This is how enterprise deployment tools configure agents per environment.
