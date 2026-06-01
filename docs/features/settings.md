# Settings

![Settings](../Screenshot_15.png)

Controller server configuration — manage the listening port and server lifecycle.

---

## Capabilities

- **Set Port Number** — configure which TCP port the controller listens on for agent connections (default: `5555`)
- **Start Server** — bind to the port and begin accepting incoming agent connections
- **Stop Server** — shut down the server and disconnect all connected agents

---

## How It Works

1. Click **Start Server** — the controller opens a `ServerSocket` on the configured port
2. Each agent that connects is accepted as a new thread/connection
3. The device appears in the dropdown across all tabs
4. Click **Stop Server** to close the socket and cleanly disconnect all agents

---

## What You Learn

!!! info "Industry Comparison"
    This demonstrates how **C2 (Command and Control) servers** and **enterprise management servers** bind to ports, accept agent connections, and manage their lifecycle. Every remote admin tool from **SSH servers** to **Cobalt Strike team servers** to **ManageEngine agents** uses this same pattern: a server socket waiting for incoming agent connections.
