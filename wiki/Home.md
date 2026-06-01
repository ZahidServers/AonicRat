# AonicRAT Wiki

Welcome to the AonicRAT wiki — quick-reference pages for setup, features, and troubleshooting.

**Created by:** Mohammed Zahid Imtiyaz Wadiwale  
**Version:** 1.0.0-beta  
**Purpose:** Educational — Understanding Enterprise-Grade Remote Administration Tools

---

## Pages

| Page | Description |
|------|-------------|
| [Home](Home) | This page |
| [Installation](Installation) | How to build and run both components |
| [Dependencies](Dependencies) | All runtime and build dependencies |
| [Feature Reference](Feature-Reference) | Quick reference for every tab and button |
| [Network Controls & Pcap](Network-Controls-Pcap) | Packet capture setup, Npcap, libpcap |
| [Client Generator](Client-Generator) | How to build agents for each platform |
| [Troubleshooting](Troubleshooting) | Common problems and fixes |
| [Architecture](Architecture) | How the controller and agent communicate |
| [Legal](Legal) | License, attribution, ethical use |

---

## Quick Start

```bash
# Controller
cd AonicRat && mvn clean package
java -jar target/AonicRat-1.0.0-beta.jar

# Agent
cd AonicRat/SimpleServer && mvn clean package
```

1. Launch controller → **Settings** → set port → **Start Server**
2. **Client Generator** → set IP/port → **Generate**
3. Deploy generated agent to target → run it
4. Device appears in all tabs
