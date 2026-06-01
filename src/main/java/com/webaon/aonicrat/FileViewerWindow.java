/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.webaon.aonicrat;

/**
 *
 * @author Zahid Wadiwale
 */
import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

public class FileViewerWindow extends JFrame {

    public FileViewerWindow(String path, String content) {
        this(path, content, false, null);
    }

    public FileViewerWindow(String path, String content, boolean editable, BiConsumer<String, String> onSave) {
        String fileName = new java.io.File(path).getName();
        setTitle((editable ? "Edit: " : "View: ") + fileName);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTextArea area = new JTextArea(content);
        area.setFont(new Font("Consolas", Font.PLAIN, 14));
        area.setEditable(editable);
        JScrollPane pane = new JScrollPane(area);

        setLayout(new BorderLayout());
        add(pane, BorderLayout.CENTER);

        if (editable && onSave != null) {
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveBtn = new JButton("Save to Remote");
            saveBtn.addActionListener(e -> {
                onSave.accept(path, area.getText());
                JOptionPane.showMessageDialog(this, "File sent to remote device: " + fileName);
            });
            bottom.add(saveBtn);
            add(bottom, BorderLayout.SOUTH);
        }

        setVisible(true);
    }
}
