# Troubleshooting

## Controller Won't Start

| Problem | Solution |
|---------|----------|
| `java.lang.UnsupportedClassVersionError` | Java version too old. Controller needs Java 18+. Run `java -version` to check. |
| Port already in use | Another process is using port 5555. Change the port in Settings, or kill the conflicting process. |
| Build fails — no JAR in `target/` | Run `mvn clean package` and check the Maven output for errors. |

---

## Agent Won't Connect

| Problem | Solution |
|---------|----------|
| Agent runs but nothing appears in controller | Check the IP and port embedded in the agent match the controller's Settings port. Check firewall allows inbound on that port. |
| "Connection refused" | Controller server is not started. Go to Settings → Start Server first. |
| Agent connects then immediately drops | Check Java version on target. Agent requires Java 11+. |
| Agent on different network can't connect | Use the controller machine's **public IP** or set up port forwarding on the router. |

---

## Packet Capture

| Problem | Solution |
|---------|----------|
| Capture hangs on Windows | Npcap is being installed silently. Wait ~60 seconds, then **restart the agent** and try again. |
| "Permission denied" / capture fails on Linux | Agent must run as root: `sudo java -jar SimpleServer-1.0.0-beta.jar` |
| "libpcap not found" on Linux | `sudo apt install libpcap-dev` (Debian/Ubuntu) or `sudo yum install libpcap-devel` (RHEL) |
| No packets appear (capture running) | Clear the BPF filter field and capture all. Also check the correct network interface is active. |
| Capture works but misses traffic | The BPF filter may be too restrictive. Try leaving the filter blank. |

---

## File System

| Problem | Solution |
|---------|----------|
| Can't list files | Ensure agent has read permission on that directory. Some system folders require admin. |
| Upload/download stalls | Large files take time. Check the progress bar. Don't disconnect during transfer. |
| Can't delete / rename | Agent may not have write permission on that path. |

---

## Hosts Editor

| Problem | Solution |
|---------|----------|
| Save fails with permission error | Agent must run as admin (Windows) or root (Linux/macOS) to write the hosts file. |
| Changes don't take effect | Flush DNS cache: `ipconfig /flushdns` (Windows), `systemd-resolve --flush-caches` (Linux). |
| HTTPS site still shows wrong content | Certificate mismatch — hosts redirect works but TLS cert won't match. Expected behavior. |

---

## Keylogger

| Problem | Solution |
|---------|----------|
| No keystrokes captured on Linux | Agent must run as root for `/dev/input` access. |
| No keystrokes on macOS | Grant Accessibility permission to the Java process in System Settings → Privacy & Security → Accessibility. |
| Keylogger starts but output is empty | `GetAsyncKeyState` (Windows) only captures keys while the JVM has focus in some configurations. Run agent as admin. |

---

## Client Generator

| Problem | Solution |
|---------|----------|
| Launch4j download fails | Check internet connection. Launch4j is downloaded from SourceForge. Try again or manually place Launch4j in `~/.aonicrat/tools/launch4j/`. |
| Generated `.exe` won't run on target | Target needs Java 11+. The generated EXE shows a download prompt if Java is missing. |
| macOS `.app` shows "damaged" warning | Run: `xattr -rd com.apple.quarantine YourApp.app` |
| Linux script won't execute | `chmod +x agent-name && ./agent-name`. Needs `java` and `base64` on target. |

---

## General

| Problem | Solution |
|---------|----------|
| NullPointerException on startup | Known fix applied — `TerminalPanel` field shadowing. Make sure you have the latest code. |
| UI freezes | Never run long operations on the EDT. All network operations are off-thread. If frozen, check for an unhandled exception in the output console. |
| Device shows in list but commands fail | Device may have disconnected. Reload device list and reconnect. |
