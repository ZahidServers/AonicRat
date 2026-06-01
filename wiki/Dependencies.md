# Dependencies

## Controller (AonicRat)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `org.json` | 20240303 | JSON serialization for protocol messages |
| `maven-shade-plugin` | 3.5.2 | Bundles all deps into a single fat JAR |

Java 18+ required. All deps bundled — no install needed.

---

## Agent (SimpleServer)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `org.json` | 20240303 | JSON protocol parsing |
| `pcap4j-core` | 1.8.2 | Java wrapper for libpcap/Npcap (packet capture) |
| `pcap4j-packetfactory-static` | 1.8.2 | Static packet factory for Pcap4J |
| `slf4j-nop` | 2.0.13 | No-op logger (suppresses Pcap4J output) |

All bundled into the agent fat JAR via `maven-shade-plugin`.

---

## Native Runtime Dependencies (Agent Machine)

These are **not** Java libraries — they are OS-level native libraries required for packet capture only. All other features work without them.

| OS | Library | How to Install |
|----|---------|---------------|
| **Windows** | **Npcap** | Auto-installed by the agent on first capture (~60s). Restart agent after. |
| **Linux** | **libpcap** | `sudo apt install libpcap-dev` (Debian/Ubuntu) |
| **Linux (RHEL)** | **libpcap** | `sudo yum install libpcap-devel` |
| **macOS** | **libpcap** | Pre-installed. Or: `brew install libpcap` |

> If you don't need packet capture, these native libraries are never used. All other features (file system, terminal, remote desktop, hosts editor, interfaces, etc.) work with pure Java — no native deps.

---

## Feature → Dependency Matrix

| Feature | Java Only | Needs Native Lib | Needs Admin/Root |
|---------|:---------:|:----------------:|:----------------:|
| File System | ✅ | — | — |
| Command Line | ✅ | — | — |
| GUI Remote | ✅ | — | — |
| **Packet Capture** | — | ✅ Npcap/libpcap | ✅ |
| Hosts Editor (read) | ✅ | — | — |
| Hosts Editor (write) | ✅ | — | ✅ |
| Network Interfaces | ✅ | — | — |
| System Info | ✅ | — | — |
| Process Manager | ✅ | — | — |
| Clipboard | ✅ | — | — |
| **Keylogger** | — | OS API | ✅ |
| Power Controls | ✅ | — | ✅ |
| Startup Manager | ✅ | — | ✅ (write) |
| Message Box | ✅ | — | — |
| Client Generator | ✅ | — | — |
