# Client Generator

![Client Generator](../Screenshot_7.png)

Generates platform-specific agent executables from the SimpleServer JAR.

---

## Capabilities

- **Target Platform:** Windows (`.exe`), Linux (self-extracting binary), macOS (`.app.zip`)
- **App Info:** Set agent name, version, company, description (embedded in Windows PE metadata)
- **Server Connection:** Set controller Host/IP and Port — injected into the agent at build time
- **Source JAR:** The SimpleServer fat JAR as the base
- **Icon:** Optional custom icon (`.ico` for Windows, `.icns` for macOS)
- **Output Folder:** Choose where generated files are saved
- **Build Log:** Real-time log of each generation step
- **Progress bar:** Visual generation progress

---

## How Each Platform is Generated

=== "Windows (.exe)"
    Uses **Launch4j** (auto-downloaded from SourceForge) to wrap the JAR into a native Windows executable:

    - Full PE metadata: file version, product version, description, company, copyright
    - Embeds a custom icon
    - Minimum JRE version enforcement (Java 11+)
    - Silent GUI mode (no console window)

=== "Linux (binary script)"
    Creates a self-extracting shell script:

    - The agent JAR is Base64-encoded and embedded inside the script
    - On execution, decodes the JAR to a temp file and launches it with `nohup java -jar`
    - No build tools needed on the target — only `java` and `base64`
    - Sets executable bit automatically

=== "macOS (.app.zip)"
    Creates a proper macOS application bundle:

    - `Contents/MacOS/` — launcher shell script
    - `Contents/Java/` — the agent JAR
    - `Contents/Info.plist` — bundle metadata (name, version, bundle ID, icon)
    - `Contents/Resources/` — optional `.icns` icon
    - Zipped into `.app.zip` for transfer

---

## Config Injection

The controller IP and port are **not hardcoded** in the agent source. At generation time, the Client Generator:

1. Opens the SimpleServer JAR as a ZIP
2. Removes any existing `aon.properties`
3. Writes a new `aon.properties` with `host=` and `port=`
4. Repackages the JAR

The agent reads these values at startup to know where to connect.

---

## What You Learn

!!! info "Industry Comparison"
    This is exactly how **`msfvenom`** (Metasploit payload generator), **Cobalt Strike's artifact kit**, and **enterprise MDM agent deployment tools** work — taking a base agent, injecting environment-specific configuration (server address, port, certificates), and packaging it for each target platform. Launch4j is the same tool used by many legitimate Java applications to produce Windows executables.
