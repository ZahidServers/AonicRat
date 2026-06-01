# Client Generator

Generates platform-specific agent executables from the SimpleServer JAR.

## Steps

1. Go to **Client Generator** tab
2. Select target platform(s): Windows / Linux / macOS
3. Fill in App Info (name, version, company, description)
4. Set **Host/IP** and **Port** to your controller's address
5. Browse to `SimpleServer/target/SimpleServer-1.0.0-beta.jar` as Source JAR
6. (Optional) Add a custom icon
7. Set output folder
8. Click **Generate**

## Platform Details

### Windows (.exe)
- Uses **Launch4j** (auto-downloaded on first use, ~3 MB from SourceForge)
- Full PE metadata: name, version, company, description, copyright, icon
- Requires Java 11+ on the target (shows download prompt if missing)
- `gui` header type — no console window

### Linux (binary script)
- Self-extracting shell script
- Agent JAR embedded as Base64 inside the script
- Extracts to temp file and launches with `nohup java -jar`
- Requires only `java` and `base64` on the target
- No build tools needed

### macOS (.app.zip)
- Full `.app` bundle: `Contents/MacOS/`, `Contents/Java/`, `Contents/Resources/`, `Info.plist`
- Zipped for transfer
- On target: unzip, run `xattr -rd com.apple.quarantine AppName.app`, then `open AppName.app`

## Config Injection

At generation time, the controller:
1. Reads the SimpleServer JAR as a ZIP
2. Removes existing `aon.properties`
3. Writes new `aon.properties` with `host=<ip>` and `port=<port>`
4. Repackages the JAR

The agent reads these values on startup to know where to connect.

## Launch4j Location

Launch4j is cached at:
```
~/.aonicrat/tools/launch4j/
```
It is only downloaded once. Subsequent generations reuse the cached copy.
