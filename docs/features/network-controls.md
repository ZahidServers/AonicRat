# Network Controls

The Network Controls tab has three sub-tabs for remote network inspection and management.

---

## Dependencies

!!! warning "Packet Capture Requires Native Libraries"
    The Packet Capture sub-tab uses **Pcap4J** — a pure-Java wrapper around the native packet capture libraries. These native libraries must be present on the **target/agent machine**:

    | OS | Native Library | How it Gets Installed |
    |----|---------------|----------------------|
    | **Windows** | **Npcap** | Auto-downloaded and silently installed by the agent on first use (~60 seconds). Restart the agent after Npcap installs if capture hangs. |
    | **Linux** | **libpcap** | Must be installed manually: `sudo apt install libpcap-dev` (Debian/Ubuntu) or `sudo yum install libpcap-devel` (RHEL/CentOS) |
    | **macOS** | **libpcap** | Pre-installed on macOS, or via Homebrew: `brew install libpcap` |

    The Pcap4J JAR itself is bundled inside the agent fat JAR — no separate Java dependency needed.

!!! danger "Admin / Root Required"
    Packet capture requires **administrator** (Windows) or **root** (Linux/macOS) privileges on the remote device. Raw socket access is restricted to privileged processes on all operating systems.

---

## Agent Dependencies (SimpleServer pom.xml)

```xml
<!-- Pcap4J core — Java wrapper for libpcap / Npcap -->
<dependency>
    <groupId>org.pcap4j</groupId>
    <artifactId>pcap4j-core</artifactId>
    <version>1.8.2</version>
</dependency>

<!-- Static packet factory — avoids reflection/SPI at runtime -->
<dependency>
    <groupId>org.pcap4j</groupId>
    <artifactId>pcap4j-packetfactory-static</artifactId>
    <version>1.8.2</version>
</dependency>

<!-- SLF4J no-op binding — suppresses Pcap4J internal log output -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-nop</artifactId>
    <version>2.0.13</version>
</dependency>
```

All three are bundled into the agent fat JAR via `maven-shade-plugin`. Only the native library (Npcap/libpcap) needs to be present on the target OS at runtime.

---

## Packet Capture

![Packet Capture](../Screenshot_4.png)

Live network packet sniffer running on the remote device.

### Capabilities

- Capture live network packets on the remote machine
- **BPF filter** (Berkeley Packet Filter syntax) — e.g. `tcp port 80`, `host 192.168.1.1`
- Filter by protocol: All, TCP, UDP, ICMP, ARP, DNS, HTTP, HTTPS, SSH
- Displays each packet: No., Time, Source, Src Port, Destination, Dst Port, Protocol, Length, Info
- **Start / Stop** capture sessions
- **Clear** captured packets
- Max 10,000 rows — oldest rows are dropped automatically
- Table updates at 10 Hz (100ms flush timer) to stay smooth under high traffic

### First-Use on Windows

On first capture attempt on a Windows target:

1. The agent detects Npcap is missing
2. Downloads the Npcap installer silently
3. Installs it (~60 seconds)
4. The status bar shows progress: `"Downloading Npcap…"` → `"Installing Npcap…"`
5. **Restart the agent** on the target after installation completes
6. Start capture again — it will work immediately

### What You Learn

!!! info "Industry Comparison"
    This is exactly how **Wireshark with remote capture** (`rpcapd`), **tcpdump**, **Microsoft Network Monitor**, and enterprise **SIEM network agents** work. Pcap4J is the same library used by professional network analysis tools. BPF (Berkeley Packet Filter) is the industry-standard filter syntax used by Wireshark, tcpdump, and all major packet capture tools.

---

## Hosts Editor

![Hosts Editor](../Screenshot_5.png)

Remote editor for the target machine's `hosts` file.

### Capabilities

- View all entries: IP Address, Hostname, Comment
- **Add** new entries
- **Delete** existing entries
- **Reload** — re-fetch the hosts file from the remote device
- **Save to Remote** — write changes back to the remote hosts file
- **Quick Redirect** — type a hostname and target IP, click Add Redirect

### Important Notes

!!! warning "Admin / Root Required"
    Writing to the hosts file requires **admin** (Windows) or **root** (Linux/macOS) on the remote device. Save will fail with a permission error if the agent is not elevated.

!!! info "Flush DNS After Saving"
    After saving changes, DNS cache must be flushed on the remote machine for changes to take effect:

    | OS | Command |
    |----|---------|
    | Windows | `ipconfig /flushdns` |
    | Linux (systemd) | `systemd-resolve --flush-caches` |
    | macOS | `sudo dscacheutil -flushcache; sudo killall -HUP mDNSResponder` |

!!! note "HTTPS Warning"
    Redirecting an HTTPS site via hosts will still show a **certificate warning** in the browser, since the TLS certificate won't match the redirected IP.

!!! note "Quick Redirect — IP Only"
    The "Quick Redirect" target must be a **numeric IP address** (e.g. `127.0.0.1`), not a domain name. The hosts file maps hostnames to IPs, not hostnames to other hostnames.

### Hosts File Locations

| OS | Path |
|----|------|
| Windows | `C:\Windows\System32\drivers\etc\hosts` |
| Linux | `/etc/hosts` |
| macOS | `/etc/hosts` |

### What You Learn

!!! info "Industry Comparison"
    Enterprise **DNS filtering** tools, **parental control software**, and **network security appliances** all use the hosts file as a first-priority DNS override. It takes effect before any DNS query is sent. This is also how many security researchers demonstrate DNS spoofing and how endpoint security tools block known malicious domains at the OS level.

---

## Network Interfaces

![Network Interfaces](../Screenshot_6.png)

Lists all network interfaces on the remote machine.

### Capabilities

- Interface name, IPv4, IPv6, MAC address, MTU, Status (Up/Down)
- **Refresh** on demand
- Pure Java — uses `java.net.NetworkInterface` — **no native dependencies, always works**

### What You Learn

!!! info "Industry Comparison"
    This is how **enterprise asset management tools** (ManageEngine, SCCM, Lansweeper), **vulnerability scanners** (Nessus, Qualys), and **network topology mappers** enumerate remote network adapters — essential for understanding which interfaces are active and what addresses a device holds.
