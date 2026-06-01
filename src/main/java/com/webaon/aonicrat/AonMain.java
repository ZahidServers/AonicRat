package com.webaon.aonicrat;

import com.webaon.aonicrat.ServerManager.DownloadProgressListener;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */

/**
 *
 * @author Zahid Wadiwale
 */
public class AonMain extends javax.swing.JFrame {
    private final ServerManager serverManager;
    private RemoteDevice currentDevice;
    private RemoteDevice currentCliDevice;
    private String currentShellSessionId;
    private TerminalPanel terminalPanel;
    private boolean pendingReadIsEdit = false;
    // GUI Remote tab
    private RemoteDevice currentGuiDevice;
    private String currentScreenSessionId;
    // At top of your class
    private final Deque<String> backStack = new ArrayDeque<>();
    private final Deque<String> forwardStack = new ArrayDeque<>();
    /**
     * Creates new form AonMain
     */
    public AonMain() {
        initComponents();
        setLocationRelativeTo(null);
        serverManager = new ServerManager();
        serverManager.setRootsListener((device, roots) -> {
            // e.g. fill a combo box of drives:
            if (roots.length > 0) {
                RemotePath_FS.setText(roots[0]);  // show the first root as default
            }
        });
        serverManager.setFilesListener((device, files) -> {
            // clear & refill the JTable
            DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
            model.setRowCount(0); // clear old rows
            String currentPath250 = RemotePath_FS.getText();
            String parent250 = new File(currentPath250).getParent();
            if (parent250 != null) {
                // Add ".." row at the top to go to parent
                model.addRow(new Object[]{"..", "", "Folder", ""});
            }
            String SEP = "/\\$#\\*,\\*#\\$/";
            for (String f : files) {
                if (f == null || f.isBlank()) continue;
                if (f.startsWith("ERROR")) {
                    JOptionPane.showMessageDialog(this, f, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String[] parts = f.split(SEP);
                if (parts.length < 3) continue;
                String rawName = parts[0];
                long size = Long.parseLong(parts[1]);
                long modified = Long.parseLong(parts[2]);

                boolean isDir = rawName.endsWith("/");
                String name = isDir ? rawName.substring(0, rawName.length() - 1) : rawName;

                String type = isDir ? "Folder" : "File";

                String sizeStr = isDir ? "" : humanReadableByteCount(size);
                String modifiedStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                         .format(new java.util.Date(modified));

                model.addRow(new Object[]{name, sizeStr, type, modifiedStr});
            }
        });
        serverManager.setUploadProgressListener((device, fileName, bytesSent, totalBytes) -> {
            int percent = (int)((bytesSent * 100) / totalBytes);
            status_progress_bar.setValue(percent);
            status_label.setText("Uploading " + fileName + " " + percent + "%");
        });
        serverManager.setDownloadProgressListener(new DownloadProgressListener() {
            @Override
            public void onProgress(RemoteDevice device, String fileName, long current, long total) {
                int percent = (int) ((current * 100) / total);
                status_progress_bar.setValue(percent);
                status_label.setText("Downloading " + fileName + " (" + percent + "%)");
            }

            @Override
            public void onComplete(RemoteDevice device, String fileName) {
                status_progress_bar.setValue(100);
                status_label.setText("Download complete: " + fileName);
            }
        });
        jTable1.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                // Detect double-click
                if (evt.getClickCount() == 2 && jTable1.getSelectedRow() != -1) {
                    int row = jTable1.getSelectedRow();
                    String type = jTable1.getValueAt(row, 2).toString(); // your "Type" column
                    if ("Folder".equalsIgnoreCase(type)) {
                        String name = jTable1.getValueAt(row, 0).toString(); // your "Name" column

                        // build the new path
                        String currentPath = RemotePath_FS.getText();
                        String newPath=null;
                        if ("..".equals(name)) {
                            String parent = new File(currentPath).getParent();
                            if (parent != null) {
                                newPath =parent;
                            }
                        }  else if (currentPath.endsWith("/") || currentPath.endsWith("\\")) {
                            newPath = currentPath + name;
                        } else {
                            // use slash or backslash depending on your remote OS
                            newPath = currentPath + "/" + name;
                        }

                        // send list request for new folder
                        if (currentDevice != null) {
                            navigateTo(newPath);
                        }
                    }
                }
            }
        });
        // after you have created RemotePath_FS and Go_BTN_FS
        RemotePath_FS.addActionListener(e -> {
            Go_BTN_FSActionPerformed(null);
        });

        // --- Command result listener (delete, rename, zip, extract) ---
        serverManager.setCommandResultListener((device, cmd, success, path, details) -> {
            if (success) {
                status_label.setText(cmd + " successful: " + new File(path).getName());
                if (currentDevice != null) {
                    serverManager.sendListRequest(currentDevice, RemotePath_FS.getText());
                }
            } else {
                JOptionPane.showMessageDialog(this, "Operation failed: " + details, "Error", JOptionPane.ERROR_MESSAGE);
                status_label.setText(cmd + " failed");
            }
        });

        // --- Persistent shell stream listener ---
        serverManager.setShellStreamListener(new ServerManager.ShellStreamListener() {
            @Override
            public void onStarted(RemoteDevice device, String sessionId) {
                // shell prints its own banner/prompt automatically
            }
            @Override
            public void onOutput(RemoteDevice device, String sessionId, String data) {
                terminalPanel.appendOutput(data);
            }
            @Override
            public void onEnded(RemoteDevice device, String sessionId) {
                terminalPanel.printInfo("\n[Shell session ended]\n");
                currentShellSessionId = null;
            }
            @Override
            public void onError(RemoteDevice device, String sessionId, String error) {
                terminalPanel.printInfo("\n[Shell error: " + error + "]\n");
                currentShellSessionId = null;
            }
        });

        // --- Find result listener ---
        serverManager.setFindResultListener((device, query, files) -> {
            DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
            model.setRowCount(0);
            if (files.length == 0) {
                status_label.setText("No results found for: " + query);
            } else {
                for (String f : files) {
                    model.addRow(new Object[]{f, "", "File", ""});
                }
                status_label.setText("Found " + files.length + " result(s) for: " + query);
            }
        });

        // --- File content listener (view / edit) ---
        serverManager.setFileContentListener(new ServerManager.FileContentListener() {
            @Override
            public void onContent(RemoteDevice device, String path, byte[] content) {
                String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                if (pendingReadIsEdit) {
                    String fileName = new File(path).getName();
                    String parentPath = new File(path).getParent();
                    new FileViewerWindow(path, text, true, (p, newContent) -> {
                        try {
                            java.io.File tmpDir = new java.io.File(
                                System.getProperty("java.io.tmpdir"),
                                "aonicrat_" + java.util.UUID.randomUUID());
                            tmpDir.mkdirs();
                            java.io.File tmpFile = new java.io.File(tmpDir, fileName);
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpFile)) {
                                fos.write(newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            serverManager.uploadFileOrFolder(currentDevice, parentPath, tmpFile);
                            status_label.setText("Saved: " + fileName);
                        } catch (java.io.IOException ex) {
                            JOptionPane.showMessageDialog(AonMain.this,
                                "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } else {
                    new FileViewerWindow(path, text);
                }
                status_label.setText("Loaded: " + new File(path).getName());
            }

            @Override
            public void onError(RemoteDevice device, String path, String error) {
                JOptionPane.showMessageDialog(AonMain.this,
                    "Cannot read file: " + error, "Error", JOptionPane.ERROR_MESSAGE);
                status_label.setText("Read failed");
            }
        });

        // --- Terminal command sender (routes to the live persistent shell session) ---
        terminalPanel.setCommandSender(command -> {
            if (currentCliDevice != null && currentShellSessionId != null) {
                serverManager.sendShellInput(currentCliDevice, currentShellSessionId, command);
            } else {
                terminalPanel.appendOutput("[No shell session — connect a device in the CLI tab first]\n");
            }
        });

        // --- CLI tab buttons ---
        load_more_devices_btn_cli.addActionListener(evt -> {
            String selectedOs = (String) OS_Type_for_CommandLine.getSelectedItem();
            List<RemoteDevice> devices = serverManager.getDevicesByOsType(selectedOs);
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (RemoteDevice d : devices) model.addElement(d.toString());
            DeviceSelector_CLI.setModel(model);
        });

        connect_btn_cli.addActionListener(evt -> {
            String selected = (String) DeviceSelector_CLI.getSelectedItem();
            if (selected == null || selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No device selected. Click 'Load More Devices' first.");
                return;
            }
            // stop any existing session first
            if (currentCliDevice != null && currentShellSessionId != null) {
                serverManager.stopShellSession(currentCliDevice);
                currentShellSessionId = null;
            }
            currentCliDevice = serverManager.getConnectedDevices().stream()
                .filter(d -> d.toString().equals(selected))
                .findFirst().orElse(null);
            if (currentCliDevice == null) {
                JOptionPane.showMessageDialog(this, "Device not found. Please reload device list.");
                return;
            }
            // launch persistent shell on the remote agent
            currentShellSessionId = serverManager.startShellSession(currentCliDevice);
            terminalPanel.printInfo("[Connecting to " + currentCliDevice.pcName + "...]\n");
        });

        Disconnect_btn_cli.addActionListener(evt -> {
            if (currentCliDevice != null && currentShellSessionId != null) {
                serverManager.stopShellSession(currentCliDevice);
                currentShellSessionId = null;
            }
            currentCliDevice = null;
            terminalPanel.printInfo("[Shell disconnected]\n");
        });

        // ── Network Controls tab ──────────────────────────────────────────────
        NetworkControls_Tab.removeAll();
        NetworkControls_Tab.setLayout(new java.awt.BorderLayout());
        NetworkControls_Tab.add(new NetworkControlPanel(serverManager),
                                java.awt.BorderLayout.CENTER);

        // ── Others tab ────────────────────────────────────────────────────────
        Others_Tab.removeAll();
        Others_Tab.setLayout(new java.awt.BorderLayout());
        Others_Tab.add(new OthersPanel(serverManager), java.awt.BorderLayout.CENTER);

        // ── Advanced tab ──────────────────────────────────────────────────────
        Advanced_Tab.removeAll();
        Advanced_Tab.setLayout(new java.awt.BorderLayout());
        Advanced_Tab.add(new AdvancedPanel(serverManager), java.awt.BorderLayout.CENTER);

        // ── Client Generator tab ──────────────────────────────────────────────
        ClientGenerator_Tab.removeAll();
        ClientGenerator_Tab.setLayout(new java.awt.BorderLayout());
        ClientGenerator_Tab.add(new ClientGeneratorPanel(), java.awt.BorderLayout.CENTER);

        // ── GUI Remote tab ────────────────────────────────────────────────────
        setupGuiRemoteTab();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GUI Remote tab – built programmatically (the generated tab is empty)
    // ─────────────────────────────────────────────────────────────────────────
    private void setupGuiRemoteTab() {

        GUIRemote_Tab.removeAll();
        GUIRemote_Tab.setLayout(new java.awt.BorderLayout());

        // ── top control bar ───────────────────────────────────────────────────
        javax.swing.JPanel topBar = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 5));
        topBar.setBackground(new java.awt.Color(102, 204, 255));

        javax.swing.JLabel guiOsLabel = new javax.swing.JLabel("OS Type:");
        javax.swing.JComboBox<String> guiOsCombo = new javax.swing.JComboBox<>(
                new String[]{"Windows", "Mac OS", "Linux", "Android", "iOS"});

        javax.swing.JLabel guiDeviceLabel = new javax.swing.JLabel("Device:");
        javax.swing.JComboBox<String> guiDeviceCombo = new javax.swing.JComboBox<>();
        guiDeviceCombo.setPreferredSize(new java.awt.Dimension(220, 22));

        javax.swing.JButton guiLoadBtn  = new javax.swing.JButton("Load More Devices");
        javax.swing.JButton guiConnBtn  = new javax.swing.JButton("Connect");
        javax.swing.JButton guiDiscBtn  = new javax.swing.JButton("Disconnect");

        javax.swing.JSeparator sep1 = new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL);
        sep1.setPreferredSize(new java.awt.Dimension(2, 24));

        javax.swing.JLabel qualLabel = new javax.swing.JLabel("Quality:");
        javax.swing.JSlider qualSlider = new javax.swing.JSlider(10, 90, 60);
        qualSlider.setPreferredSize(new java.awt.Dimension(110, 22));
        qualSlider.setMajorTickSpacing(20);
        qualSlider.setPaintTicks(true);
        qualSlider.setOpaque(false);

        javax.swing.JLabel fpsLabel = new javax.swing.JLabel("FPS:");
        javax.swing.JComboBox<Integer> fpsCombo = new javax.swing.JComboBox<>(
                new Integer[]{5, 10, 15, 20, 25, 30});
        fpsCombo.setSelectedItem(10);

        topBar.add(guiOsLabel); topBar.add(guiOsCombo);
        topBar.add(guiDeviceLabel); topBar.add(guiDeviceCombo);
        topBar.add(guiLoadBtn); topBar.add(guiConnBtn); topBar.add(guiDiscBtn);
        topBar.add(sep1);
        topBar.add(qualLabel); topBar.add(qualSlider);
        topBar.add(fpsLabel);  topBar.add(fpsCombo);

        // ── remote desktop display ─────────────────────────────────────────────
        RemoteDesktopPanel rdp = new RemoteDesktopPanel();

        // ── bottom status / special-key bar ───────────────────────────────────
        javax.swing.JPanel bottomBar = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4));
        bottomBar.setBackground(new java.awt.Color(240, 240, 240));

        javax.swing.JLabel guiStatus = new javax.swing.JLabel("Not connected");
        javax.swing.JButton cadBtn   = new javax.swing.JButton("Ctrl+Alt+Del");
        javax.swing.JButton ssBtn    = new javax.swing.JButton("Screenshot");
        javax.swing.JButton fullBtn  = new javax.swing.JButton("Fit / 1:1");

        bottomBar.add(guiStatus);
        bottomBar.add(new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL) {{
            setPreferredSize(new java.awt.Dimension(2, 20));
        }});
        bottomBar.add(cadBtn); bottomBar.add(ssBtn); bottomBar.add(fullBtn);

        GUIRemote_Tab.add(topBar,    java.awt.BorderLayout.NORTH);
        GUIRemote_Tab.add(rdp,       java.awt.BorderLayout.CENTER);
        GUIRemote_Tab.add(bottomBar, java.awt.BorderLayout.SOUTH);

        // ── screen-stream listener ─────────────────────────────────────────────
        serverManager.setScreenStreamListener(new ServerManager.ScreenStreamListener() {
            @Override
            public void onStarted(RemoteDevice device, String sessionId, int w, int h) {
                guiStatus.setText("Streaming  " + w + "×" + h
                        + "  from " + device.pcName);
            }
            @Override
            public void onFrame(RemoteDevice device, String sessionId,
                                java.awt.image.BufferedImage frame) {
                rdp.updateFrame(frame);
            }
            @Override
            public void onError(RemoteDevice device, String sessionId, String error) {
                JOptionPane.showMessageDialog(AonMain.this,
                        "Screen error: " + error, "Remote Desktop",
                        JOptionPane.ERROR_MESSAGE);
                guiStatus.setText("Error – " + error);
                currentScreenSessionId = null;
                currentGuiDevice = null;
            }
        });

        // ── forward mouse events to the remote agent ───────────────────────────
        rdp.setOnMouseMoved(c -> {
            if (currentGuiDevice != null)
                serverManager.sendMouseMove(currentGuiDevice, c[0], c[1]);
        });
        rdp.setOnMousePressed(c -> {
            if (currentGuiDevice != null)
                serverManager.sendMousePress(currentGuiDevice, c[0], c[1], c[2]);
        });
        rdp.setOnMouseReleased(c -> {
            if (currentGuiDevice != null)
                serverManager.sendMouseRelease(currentGuiDevice, c[0], c[1], c[2]);
        });
        rdp.setOnMouseScrolled(amount -> {
            if (currentGuiDevice != null)
                serverManager.sendMouseScroll(currentGuiDevice, amount);
        });

        // ── forward keyboard events to the remote agent ────────────────────────
        rdp.setOnKeyPressed(code -> {
            if (currentGuiDevice != null)
                serverManager.sendKeyPress(currentGuiDevice, code);
        });
        rdp.setOnKeyReleased(code -> {
            if (currentGuiDevice != null)
                serverManager.sendKeyRelease(currentGuiDevice, code);
        });

        // ── Load More Devices ──────────────────────────────────────────────────
        guiLoadBtn.addActionListener(e -> {
            String os = (String) guiOsCombo.getSelectedItem();
            List<RemoteDevice> devices = serverManager.getDevicesByOsType(os);
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            for (RemoteDevice d : devices) model.addElement(d.toString());
            guiDeviceCombo.setModel(model);
        });

        // ── Connect ────────────────────────────────────────────────────────────
        guiConnBtn.addActionListener(e -> {
            String selected = (String) guiDeviceCombo.getSelectedItem();
            if (selected == null || selected.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No device selected. Click 'Load More Devices' first.");
                return;
            }
            // stop previous session if any
            if (currentGuiDevice != null) {
                serverManager.stopScreenSession(currentGuiDevice);
                rdp.clearFrame();
            }
            currentGuiDevice = serverManager.getConnectedDevices().stream()
                    .filter(d -> d.toString().equals(selected))
                    .findFirst().orElse(null);
            if (currentGuiDevice == null) {
                JOptionPane.showMessageDialog(this, "Device not found. Reload the list.");
                return;
            }
            int quality = qualSlider.getValue();
            int fps     = (Integer) fpsCombo.getSelectedItem();
            currentScreenSessionId = serverManager.startScreenSession(
                    currentGuiDevice, quality, fps);
            guiStatus.setText("Connecting to " + currentGuiDevice.pcName + "…");
        });

        // ── Disconnect ─────────────────────────────────────────────────────────
        guiDiscBtn.addActionListener(e -> {
            if (currentGuiDevice != null) {
                serverManager.stopScreenSession(currentGuiDevice);
                currentGuiDevice = null;
                currentScreenSessionId = null;
            }
            rdp.clearFrame();
            guiStatus.setText("Not connected");
        });

        // ── Ctrl+Alt+Del ───────────────────────────────────────────────────────
        cadBtn.addActionListener(e -> {
            if (currentGuiDevice == null) return;
            serverManager.sendKeyPress  (currentGuiDevice, java.awt.event.KeyEvent.VK_CONTROL);
            serverManager.sendKeyPress  (currentGuiDevice, java.awt.event.KeyEvent.VK_ALT);
            serverManager.sendKeyPress  (currentGuiDevice, java.awt.event.KeyEvent.VK_DELETE);
            serverManager.sendKeyRelease(currentGuiDevice, java.awt.event.KeyEvent.VK_DELETE);
            serverManager.sendKeyRelease(currentGuiDevice, java.awt.event.KeyEvent.VK_ALT);
            serverManager.sendKeyRelease(currentGuiDevice, java.awt.event.KeyEvent.VK_CONTROL);
        });

        // ── Screenshot – save the current frame to disk ────────────────────────
        ssBtn.addActionListener(e -> {
            java.awt.image.BufferedImage snap = rdp.getCurrentFrame();
            if (snap == null) {
                JOptionPane.showMessageDialog(this, "No frame yet — connect first.");
                return;
            }
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("screenshot.png"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    javax.imageio.ImageIO.write(snap, "PNG", fc.getSelectedFile());
                    JOptionPane.showMessageDialog(this, "Saved: " + fc.getSelectedFile());
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // ── Fit / 1:1 toggle (future) ──────────────────────────────────────────
        fullBtn.addActionListener(e ->
            JOptionPane.showMessageDialog(this,
                    "Stretch-to-fit is always on. 1:1 zoom coming soon.")
        );
    }
    private String humanReadableByteCount(long bytes){
        int unit =1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    };
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jProgressBar1 = new javax.swing.JProgressBar();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        Tabs = new javax.swing.JTabbedPane();
        FileSystem_Tab = new javax.swing.JPanel();
        topBar_FS = new javax.swing.JPanel();
        remotePath_FS_label = new javax.swing.JLabel();
        RemotePath_FS = new javax.swing.JTextField();
        Go_BTN_FS = new javax.swing.JButton();
        Reload_BTN_FS = new javax.swing.JButton();
        Backward_BTN_FS = new javax.swing.JButton();
        Forward_BTN_FS = new javax.swing.JButton();
        Upload_BTN_FS = new javax.swing.JButton();
        Download_BTN_FS = new javax.swing.JButton();
        Delete_BTN_FS = new javax.swing.JButton();
        Rename_BTN_FS = new javax.swing.JButton();
        Extract_BTN_FS = new javax.swing.JButton();
        ZIP_BTN_FS = new javax.swing.JButton();
        View_File_BTN_FS = new javax.swing.JButton();
        Edit_File_BTN_FS = new javax.swing.JButton();
        Refresh_BTN_FS = new javax.swing.JButton();
        Reload_BTN_FS10 = new javax.swing.JButton();
        undo_BTN_FS = new javax.swing.JButton();
        Redo_BTN_FS = new javax.swing.JButton();
        Find_BTN_FS = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        OS_Type_FS_Label = new javax.swing.JLabel();
        OS_Type_for_FS = new javax.swing.JComboBox<>();
        device_FS_label = new javax.swing.JLabel();
        DeviceSelector_FS = new javax.swing.JComboBox<>();
        load_more_devices_btn_FS = new javax.swing.JButton();
        connect_btn_FS = new javax.swing.JButton();
        Disconnect_btn_FS = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        Status_shower = new javax.swing.JLabel();
        status_label = new javax.swing.JLabel();
        status_progress_bar = new javax.swing.JProgressBar();
        CommandLine_Tab = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        OS_Type_CLI_Label = new javax.swing.JLabel();
        OS_Type_for_CommandLine = new javax.swing.JComboBox<>();
        device_cli_label = new javax.swing.JLabel();
        DeviceSelector_CLI = new javax.swing.JComboBox<>();
        load_more_devices_btn_cli = new javax.swing.JButton();
        connect_btn_cli = new javax.swing.JButton();
        Disconnect_btn_cli = new javax.swing.JButton();
        GUIRemote_Tab = new javax.swing.JPanel();
        NetworkControls_Tab = new javax.swing.JPanel();
        ClientGenerator_Tab = new javax.swing.JPanel();
        Others_Tab = new javax.swing.JPanel();
        Advanced_Tab = new javax.swing.JPanel();
        Settings_Tab = new javax.swing.JPanel();
        ServerSettings_Label = new javax.swing.JLabel();
        jSeparator2 = new javax.swing.JSeparator();
        portTextField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        startServerButton = new javax.swing.JButton();
        stopServerButton = new javax.swing.JButton();
        About_Tab = new javax.swing.JPanel();
        Header = new javax.swing.JPanel();
        Header_Name = new javax.swing.JLabel();
        username_welcome = new javax.swing.JLabel();
        exitbtn = new javax.swing.JToggleButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(true);

        topBar_FS.setBackground(new java.awt.Color(102, 204, 255));

        remotePath_FS_label.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        remotePath_FS_label.setText("Remote Path:");

        RemotePath_FS.setText("C://");

        Go_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/go.png"))); // NOI18N
        Go_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Go_BTN_FSActionPerformed(evt);
            }
        });

        Reload_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/reload.png"))); // NOI18N
        Reload_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Reload_BTN_FSActionPerformed(evt);
            }
        });

        Backward_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/back.png"))); // NOI18N
        Backward_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Backward_BTN_FSActionPerformed(evt);
            }
        });

        Forward_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/forward.png"))); // NOI18N
        Forward_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Forward_BTN_FSActionPerformed(evt);
            }
        });

        Upload_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/upload.png"))); // NOI18N
        Upload_BTN_FS.setToolTipText("Upload");
        Upload_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Upload_BTN_FSActionPerformed(evt);
            }
        });

        Download_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/download.png"))); // NOI18N
        Download_BTN_FS.setToolTipText("Download");
        Download_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Download_BTN_FSActionPerformed(evt);
            }
        });

        Delete_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/delete.png"))); // NOI18N
        Delete_BTN_FS.setToolTipText("Delete");
        Delete_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Delete_BTN_FSActionPerformed(evt);
            }
        });

        Rename_BTN_FS.setBackground(new java.awt.Color(0, 0, 0));
        Rename_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/rename.png"))); // NOI18N
        Rename_BTN_FS.setToolTipText("Rename");
        Rename_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Rename_BTN_FSActionPerformed(evt);
            }
        });

        Extract_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/extract.png"))); // NOI18N
        Extract_BTN_FS.setToolTipText("Extract");
        Extract_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Extract_BTN_FSActionPerformed(evt);
            }
        });

        ZIP_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/zip.png"))); // NOI18N
        ZIP_BTN_FS.setToolTipText("Zip");
        ZIP_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ZIP_BTN_FSActionPerformed(evt);
            }
        });

        View_File_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/view-file.png"))); // NOI18N
        View_File_BTN_FS.setToolTipText("View File");
        View_File_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                View_File_BTN_FSActionPerformed(evt);
            }
        });

        Edit_File_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/edit-file.png"))); // NOI18N
        Edit_File_BTN_FS.setToolTipText("Edit File");
        Edit_File_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Edit_File_BTN_FSActionPerformed(evt);
            }
        });

        Refresh_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/refresh.png"))); // NOI18N
        Refresh_BTN_FS.setToolTipText("Refresh File");
        Refresh_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Refresh_BTN_FSActionPerformed(evt);
            }
        });

        Reload_BTN_FS10.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/reload.png"))); // NOI18N
        Reload_BTN_FS10.setToolTipText("Reload");
        Reload_BTN_FS10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Reload_BTN_FS10ActionPerformed(evt);
            }
        });

        undo_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/undo.png"))); // NOI18N
        undo_BTN_FS.setToolTipText("Undo");
        undo_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                undo_BTN_FSActionPerformed(evt);
            }
        });

        Redo_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/redo.png"))); // NOI18N
        Redo_BTN_FS.setToolTipText("Redo");
        Redo_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Redo_BTN_FSActionPerformed(evt);
            }
        });

        Find_BTN_FS.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/webaon/aonicrat/images/search.png"))); // NOI18N
        Find_BTN_FS.setToolTipText("Find/Search");
        Find_BTN_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Find_BTN_FSActionPerformed(evt);
            }
        });

        jTable1.setDefaultEditor(Object.class, null);
        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Name", "Size", "Type", "Modified"
            }
        ));
        jScrollPane1.setViewportView(jTable1);

        javax.swing.GroupLayout topBar_FSLayout = new javax.swing.GroupLayout(topBar_FS);
        topBar_FS.setLayout(topBar_FSLayout);
        topBar_FSLayout.setHorizontalGroup(
            topBar_FSLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(topBar_FSLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(topBar_FSLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(topBar_FSLayout.createSequentialGroup()
                        .addComponent(remotePath_FS_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(RemotePath_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 700, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Go_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Reload_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Backward_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Forward_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(topBar_FSLayout.createSequentialGroup()
                        .addComponent(Upload_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Download_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Delete_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Rename_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Extract_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ZIP_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(View_File_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Edit_File_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Refresh_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Reload_BTN_FS10, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(undo_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Redo_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Find_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(34, Short.MAX_VALUE))
            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        topBar_FSLayout.setVerticalGroup(
            topBar_FSLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(topBar_FSLayout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addGroup(topBar_FSLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(Forward_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Backward_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Reload_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(topBar_FSLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(remotePath_FS_label)
                        .addComponent(RemotePath_FS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(Go_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(topBar_FSLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(Upload_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Download_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Delete_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Rename_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Extract_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ZIP_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(View_File_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Edit_File_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Refresh_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Reload_BTN_FS10, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(undo_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Redo_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(Find_BTN_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 12, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 413, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        OS_Type_FS_Label.setText("OS Type:");

        OS_Type_for_FS.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Windows", "Mac OS", "Linux", "Android", "iOS" }));

        device_FS_label.setText("Device:");

        DeviceSelector_FS.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Device 1 - some sample name", "Device 2", "Device 3", "Device 4" }));

        load_more_devices_btn_FS.setText("Load More Devices");
        load_more_devices_btn_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                load_more_devices_btn_FSActionPerformed(evt);
            }
        });

        connect_btn_FS.setText("Connect");
        connect_btn_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connect_btn_FSActionPerformed(evt);
            }
        });

        Disconnect_btn_FS.setText("Disconnect");
        Disconnect_btn_FS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Disconnect_btn_FSActionPerformed(evt);
            }
        });

        Status_shower.setText("Status:");

        status_label.setText("Default");

        javax.swing.GroupLayout FileSystem_TabLayout = new javax.swing.GroupLayout(FileSystem_Tab);
        FileSystem_Tab.setLayout(FileSystem_TabLayout);
        FileSystem_TabLayout.setHorizontalGroup(
            FileSystem_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(topBar_FS, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(FileSystem_TabLayout.createSequentialGroup()
                .addComponent(OS_Type_FS_Label, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(OS_Type_for_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(device_FS_label, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10)
                .addComponent(DeviceSelector_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10)
                .addComponent(load_more_devices_btn_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10)
                .addComponent(connect_btn_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(15, 15, 15)
                .addComponent(Disconnect_btn_FS, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(FileSystem_TabLayout.createSequentialGroup()
                .addGroup(FileSystem_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator1)
                    .addGroup(FileSystem_TabLayout.createSequentialGroup()
                        .addComponent(Status_shower, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(status_label, javax.swing.GroupLayout.PREFERRED_SIZE, 570, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(status_progress_bar, javax.swing.GroupLayout.PREFERRED_SIZE, 246, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        FileSystem_TabLayout.setVerticalGroup(
            FileSystem_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(FileSystem_TabLayout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addGroup(FileSystem_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(FileSystem_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(OS_Type_FS_Label)
                        .addComponent(OS_Type_for_FS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(device_FS_label)
                    .addComponent(DeviceSelector_FS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(load_more_devices_btn_FS)
                    .addComponent(connect_btn_FS)
                    .addComponent(Disconnect_btn_FS))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(topBar_FS, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(FileSystem_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(status_progress_bar, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(FileSystem_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(Status_shower)
                        .addComponent(status_label)))
                .addContainerGap())
        );

        Tabs.addTab("File System", FileSystem_Tab);

        CommandLine_Tab.setLayout(null);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 930, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 350, Short.MAX_VALUE)
        );

        terminalPanel = new TerminalPanel();
        jPanel1.setLayout(new java.awt.BorderLayout());
        jPanel1.add(terminalPanel, java.awt.BorderLayout.CENTER);

        CommandLine_Tab.add(jPanel1);
        jPanel1.setBounds(0, 40, 930, 350);

        OS_Type_CLI_Label.setText("OS Type:");
        CommandLine_Tab.add(OS_Type_CLI_Label);
        OS_Type_CLI_Label.setBounds(10, 10, 50, 16);

        OS_Type_for_CommandLine.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Windows", "Mac OS", "Linux", "Android", "iOS" }));
        CommandLine_Tab.add(OS_Type_for_CommandLine);
        OS_Type_for_CommandLine.setBounds(70, 10, 100, 22);

        device_cli_label.setText("Device:");
        CommandLine_Tab.add(device_cli_label);
        device_cli_label.setBounds(240, 10, 40, 16);

        DeviceSelector_CLI.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Device 1 - some sample name", "Device 2", "Device 3", "Device 4" }));
        CommandLine_Tab.add(DeviceSelector_CLI);
        DeviceSelector_CLI.setBounds(290, 10, 220, 22);

        load_more_devices_btn_cli.setText("Load More Devices");
        CommandLine_Tab.add(load_more_devices_btn_cli);
        load_more_devices_btn_cli.setBounds(520, 10, 150, 23);

        connect_btn_cli.setText("Connect");
        CommandLine_Tab.add(connect_btn_cli);
        connect_btn_cli.setBounds(680, 10, 73, 23);

        Disconnect_btn_cli.setText("Disconnect");
        CommandLine_Tab.add(Disconnect_btn_cli);
        Disconnect_btn_cli.setBounds(770, 10, 100, 23);

        Tabs.addTab("Command Line", CommandLine_Tab);

        javax.swing.GroupLayout GUIRemote_TabLayout = new javax.swing.GroupLayout(GUIRemote_Tab);
        GUIRemote_Tab.setLayout(GUIRemote_TabLayout);
        GUIRemote_TabLayout.setHorizontalGroup(
            GUIRemote_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 930, Short.MAX_VALUE)
        );
        GUIRemote_TabLayout.setVerticalGroup(
            GUIRemote_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 567, Short.MAX_VALUE)
        );

        Tabs.addTab("GUI Remote", GUIRemote_Tab);

        javax.swing.GroupLayout NetworkControls_TabLayout = new javax.swing.GroupLayout(NetworkControls_Tab);
        NetworkControls_Tab.setLayout(NetworkControls_TabLayout);
        NetworkControls_TabLayout.setHorizontalGroup(
            NetworkControls_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 930, Short.MAX_VALUE)
        );
        NetworkControls_TabLayout.setVerticalGroup(
            NetworkControls_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 567, Short.MAX_VALUE)
        );

        Tabs.addTab("Network Controls", NetworkControls_Tab);

        javax.swing.GroupLayout ClientGenerator_TabLayout = new javax.swing.GroupLayout(ClientGenerator_Tab);
        ClientGenerator_Tab.setLayout(ClientGenerator_TabLayout);
        ClientGenerator_TabLayout.setHorizontalGroup(
            ClientGenerator_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 930, Short.MAX_VALUE)
        );
        ClientGenerator_TabLayout.setVerticalGroup(
            ClientGenerator_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 567, Short.MAX_VALUE)
        );

        Tabs.addTab("Client Generator", ClientGenerator_Tab);

        javax.swing.GroupLayout Others_TabLayout = new javax.swing.GroupLayout(Others_Tab);
        Others_Tab.setLayout(Others_TabLayout);
        Others_TabLayout.setHorizontalGroup(
            Others_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 930, Short.MAX_VALUE)
        );
        Others_TabLayout.setVerticalGroup(
            Others_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 567, Short.MAX_VALUE)
        );

        Tabs.addTab("Others", Others_Tab);

        javax.swing.GroupLayout Advanced_TabLayout = new javax.swing.GroupLayout(Advanced_Tab);
        Advanced_Tab.setLayout(Advanced_TabLayout);
        Advanced_TabLayout.setHorizontalGroup(
            Advanced_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 930, Short.MAX_VALUE)
        );
        Advanced_TabLayout.setVerticalGroup(
            Advanced_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 567, Short.MAX_VALUE)
        );

        Tabs.addTab("Advanced", Advanced_Tab);

        ServerSettings_Label.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        ServerSettings_Label.setText("Server Settings");

        portTextField.setToolTipText("Enter Port Number to Listen");
        portTextField.setInputVerifier(new IntegerVerifier());

        jLabel1.setText("Set Port Number:");

        startServerButton.setText("Start Server");
        startServerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startServerButtonActionPerformed(evt);
            }
        });

        stopServerButton.setText("Stop Server");
        stopServerButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopServerButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout Settings_TabLayout = new javax.swing.GroupLayout(Settings_Tab);
        Settings_Tab.setLayout(Settings_TabLayout);
        Settings_TabLayout.setHorizontalGroup(
            Settings_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSeparator2)
            .addGroup(Settings_TabLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(Settings_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ServerSettings_Label, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(Settings_TabLayout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(portTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(startServerButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(stopServerButton)))
                .addContainerGap(545, Short.MAX_VALUE))
        );
        Settings_TabLayout.setVerticalGroup(
            Settings_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(Settings_TabLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ServerSettings_Label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(Settings_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(portTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1)
                    .addComponent(startServerButton)
                    .addComponent(stopServerButton))
                .addContainerGap(507, Short.MAX_VALUE))
        );

        Tabs.addTab("Settings", Settings_Tab);

        javax.swing.GroupLayout About_TabLayout = new javax.swing.GroupLayout(About_Tab);
        About_Tab.setLayout(About_TabLayout);
        About_TabLayout.setHorizontalGroup(
            About_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 930, Short.MAX_VALUE)
        );
        About_TabLayout.setVerticalGroup(
            About_TabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 567, Short.MAX_VALUE)
        );

        Tabs.addTab("About", About_Tab);

        Header.setBackground(new java.awt.Color(0, 153, 255));

        Header_Name.setFont(new java.awt.Font("ROG Fonts", 1, 48)); // NOI18N
        Header_Name.setForeground(new java.awt.Color(255, 255, 255));
        Header_Name.setText("AonicRAT");

        username_welcome.setFont(new java.awt.Font("ROG Fonts", 2, 12)); // NOI18N
        username_welcome.setText("Welcome Back, ");

        exitbtn.setFont(new java.awt.Font("ROG Fonts", 0, 18)); // NOI18N
        exitbtn.setText("Close");
        exitbtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitbtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout HeaderLayout = new javax.swing.GroupLayout(Header);
        Header.setLayout(HeaderLayout);
        HeaderLayout.setHorizontalGroup(
            HeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(HeaderLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(HeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(HeaderLayout.createSequentialGroup()
                        .addComponent(username_welcome, javax.swing.GroupLayout.PREFERRED_SIZE, 304, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(HeaderLayout.createSequentialGroup()
                        .addComponent(Header_Name, javax.swing.GroupLayout.PREFERRED_SIZE, 329, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(exitbtn, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        HeaderLayout.setVerticalGroup(
            HeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(HeaderLayout.createSequentialGroup()
                .addGroup(HeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(HeaderLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(Header_Name)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, HeaderLayout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(exitbtn, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)))
                .addComponent(username_welcome)
                .addGap(14, 14, 14))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(Header, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(Tabs, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(Header, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(3, 3, 3)
                .addComponent(Tabs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(80, Short.MAX_VALUE))
        );

        getAccessibleContext().setAccessibleName("AonicRAT");
        getAccessibleContext().setAccessibleDescription("AonicRAT - A RAT by Webaon");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void exitbtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitbtnActionPerformed
        // TODO add your handling code here:
        System.exit(0);
    }//GEN-LAST:event_exitbtnActionPerformed

    private void startServerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startServerButtonActionPerformed
        // TODO add your handling code here:
        int port_number_server = Integer.parseInt(portTextField.getText());
        serverManager.startServer(port_number_server);
    }//GEN-LAST:event_startServerButtonActionPerformed

    private void stopServerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopServerButtonActionPerformed
        // TODO add your handling code here:
        serverManager.stopServer();
    }//GEN-LAST:event_stopServerButtonActionPerformed

    private void load_more_devices_btn_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_load_more_devices_btn_FSActionPerformed
        // TODO add your handling code here:
        String selectedOsType = (String) OS_Type_for_FS.getSelectedItem();
        List<RemoteDevice> devices = serverManager.getDevicesByOsType(selectedOsType);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (RemoteDevice d : devices) {
            model.addElement(d.toString());
        }
        DeviceSelector_FS.setModel(model);
    }//GEN-LAST:event_load_more_devices_btn_FSActionPerformed

    private void connect_btn_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connect_btn_FSActionPerformed
        // TODO add your handling code here:
        String selected = (String) DeviceSelector_FS.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No device selected");
            return;
        }

        // find the RemoteDevice whose toString() matches
        currentDevice = serverManager.getConnectedDevices()
                                     .stream()
                                     .filter(d -> d.toString().equals(selected))
                                     .findFirst()
                                     .orElse(null);

        if (currentDevice == null) {
            JOptionPane.showMessageDialog(this, "Device not found");
            return;
        }

        status_label.setText("Connected to " + currentDevice);

        // right after connecting ask the agent for its roots:
        serverManager.requestRoots(currentDevice);
    }//GEN-LAST:event_connect_btn_FSActionPerformed

    private void Disconnect_btn_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Disconnect_btn_FSActionPerformed
        // TODO add your handling code here:
         currentDevice = null;
        status_label.setText("Disconnected");
    }//GEN-LAST:event_Disconnect_btn_FSActionPerformed

    private void Go_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Go_BTN_FSActionPerformed
        // TODO add your handling code here:
        if (currentDevice == null) {
            JOptionPane.showMessageDialog(this, "No device selected");
            return;
        }
        String path = RemotePath_FS.getText();
        navigateTo(path);
    }//GEN-LAST:event_Go_BTN_FSActionPerformed

    private void Backward_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Backward_BTN_FSActionPerformed
        // TODO add your handling code here:
        if (!backStack.isEmpty()) {
            String currentPath = RemotePath_FS.getText();
            forwardStack.push(currentPath);  // so we can go forward again
            String previousPath = backStack.pop();
            RemotePath_FS.setText(previousPath);
            if (currentDevice != null) {
                serverManager.sendListRequest(currentDevice, previousPath);
            }
        }
    }//GEN-LAST:event_Backward_BTN_FSActionPerformed

    private void Forward_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Forward_BTN_FSActionPerformed
        // TODO add your handling code here:
        if (!forwardStack.isEmpty()) {
            String currentPath = RemotePath_FS.getText();
            backStack.push(currentPath);  // so we can go back again
            String nextPath = forwardStack.pop();
            RemotePath_FS.setText(nextPath);
            if (currentDevice != null) {
                serverManager.sendListRequest(currentDevice, nextPath);
            }
        }
    }//GEN-LAST:event_Forward_BTN_FSActionPerformed

    private void Reload_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Reload_BTN_FSActionPerformed
        // TODO add your handling code here:
        String currentPath = RemotePath_FS.getText();
        if (currentDevice != null && currentPath != null && !currentPath.isEmpty()) {
            serverManager.sendListRequest(currentDevice, currentPath);
        }
    }//GEN-LAST:event_Reload_BTN_FSActionPerformed

    private void Upload_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Upload_BTN_FSActionPerformed
        // TODO add your handling code here:
        if (currentDevice == null) {
            JOptionPane.showMessageDialog(this, "No device selected");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File[] selected = chooser.getSelectedFiles();
            String remotePath = RemotePath_FS.getText();

            for (File f : selected) {
                serverManager.uploadFileOrFolder(currentDevice, remotePath, f);
            }

            serverManager.sendListRequest(currentDevice, remotePath); // refresh
        }
    }//GEN-LAST:event_Upload_BTN_FSActionPerformed

    private void Download_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Download_BTN_FSActionPerformed
        // TODO add your handling code here:

        // base path from text field (current remote folder)
        String basePath = RemotePath_FS.getText();

        // file/folder name from the selected row in jTable1
        int selectedRow = jTable1.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a file/folder in the table.");
            return;
        }
        String fileName = jTable1.getValueAt(selectedRow, 0).toString();
        String separator = basePath.contains("\\") ? "\\" : "/";

        // merge into full path
        String selectedRemotePath = basePath + separator + fileName;
        if (selectedRemotePath == null || selectedRemotePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a file/folder.");
            return;
        }
        if (currentDevice == null) {
            JOptionPane.showMessageDialog(this, "Please select a device.");
            return;
        } 

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose download folder");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File localDir = chooser.getSelectedFile();
            status_label.setText("Starting download...");
            serverManager.downloadFileOrFolder(
                currentDevice, selectedRemotePath, localDir
            );
        }
    }//GEN-LAST:event_Download_BTN_FSActionPerformed

    private void Delete_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Delete_BTN_FSActionPerformed
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "No device selected"); return; }
        int row = jTable1.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a file or folder to delete"); return; }
        String name = jTable1.getValueAt(row, 0).toString();
        String basePath = RemotePath_FS.getText();
        String sep = basePath.contains("\\") ? "\\" : "/";
        String targetPath = basePath + sep + name;
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete permanently?\n" + targetPath, "Confirm Delete",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            status_label.setText("Deleting: " + name);
            serverManager.sendDelete(currentDevice, targetPath);
        }
    }//GEN-LAST:event_Delete_BTN_FSActionPerformed

    private void Rename_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Rename_BTN_FSActionPerformed
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "No device selected"); return; }
        int row = jTable1.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a file or folder to rename"); return; }
        String name = jTable1.getValueAt(row, 0).toString();
        String basePath = RemotePath_FS.getText();
        String sep = basePath.contains("\\") ? "\\" : "/";
        String oldPath = basePath + sep + name;
        String newName = JOptionPane.showInputDialog(this, "New name:", name);
        if (newName != null && !newName.trim().isEmpty() && !newName.trim().equals(name)) {
            String newPath = basePath + sep + newName.trim();
            status_label.setText("Renaming: " + name + " → " + newName.trim());
            serverManager.sendRename(currentDevice, oldPath, newPath);
        }
    }//GEN-LAST:event_Rename_BTN_FSActionPerformed

    private void Extract_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Extract_BTN_FSActionPerformed
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "No device selected"); return; }
        int row = jTable1.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a zip file to extract"); return; }
        String name = jTable1.getValueAt(row, 0).toString();
        String basePath = RemotePath_FS.getText();
        String sep = basePath.contains("\\") ? "\\" : "/";
        String zipPath = basePath + sep + name;
        String destDir = JOptionPane.showInputDialog(this, "Extract to directory:", basePath);
        if (destDir != null && !destDir.trim().isEmpty()) {
            status_label.setText("Extracting: " + name);
            serverManager.sendExtract(currentDevice, zipPath, destDir.trim());
        }
    }//GEN-LAST:event_Extract_BTN_FSActionPerformed

    private void ZIP_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ZIP_BTN_FSActionPerformed
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "No device selected"); return; }
        int row = jTable1.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a file or folder to zip"); return; }
        String name = jTable1.getValueAt(row, 0).toString();
        String basePath = RemotePath_FS.getText();
        String sep = basePath.contains("\\") ? "\\" : "/";
        String sourcePath = basePath + sep + name;
        String destPath = JOptionPane.showInputDialog(this, "Save zip as:", sourcePath + ".zip");
        if (destPath != null && !destPath.trim().isEmpty()) {
            status_label.setText("Zipping: " + name);
            serverManager.sendZip(currentDevice, sourcePath, destPath.trim());
        }
    }//GEN-LAST:event_ZIP_BTN_FSActionPerformed

    private void View_File_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_View_File_BTN_FSActionPerformed
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "No device selected"); return; }
        int row = jTable1.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a file to view"); return; }
        if ("Folder".equalsIgnoreCase(jTable1.getValueAt(row, 2).toString())) {
            JOptionPane.showMessageDialog(this, "Select a file, not a folder"); return;
        }
        String name = jTable1.getValueAt(row, 0).toString();
        String basePath = RemotePath_FS.getText();
        String sep = basePath.contains("\\") ? "\\" : "/";
        pendingReadIsEdit = false;
        status_label.setText("Reading: " + name);
        serverManager.sendReadFile(currentDevice, basePath + sep + name);
    }//GEN-LAST:event_View_File_BTN_FSActionPerformed

    private void Edit_File_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Edit_File_BTN_FSActionPerformed
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "No device selected"); return; }
        int row = jTable1.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a file to edit"); return; }
        if ("Folder".equalsIgnoreCase(jTable1.getValueAt(row, 2).toString())) {
            JOptionPane.showMessageDialog(this, "Select a file, not a folder"); return;
        }
        String name = jTable1.getValueAt(row, 0).toString();
        String basePath = RemotePath_FS.getText();
        String sep = basePath.contains("\\") ? "\\" : "/";
        pendingReadIsEdit = true;
        status_label.setText("Opening for edit: " + name);
        serverManager.sendReadFile(currentDevice, basePath + sep + name);
    }//GEN-LAST:event_Edit_File_BTN_FSActionPerformed

    private void Refresh_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Refresh_BTN_FSActionPerformed
        String path = RemotePath_FS.getText();
        if (currentDevice != null && path != null && !path.isEmpty()) {
            serverManager.sendListRequest(currentDevice, path);
        }
    }//GEN-LAST:event_Refresh_BTN_FSActionPerformed

    private void Reload_BTN_FS10ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Reload_BTN_FS10ActionPerformed
        String path = RemotePath_FS.getText();
        if (currentDevice != null && path != null && !path.isEmpty()) {
            serverManager.sendListRequest(currentDevice, path);
        }
    }//GEN-LAST:event_Reload_BTN_FS10ActionPerformed

    private void undo_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_undo_BTN_FSActionPerformed
        Backward_BTN_FSActionPerformed(evt);
    }//GEN-LAST:event_undo_BTN_FSActionPerformed

    private void Redo_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Redo_BTN_FSActionPerformed
        Forward_BTN_FSActionPerformed(evt);
    }//GEN-LAST:event_Redo_BTN_FSActionPerformed

    private void Find_BTN_FSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Find_BTN_FSActionPerformed
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "No device selected"); return; }
        String query = JOptionPane.showInputDialog(this, "Search for (name contains):", "");
        if (query != null && !query.trim().isEmpty()) {
            status_label.setText("Searching for: " + query.trim());
            serverManager.sendFind(currentDevice, RemotePath_FS.getText(), query.trim());
        }
    }//GEN-LAST:event_Find_BTN_FSActionPerformed

    private void navigateTo(String newPath) {
        String currentPath = RemotePath_FS.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            backStack.push(currentPath);  // store history
        }
        forwardStack.clear();         // clear forward history
        RemotePath_FS.setText(newPath);
        if (currentDevice != null) {
            serverManager.sendListRequest(currentDevice, newPath);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(AonMain.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(AonMain.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(AonMain.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(AonMain.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new AonMain().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel About_Tab;
    private javax.swing.JPanel Advanced_Tab;
    private javax.swing.JButton Backward_BTN_FS;
    private javax.swing.JPanel ClientGenerator_Tab;
    private javax.swing.JPanel CommandLine_Tab;
    private javax.swing.JButton Delete_BTN_FS;
    private javax.swing.JComboBox<String> DeviceSelector_CLI;
    private javax.swing.JComboBox<String> DeviceSelector_FS;
    private javax.swing.JButton Disconnect_btn_FS;
    private javax.swing.JButton Disconnect_btn_cli;
    private javax.swing.JButton Download_BTN_FS;
    private javax.swing.JButton Edit_File_BTN_FS;
    private javax.swing.JButton Extract_BTN_FS;
    private javax.swing.JPanel FileSystem_Tab;
    private javax.swing.JButton Find_BTN_FS;
    private javax.swing.JButton Forward_BTN_FS;
    private javax.swing.JPanel GUIRemote_Tab;
    private javax.swing.JButton Go_BTN_FS;
    private javax.swing.JPanel Header;
    private javax.swing.JLabel Header_Name;
    private javax.swing.JPanel NetworkControls_Tab;
    private javax.swing.JLabel OS_Type_CLI_Label;
    private javax.swing.JLabel OS_Type_FS_Label;
    private javax.swing.JComboBox<String> OS_Type_for_CommandLine;
    private javax.swing.JComboBox<String> OS_Type_for_FS;
    private javax.swing.JPanel Others_Tab;
    private javax.swing.JButton Redo_BTN_FS;
    private javax.swing.JButton Refresh_BTN_FS;
    private javax.swing.JButton Reload_BTN_FS;
    private javax.swing.JButton Reload_BTN_FS10;
    private javax.swing.JTextField RemotePath_FS;
    private javax.swing.JButton Rename_BTN_FS;
    private javax.swing.JLabel ServerSettings_Label;
    private javax.swing.JPanel Settings_Tab;
    private javax.swing.JLabel Status_shower;
    private javax.swing.JTabbedPane Tabs;
    private javax.swing.JButton Upload_BTN_FS;
    private javax.swing.JButton View_File_BTN_FS;
    private javax.swing.JButton ZIP_BTN_FS;
    private javax.swing.JButton connect_btn_FS;
    private javax.swing.JButton connect_btn_cli;
    private javax.swing.JLabel device_FS_label;
    private javax.swing.JLabel device_cli_label;
    private javax.swing.JToggleButton exitbtn;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JTable jTable1;
    private javax.swing.JButton load_more_devices_btn_FS;
    private javax.swing.JButton load_more_devices_btn_cli;
    private javax.swing.JTextField portTextField;
    private javax.swing.JLabel remotePath_FS_label;
    private javax.swing.JButton startServerButton;
    private javax.swing.JLabel status_label;
    private javax.swing.JProgressBar status_progress_bar;
    private javax.swing.JButton stopServerButton;
    private javax.swing.JPanel topBar_FS;
    private javax.swing.JButton undo_BTN_FS;
    private javax.swing.JLabel username_welcome;
    // End of variables declaration//GEN-END:variables
}
