package com.webaon.aonicrat;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A proper inline terminal panel.
 *
 * The user types directly inside the single JTextPane, just like a real
 * terminal. Everything above `promptPosition` is read-only (history and
 * shell output). Everything from `promptPosition` to the end is editable
 * (what the user is currently typing).
 *
 * When Enter is pressed, the typed command is sent to the persistent remote
 * shell via commandSender. The shell's response (including its next prompt)
 * is streamed back through appendOutput().
 *
 * Supports: up/down arrow history, Ctrl+L clear.
 */
public class TerminalPanel extends JScrollPane {

    private final JTextPane terminal   = new JTextPane();
    private final InlineFilter filter  = new InlineFilter();
    private int promptPosition         = 0;
    private Consumer<String> commandSender;

    private final List<String> history = new ArrayList<>();
    private int historyIndex           = -1;

    public TerminalPanel() {
        terminal.setFont(new Font("Consolas", Font.PLAIN, 13));
        terminal.setBackground(new Color(12, 12, 12));
        terminal.setForeground(new Color(200, 200, 200));
        terminal.setCaretColor(Color.WHITE);
        terminal.setEditable(true);
        terminal.setSelectedTextColor(Color.BLACK);
        terminal.setSelectionColor(new Color(80, 120, 200));

        setViewportView(terminal);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(new Color(12, 12, 12));

        ((AbstractDocument) terminal.getDocument()).setDocumentFilter(filter);

        // Prevent the caret from ever moving left of the current prompt position.
        // This intercepts ALL caret movement: mouse clicks, arrow keys, Home, etc.
        terminal.setNavigationFilter(new NavigationFilter() {
            @Override
            public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
                fb.setDot(Math.max(dot, promptPosition), bias);
            }
            @Override
            public void moveDot(FilterBypass fb, int dot, Position.Bias bias) {
                fb.moveDot(Math.max(dot, promptPosition), bias);
            }
        });

        insertDirect("Remote Shell — connect a device in the CLI tab to begin.\n");
        advancePrompt();

        terminal.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                        e.consume();
                        SwingUtilities.invokeLater(TerminalPanel.this::submitCommand);
                        break;
                    case KeyEvent.VK_UP:
                        e.consume();
                        navigateHistory(-1);
                        break;
                    case KeyEvent.VK_DOWN:
                        e.consume();
                        navigateHistory(+1);
                        break;
                    case KeyEvent.VK_L:
                        if (e.isControlDown()) {
                            e.consume();
                            clearScreen();
                        }
                        break;
                    case KeyEvent.VK_HOME:
                        // jump to start of input, not start of document
                        terminal.setCaretPosition(promptPosition);
                        e.consume();
                        break;
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setCommandSender(Consumer<String> sender) {
        this.commandSender = sender;
    }

    /**
     * Called (on any thread) when the remote shell sends output.
     * Correctly inserts the output before any text the user is currently
     * typing, then restores that typed text.
     */
    public void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            Document doc = terminal.getDocument();
            filter.bypass(true);
            try {
                // snapshot any in-progress user input
                int userLen = doc.getLength() - promptPosition;
                String userInput = userLen > 0 ? doc.getText(promptPosition, userLen) : "";
                if (userLen > 0) doc.remove(promptPosition, userLen);

                // insert output at the prompt boundary
                doc.insertString(promptPosition, text, null);
                promptPosition = doc.getLength();
                filter.setPromptAt(promptPosition);

                // restore whatever the user had started typing
                if (!userInput.isEmpty()) {
                    doc.insertString(doc.getLength(), userInput, null);
                }
                scrollToBottom();
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            } finally {
                filter.bypass(false);
            }
        });
    }

    /** Print a local informational line (not from the remote shell). */
    public void printInfo(String msg) {
        appendOutput(msg.endsWith("\n") ? msg : msg + "\n");
    }

    public void clearScreen() {
        SwingUtilities.invokeLater(() -> {
            filter.bypass(true);
            try {
                terminal.setText("");
                promptPosition = 0;
                filter.setPromptAt(0);
            } finally {
                filter.bypass(false);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void submitCommand() {
        Document doc = terminal.getDocument();
        try {
            int inputLen = doc.getLength() - promptPosition;
            String command = inputLen > 0 ? doc.getText(promptPosition, inputLen) : "";
            // strip any stray newlines the user may have typed
            command = command.replace("\r", "").replace("\n", "").trim();

            // move past command — insert newline then lock it
            filter.bypass(true);
            doc.insertString(doc.getLength(), "\n", null);
            filter.bypass(false);
            advancePrompt();

            // history
            if (!command.isEmpty() && (history.isEmpty() || !history.get(0).equals(command))) {
                history.add(0, command);
                if (history.size() > 200) history.remove(history.size() - 1);
            }
            historyIndex = -1;

            if (command.equalsIgnoreCase("cls") || command.equalsIgnoreCase("clear")) {
                clearScreen();
                // fall through: also send to shell so its own state resets
            }

            if (commandSender != null) {
                commandSender.accept(command);
            } else {
                insertDirect("[No device connected — connect a device in the CLI tab]\n");
                advancePrompt();
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;
        historyIndex = Math.max(-1, Math.min(history.size() - 1, historyIndex + direction));
        String text = historyIndex >= 0 ? history.get(historyIndex) : "";
        try {
            Document doc = terminal.getDocument();
            int userLen = doc.getLength() - promptPosition;
            if (userLen > 0) doc.remove(promptPosition, userLen);
            if (!text.isEmpty()) doc.insertString(promptPosition, text, null);
            terminal.setCaretPosition(doc.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /** Insert text bypassing the filter (internal/programmatic use). */
    private void insertDirect(String text) {
        filter.bypass(true);
        try {
            Document doc = terminal.getDocument();
            doc.insertString(doc.getLength(), text, null);
            terminal.setCaretPosition(doc.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        } finally {
            filter.bypass(false);
        }
    }

    /** Lock everything up to (and including) the current end of document. */
    private void advancePrompt() {
        promptPosition = terminal.getDocument().getLength();
        filter.setPromptAt(promptPosition);
        terminal.setCaretPosition(promptPosition);
    }

    private void scrollToBottom() {
        terminal.setCaretPosition(terminal.getDocument().getLength());
    }

    // -------------------------------------------------------------------------
    // Inline document filter — protects history above promptPosition
    // -------------------------------------------------------------------------

    private static final class InlineFilter extends DocumentFilter {

        private int promptPos    = 0;
        private boolean bypassed = false;

        void setPromptAt(int pos) { this.promptPos = pos; }
        void bypass(boolean on)   { this.bypassed = on;   }

        @Override
        public void insertString(FilterBypass fb, int offset, String string,
                                 AttributeSet attr) throws BadLocationException {
            if (bypassed || offset >= promptPos) {
                super.insertString(fb, offset, string, attr);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text,
                            AttributeSet attrs) throws BadLocationException {
            if (bypassed || offset >= promptPos) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length)
                throws BadLocationException {
            if (bypassed || offset >= promptPos) {
                super.remove(fb, offset, length);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }
}
