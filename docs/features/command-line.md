# Command Line (Terminal)

![Command Line](../Screenshot_2.png)

A fully interactive persistent remote shell — the remote machine's terminal streamed live to the operator.

---

## Capabilities

- Opens a **persistent interactive shell** on the remote device
    - Windows → `cmd.exe`
    - Linux / macOS → `bash` or `sh`
- Streams **real-time output** back to the controller as it is produced
- Fully interactive — run commands, navigate directories, execute scripts, launch programs
- **Session-based** — the shell process stays alive between commands (not one-shot execution)
- Color-coded terminal output with a dark console theme
- Device selector to switch between multiple connected devices

---

## How It Works

1. Operator selects a device and clicks **Connect**
2. The controller sends a `START_SHELL` command
3. The agent spawns a shell process (`ProcessBuilder`) and keeps it alive
4. The agent streams `stdout` and `stderr` back to the controller in real time
5. Every line the operator types is sent as `SHELL_INPUT` to the agent's `stdin`
6. The shell session persists until explicitly disconnected

---

## What You Learn

!!! info "Industry Comparison"
    This is the same model used by **SSH** (Secure Shell), **Telnet**, **Cobalt Strike's interactive shell**, and **Metasploit Meterpreter's shell command**. The key difference between a **one-shot command executor** (run command → get output → done) and a **persistent PTY session** (shell stays alive, maintains state like current directory and environment variables) is a fundamental concept in remote administration.
