# Keylogger

![Keylogger](../Screenshot_11.png)

A remote keylogger that captures keystrokes on the target machine.

---

## Capabilities

- **Start** — begins capturing keystrokes on the remote machine
- **Stop** — stops keystroke capture
- **Clear** — clears the captured output
- Live keystroke stream displayed in the controller

---

## Platform Implementation

| OS | API Used | Requirement |
|----|----------|-------------|
| **Windows** | `GetAsyncKeyState` (Win32 API) | Admin recommended |
| **Linux** | `/dev/input/eventN` raw input device | Root required |
| **macOS** | Accessibility API (`CGEventTap`) | Accessibility permission required |

---

## What You Learn

!!! info "Industry Comparison"
    Understanding how keyloggers are implemented at the OS API level is **essential for defenders**. EDR (Endpoint Detection & Response) products like CrowdStrike Falcon and SentinelOne detect keylogger behavior by monitoring exactly these API calls (`GetAsyncKeyState` hooking on Windows, `/dev/input` access on Linux). Studying how keyloggers work is the foundation for understanding how to detect and block them.

!!! warning "Ethical Use"
    Only use this feature on systems you own or have explicit written permission to monitor. Unauthorized keylogging is illegal in virtually every jurisdiction.
