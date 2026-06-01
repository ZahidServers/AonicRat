# Dependencies

## Controller (AonicRat)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `org.json` | 20240303 | JSON serialization for protocol messages |
| `maven-shade-plugin` | 3.5.2 | Bundles all deps into a single fat JAR |

Java 18+ required. All deps bundled — no install needed on the operator machine.

---

## Agent (SimpleServer)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `org.json` | 20240303 | JSON protocol parsing |
| `pcap4j-core` | 1.8.2 | Java wrapper for libpcap/Npcap (packet capture) |
| `pcap4j-packetfactory-static` | 1.8.2 | Static packet factory for Pcap4J |
| `slf4j-nop` | 2.0.13 | No-op logger (suppresses Pcap4J internal output) |

All bundled into the agent fat JAR. Only the native library (Npcap/libpcap) needs to be present on the target OS at runtime — and only for packet capture.

---

## Native Runtime Dependencies (Agent Machine Only)

These are OS-level native libraries, **not** Java dependencies. Required only for **Packet Capture**. All other features work with pure Java.

| OS | Library | How to Install |
|----|---------|---------------|
| **Windows** | **Npcap** | Auto-downloaded and silently installed by the agent on first capture use (~60s). Restart agent after. |
| **Linux (Debian/Ubuntu)** | **libpcap** | `sudo apt install libpcap-dev` |
| **Linux (RHEL/CentOS)** | **libpcap** | `sudo yum install libpcap-devel` |
| **macOS** | **libpcap** | Pre-installed. Or: `brew install libpcap` |

---

## Feature → Dependency Matrix

| Feature | Pure Java | Needs Npcap/libpcap | Needs Admin/Root |
|---------|:---------:|:-------------------:|:----------------:|
| File System | ✅ | — | — |
| Command Line | ✅ | — | — |
| GUI Remote | ✅ | — | — |
| **Packet Capture** | — | ✅ | ✅ |
| Hosts Editor (read) | ✅ | — | — |
| Hosts Editor (write) | ✅ | — | ✅ |
| Network Interfaces | ✅ | — | — |
| System Info | ✅ | — | — |
| Process Manager | ✅ | — | — |
| Clipboard | ✅ | — | — |
| **Keylogger** | — | — | ✅ |
| Power Controls | ✅ | — | ✅ |
| Startup Manager (read) | ✅ | — | — |
| Startup Manager (write) | ✅ | — | ✅ |
| Message Box | ✅ | — | — |
| Client Generator | ✅ | — | — |
