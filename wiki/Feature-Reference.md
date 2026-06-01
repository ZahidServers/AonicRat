# Feature Reference

Quick reference for every tab, sub-tab, and button in AonicRAT.

---

## File System

| Button / Action | What it does |
|----------------|-------------|
| Double-click folder | Navigate into folder |
| Double-click `..` | Go up one directory level |
| Back / Forward | Browser-style navigation |
| Upload | Send file from operator machine to remote |
| Download | Retrieve file from remote to operator machine |
| Delete | Delete selected file/folder on remote |
| Rename | Rename selected file/folder on remote |
| Zip | Compress selected item on remote |
| Extract | Decompress ZIP on remote |
| View | Open file in built-in text viewer |
| Edit | Open file for editing, save back to remote |

---

## Command Line

| Element | Description |
|---------|-------------|
| Device selector | Choose which connected device to shell into |
| Connect | Start a persistent shell session (`cmd.exe` / `bash`) |
| Disconnect | End the shell session |
| Terminal input | Type commands and press Enter to send |
| Terminal output | Real-time shell output, color-coded |

---

## GUI Remote

| Button | What it does |
|--------|-------------|
| Connect | Start screen streaming from remote device |
| Disconnect | Stop screen stream |
| Ctrl+Alt+Del | Send secure attention sequence to remote |
| Screenshot | Save current remote screen as image |
| FPS slider | Control frame rate (higher = smoother, more bandwidth) |
| Quality | Control JPEG compression (higher = clearer, more bandwidth) |

---

## Network Controls

### Packets
| Button / Field | What it does |
|---------------|-------------|
| Filter (BPF) | BPF filter expression (e.g. `tcp port 80`) |
| Protocol dropdown | Filter displayed rows by protocol |
| ▶ Start | Begin packet capture on remote device |
| ■ Stop | Stop capture |
| Clear | Clear all rows from the packet table |

### Hosts Editor
| Button | What it does |
|--------|-------------|
| Reload | Fetch hosts file from remote device |
| Add | Add a new IP→hostname entry |
| Delete | Remove selected entry |
| Save to Remote | Write current table back to remote hosts file |
| Quick Redirect | Add a hostname→IP redirect in one click |

### Interfaces
| Button | What it does |
|--------|-------------|
| Refresh | Re-fetch interface list from remote device |

---

## Client Generator

| Field / Button | What it does |
|---------------|-------------|
| Windows / Linux / macOS checkboxes | Select target platform(s) |
| App Name | Name embedded in the generated executable |
| Version | Version string in PE metadata (Windows) |
| Company | Company string in PE metadata (Windows) |
| Description | Description in PE metadata (Windows) |
| Host / IP | Controller IP the agent will connect back to |
| Port | Controller port |
| Source JAR | Browse to `SimpleServer-1.0.0-beta.jar` |
| Icon (optional) | `.ico` (Windows) or `.icns` (macOS) |
| Output folder | Where generated files are saved |
| Generate | Build all selected platform agents |

---

## Others

### System Info
Displays OS, CPU, RAM, username, hostname, Java version — no buttons, read-only.

### Process Manager
| Button | What it does |
|--------|-------------|
| Refresh | Reload process list from remote |
| Kill Process | Terminate selected process on remote |

### Clipboard
| Button | What it does |
|--------|-------------|
| Get Clipboard | Fetch remote clipboard content |
| Set Clipboard | Push text to remote clipboard |

### Keylogger
| Button | What it does |
|--------|-------------|
| Start | Begin keystroke capture on remote |
| Stop | Stop keystroke capture |
| Clear | Clear displayed keylog |

---

## Advanced

### Power Controls
| Button | What it does |
|--------|-------------|
| Shutdown | Shut down the remote machine immediately |
| Restart | Reboot the remote machine |
| Sleep | Put remote machine to sleep/suspend |
| Lock Screen | Lock remote screen (requires re-login) |
| Log Off | Log off current user session |

### Startup Manager
| Button | What it does |
|--------|-------------|
| Add Entry | Add a new startup entry on remote |
| Remove Entry | Delete selected startup entry on remote |

### Message Box
| Field / Button | What it does |
|---------------|-------------|
| Title | Dialog title text |
| Message | Dialog body text |
| Type | Information / Warning / Error / Question |
| Send Message Box | Deliver pop-up to remote machine's screen |

---

## Settings

| Button | What it does |
|--------|-------------|
| Set Port Number | Configure the controller's listening port |
| Start Server | Begin accepting agent connections |
| Stop Server | Stop server and disconnect all agents |
