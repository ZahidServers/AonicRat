package com.webaon.aonicrat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Displays a live remote screen and forwards mouse / keyboard events back
 * to the server in remote-screen coordinates.
 *
 * Layout:
 *   - The captured frame is drawn centred and letterboxed inside the panel.
 *   - Mouse coordinates are mapped from panel-space → remote-screen-space
 *     before being sent so the remote Robot lands on the correct pixel.
 *   - Keyboard events are forwarded as Java VK_ codes (same on all platforms)
 *     so the agent's Robot can replay them verbatim.
 */
public class RemoteDesktopPanel extends JPanel {

    // current frame and its remote dimensions
    private volatile BufferedImage frame;
    private int remoteW, remoteH;

    // letterbox geometry (updated in paintComponent)
    private int    offsetX, offsetY;
    private double scale = 1.0;

    // ── event callbacks (set by AonMain) ──────────────────────────────────────
    private Consumer<int[]> onMouseMoved;    // [rx, ry]
    private Consumer<int[]> onMousePressed;  // [rx, ry, awtButtonMask]
    private Consumer<int[]> onMouseReleased; // [rx, ry, awtButtonMask]
    private IntConsumer     onMouseScrolled; // wheel notches (+/-)
    private IntConsumer     onKeyPressed;    // Java VK_ code
    private IntConsumer     onKeyReleased;   // Java VK_ code

    private static final String PLACEHOLDER =
        "Remote desktop will appear here once connected";

    public RemoteDesktopPanel() {
        setBackground(new Color(18, 18, 18));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false); // let Tab through to the remote

        // ── mouse motion ────────────────────────────────────────────────────
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved  (MouseEvent e) { fireMove(e); }
            @Override public void mouseDragged(MouseEvent e) { fireMove(e); }
        });

        // ── mouse buttons ────────────────────────────────────────────────────
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();          // grab keyboard focus
                fireButton(e, true);
            }
            @Override
            public void mouseReleased(MouseEvent e) { fireButton(e, false); }
        });

        // ── scroll wheel ─────────────────────────────────────────────────────
        addMouseWheelListener(e -> {
            if (onMouseScrolled != null)
                onMouseScrolled.accept((int) e.getPreciseWheelRotation());
        });

        // ── keyboard ─────────────────────────────────────────────────────────
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed (KeyEvent e) {
                if (onKeyPressed  != null) onKeyPressed .accept(e.getKeyCode());
            }
            @Override public void keyReleased(KeyEvent e) {
                if (onKeyReleased != null) onKeyReleased.accept(e.getKeyCode());
            }
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void updateFrame(BufferedImage img) {
        this.frame   = img;
        this.remoteW = img.getWidth();
        this.remoteH = img.getHeight();
        SwingUtilities.invokeLater(this::repaint);
    }

    public void clearFrame() {
        frame = null;
        repaint();
    }

    /** Returns the most recently received frame, or null if none yet. */
    public BufferedImage getCurrentFrame() { return frame; }

    public void setOnMouseMoved   (Consumer<int[]> h) { onMouseMoved    = h; }
    public void setOnMousePressed (Consumer<int[]> h) { onMousePressed  = h; }
    public void setOnMouseReleased(Consumer<int[]> h) { onMouseReleased = h; }
    public void setOnMouseScrolled(IntConsumer     h) { onMouseScrolled = h; }
    public void setOnKeyPressed   (IntConsumer     h) { onKeyPressed    = h; }
    public void setOnKeyReleased  (IntConsumer     h) { onKeyReleased   = h; }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage f = frame;
        if (f == null) {
            drawPlaceholder(g);
            return;
        }

        // fit the frame into the panel while keeping aspect ratio
        double sx = (double) getWidth()  / remoteW;
        double sy = (double) getHeight() / remoteH;
        scale   = Math.min(sx, sy);
        int w   = (int)(remoteW * scale);
        int h   = (int)(remoteH * scale);
        offsetX = (getWidth()  - w) / 2;
        offsetY = (getHeight() - h) / 2;

        // black bars on the sides/top
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        // draw the frame
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(f, offsetX, offsetY, w, h, null);
    }

    private void drawPlaceholder(Graphics g) {
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(100, 100, 100));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(PLACEHOLDER,
            (getWidth()  - fm.stringWidth(PLACEHOLDER)) / 2,
            getHeight() / 2);
    }

    // ── Event helpers ─────────────────────────────────────────────────────────

    private void fireMove(MouseEvent e) {
        Point r = toRemote(e.getPoint());
        if (r != null && onMouseMoved != null)
            onMouseMoved.accept(new int[]{r.x, r.y});
    }

    private void fireButton(MouseEvent e, boolean press) {
        Point r = toRemote(e.getPoint());
        if (r == null) return;
        int mask = swingToAwtMask(e.getButton());
        Consumer<int[]> h = press ? onMousePressed : onMouseReleased;
        if (h != null) h.accept(new int[]{r.x, r.y, mask});
    }

    /** Convert panel-space point → remote-screen-space point. Returns null if outside frame. */
    private Point toRemote(Point p) {
        if (frame == null || scale == 0) return null;
        int rx = (int)((p.x - offsetX) / scale);
        int ry = (int)((p.y - offsetY) / scale);
        if (rx < 0 || ry < 0 || rx >= remoteW || ry >= remoteH) return null;
        return new Point(rx, ry);
    }

    /**
     * Convert Swing mouse-button constant (BUTTON1/2/3) to the AWT
     * InputEvent mask used by java.awt.Robot.
     */
    private static int swingToAwtMask(int swingButton) {
        switch (swingButton) {
            case MouseEvent.BUTTON2: return InputEvent.BUTTON2_DOWN_MASK;
            case MouseEvent.BUTTON3: return InputEvent.BUTTON3_DOWN_MASK;
            default:                 return InputEvent.BUTTON1_DOWN_MASK;
        }
    }
}
