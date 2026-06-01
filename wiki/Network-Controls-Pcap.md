# Network Controls & Packet Capture

## Overview

Packet capture uses **Pcap4J** — a pure-Java wrapper around the native packet capture libraries (**Npcap** on Windows, **libpcap** on Linux/macOS).

---

## Windows — Npcap Auto-Install

On first capture on a Windows target:

1. Agent detects Npcap is not installed
2. Downloads Npcap installer silently
3. Installs it (~60 seconds)
4. Status bar shows: `Downloading Npcap…` → `Installing Npcap…`
5. **Restart the agent** on the target after install
6. Start capture again — it will work

If the agent is not running as Administrator, the Npcap install will fail. Run the agent as Administrator on Windows for packet capture.

---

## Linux — libpcap Manual Install

```bash
# Debian / Ubuntu
sudo apt install libpcap-dev

# RHEL / CentOS / Fedora
sudo yum install libpcap-devel
# or
sudo dnf install libpcap-devel

# Arch Linux
sudo pacman -S libpcap
```

The agent must also run as root for raw socket access:
```bash
sudo java -jar SimpleServer-1.0.0-beta.jar
```

---

## macOS — libpcap

libpcap is pre-installed on macOS. If missing:
```bash
brew install libpcap
```

The agent requires root or Accessibility permissions:
```bash
sudo java -jar SimpleServer-1.0.0-beta.jar
```

---

## BPF Filter Syntax

The filter field accepts standard **Berkeley Packet Filter (BPF)** expressions — the same syntax used by Wireshark and tcpdump.

| Example Filter | What it captures |
|---------------|-----------------|
| *(empty)* | All traffic |
| `tcp` | TCP packets only |
| `udp` | UDP packets only |
| `port 80` | HTTP traffic |
| `port 443` | HTTPS traffic |
| `host 192.168.1.1` | Traffic to/from specific host |
| `tcp port 22` | SSH traffic |
| `not port 5555` | Everything except the AonicRat port |
| `src host 10.0.0.1` | Traffic from a specific source |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Capture hangs on Windows | Npcap is installing. Wait ~60s then restart agent. |
| "Permission denied" on Linux | Run agent as root: `sudo java -jar ...` |
| No packets appear | Check BPF filter — try clearing it (capture all). |
| "libpcap not found" on Linux | Install: `sudo apt install libpcap-dev` |
| Capture works but no data | Check the network interface — traffic may be on a different interface. |
