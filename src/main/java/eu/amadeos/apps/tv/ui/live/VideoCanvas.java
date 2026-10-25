package eu.amadeos.apps.tv.ui.live;

import java.awt.*;

/**
 * The native window mpv renders into (through its {@code wid} option). It is heavyweight on purpose: Swing
 * cannot paint over the picture, so the controls sit around it. While mpv shows nothing, the canvas paints
 * a hint.
 */
public final class VideoCanvas extends Canvas {
    private String hint = "";

    public VideoCanvas() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(640, 360));
        // Keyboard shortcuts belong to the window, not to the native video window.
        setFocusable(false);
    }

    public void setHint(String text) {
        if (text.equals(hint)) return;
        hint = text;
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (hint.isEmpty()) return;
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 14) : getFont().deriveFont(14f));
        g2.setColor(new Color(0x9A, 0x9A, 0x9A));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, (getHeight() + fm.getAscent()) / 2);
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }
}
