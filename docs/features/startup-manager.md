# Startup Manager

![Startup Manager](../Screenshot_13.png)

View and manage programs that automatically start on the remote device.

---

## Capabilities

- Lists all startup entries: Name, Command, Type
- **Add Entry** — add a new startup entry remotely
- **Remove Entry** — delete a startup entry remotely

---

## Startup Entry Locations by Platform

=== "Windows"
    | Type | Location |
    |------|----------|
    | Registry (User) | `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` |
    | Registry (System) | `HKLM\Software\Microsoft\Windows\CurrentVersion\Run` |
    | Startup Folder (User) | `%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup` |
    | Startup Folder (All Users) | `%PROGRAMDATA%\Microsoft\Windows\Start Menu\Programs\Startup` |

=== "Linux"
    | Type | Location |
    |------|----------|
    | User autostart | `~/.config/autostart/*.desktop` |
    | Cron (reboot) | `@reboot` entry in crontab |
    | Systemd user service | `~/.config/systemd/user/` |

=== "macOS"
    | Type | Location |
    |------|----------|
    | Launch Agents (User) | `~/Library/LaunchAgents/*.plist` |
    | Launch Agents (System) | `/Library/LaunchAgents/*.plist` |
    | Login Items | System Preferences / System Settings |

---

## What You Learn

!!! info "Industry Comparison"
    Startup management is a **critical concept in both IT administration and malware analysis**. Virtually all persistent malware achieves persistence by writing to one of these startup locations. Enterprise tools like **Autoruns** (Sysinternals), **ManageEngine**, and **CrowdStrike Falcon** audit these exact locations to detect unauthorized persistence. Understanding where programs register themselves to start automatically is the foundation for both IT administration and endpoint security.
