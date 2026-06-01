# Architecture

## Components

```
AonicRat/
├── src/          ← Controller (operator GUI dashboard)
└── SimpleServer/ ← Agent (runs on target machine)
```

## Reverse Connection

```
[ Agent ]  ──TCP connect──▶  [ Controller ]
 Target                        Operator
 (calls back on startup)       (listens on port)
```

Agent initiates the connection — works through NAT and firewalls without port forwarding on the target.

## Protocol

All communication is over a persistent TCP socket using structured JSON messages.

```
Controller                    Agent
    │──── LIST_FILES /home ──▶│
    │◀─── file entries ────────│
    │                          │
    │──── START_SHELL ────────▶│
    │◀─── shell output stream ─│
    │──── SHELL_INPUT "ls" ───▶│
    │◀─── output ──────────────│
    │                          │
    │──── SCREEN_CAPTURE ─────▶│
    │◀─── JPEG frame bytes ────│
```

## Config Injection

Controller IP/port are injected into `aon.properties` inside the agent JAR at generation time — not hardcoded in source.

## Fat JARs

Both components packaged as fat JARs via `maven-shade-plugin`. Single file, no dependency install on target.

## Key Files

| File | Role |
|------|------|
| `AonMain.java` | Main window, all tab wiring |
| `ServerManager.java` | TCP server, protocol, all device listeners |
| `RemoteDevice.java` | Connected device model |
| `ClientGeneratorPanel.java` | Agent builder |
| `NetworkControlPanel.java` | Packet capture, hosts, interfaces |
| `TerminalPanel.java` | Interactive terminal UI |
| `RemoteDesktopPanel.java` | Screen viewer |
| `AdvancedPanel.java` | Power, startup, message box |
| `OthersPanel.java` | Sysinfo, processes, clipboard, keylogger |
