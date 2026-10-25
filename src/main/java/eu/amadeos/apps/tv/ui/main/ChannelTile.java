package eu.amadeos.apps.tv.ui.main;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.service.logo.LogoCache;
import eu.amadeos.commons.swing.AmadeUI;

import java.awt.*;
import java.util.Optional;

/**
 * Paints a channel's tile: a raised Motif button face with the logo, or the channel name while there is no
 * logo. White logos get a dark face, the others a light one, whatever the desktop colours.
 */
public final class ChannelTile {
    public static final int WIDTH = 104;
    public static final int HEIGHT = 34;
    static final Color LIGHT_FACE = new Color(0xF4F1EC);
    static final Color DARK_FACE = new Color(0x2E3440);

    private ChannelTile() {
    }

    public static void paint(Graphics2D g, int x, int y, int w, int h, Channel channel, Optional<LogoCache.Logo> logo,
                             Font font, Color text) {
        boolean dark = logo.map(LogoCache.Logo::light).orElse(false);
        g.setColor(logo.isEmpty() ? AmadeUI.theme().sunkenBg() : dark ? DARK_FACE : LIGHT_FACE);
        g.fillRect(x, y, w, h);
        AmadeUI.paintBevel(g, x, y, w, h, true, 1);
        if (logo.isPresent()) {
            Image img = logo.get().image();
            int iw = Math.min(img.getWidth(null), w - 8), ih = Math.min(img.getHeight(null), h - 6);
            g.drawImage(img, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, null);
            return;
        }
        Graphics2D t = (Graphics2D) g.create();
        t.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        t.setFont(font.deriveFont(Font.BOLD, Math.min(font.getSize2D(), 12f)));
        t.setColor(text);
        t.clipRect(x + 3, y, w - 6, h);
        FontMetrics fm = t.getFontMetrics();
        String name = channel.name();
        t.drawString(name, x + Math.max(4, (w - fm.stringWidth(name)) / 2), y + (h + fm.getAscent() - fm.getDescent()) / 2);
        t.dispose();
    }
}
