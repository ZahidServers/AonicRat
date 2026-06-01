# Power Controls

![Power Controls](../Screenshot_12.png)

Remote power management — control the power state of the target device.

---

## Capabilities

| Button | Action |
|--------|--------|
| **Shutdown** | Immediately shuts down the remote machine |
| **Restart** | Reboots the remote machine |
| **Sleep** | Puts the remote machine into sleep/suspend |
| **Lock Screen** | Locks the screen — requires re-authentication to resume |
| **Log Off** | Logs off the current user session |

---

## Platform Commands

=== "Windows"
    | Action | Command |
    |--------|---------|
    | Shutdown | `shutdown /s /t 0` |
    | Restart | `shutdown /r /t 0` |
    | Sleep | `rundll32.exe powrprof.dll,SetSuspendState 0,1,0` |
    | Lock Screen | `rundll32.exe user32.dll,LockWorkStation` |
    | Log Off | `shutdown /l` |

=== "Linux"
    | Action | Command |
    |--------|---------|
    | Shutdown | `shutdown -h now` / `poweroff` |
    | Restart | `reboot` |
    | Sleep | `systemctl suspend` |
    | Lock Screen | `loginctl lock-session` |
    | Log Off | `pkill -KILL -u $USER` |

=== "macOS"
    | Action | Command |
    |--------|---------|
    | Shutdown | `osascript -e 'tell app "System Events" to shut down'` |
    | Restart | `osascript -e 'tell app "System Events" to restart'` |
    | Sleep | `pmset sleepnow` |
    | Lock Screen | `osascript -e 'tell app "System Events" to keystroke "q" using {control down, command down}'` |
    | Log Off | `osascript -e 'tell app "System Events" to log out'` |

---

## What You Learn

!!! info "Industry Comparison"
    Enterprise IT management platforms (**ManageEngine**, **Microsoft SCCM/Intune**, **PDQ Deploy**) and remote admin tools all implement remote power management using exactly these OS-level commands. Understanding which API or shell command controls each power state on each OS is fundamental knowledge for IT administrators and security researchers.
