package eu.amadeos.apps.tv.ui.live;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Schedule;
import eu.amadeos.apps.tv.ui.main.ChannelTile;
import eu.amadeos.apps.tv.ui.main.LogoSource;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A row of the channel list: the channel tile (logo), what runs now with its times, and a bar for how much
 * of it has run.
 */
final class ChannelCell extends JPanel implements ListCellRenderer<Channel> {
    static final int HEIGHT = ChannelTile.HEIGHT + 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Supplier<Schedule> schedule;
    private final Supplier<Instant> clock;
    private final LogoSource logos;
    private final ZoneId zone;
    private Channel channel;
    private boolean selected;
    private boolean playing;
    private String playingId = "";

    ChannelCell(Supplier<Schedule> schedule, Supplier<Instant> clock, LogoSource logos, ZoneId zone) {
        this.schedule = schedule;
        this.clock = clock;
        this.logos = logos;
        this.zone = zone;
        setPreferredSize(new Dimension(300, HEIGHT));
    }

    void setPlaying(String channelId) {
        playingId = channelId == null ? "" : channelId;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Channel> list, Channel value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        channel = value;
        selected = isSelected;
        playing = value != null && value.id().equals(playingId);
        setFont(list.getFont());
        if (value != null) getAccessibleContext().setAccessibleName(describe(value));
        return this;
    }

    /** "Tagesschau, 20:00 to 20:15" for screen readers and tests. */
    String describe(Channel c) {
        Optional<Broadcast> now = schedule.get().at(c.id(), clock.get());
        return now.map(b -> tr("live.cell.now", c.name(), b.fullTitle(), TIME.format(b.start().atZone(zone)),
                TIME.format(b.end().atZone(zone)))).orElse(c.name());
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (channel == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        DesktopTheme t = AmadeUI.theme();
        int w = getWidth(), h = getHeight();
        g.setColor(selected ? t.selectionBackground() : t.fieldBackground());
        g.fillRect(0, 0, w, h);
        Color text = selected ? t.selectionText() : t.textActive();
        Color dim = selected ? t.selectionText() : t.textInactive();
        ChannelTile.paint(g, 6, (h - ChannelTile.HEIGHT) / 2, ChannelTile.WIDTH, ChannelTile.HEIGHT, channel,
                logos.logo(channel), getFont(), t.textActive());

        int x = ChannelTile.WIDTH + 14, right = w - 8;
        Instant now = clock.get();
        Optional<Broadcast> b = schedule.get().at(channel.id(), now);
        Font font = getFont();
        g.setFont(font.deriveFont(Font.BOLD, 12.5f));
        g.setColor(text);
        String title = b.map(Broadcast::title).orElse(channel.name());
        if (playing) title = "▶ " + title;
        g.drawString(clip(g, title, right - x), x, h / 2 - 3);
        g.setFont(font.deriveFont(Font.PLAIN, 11f));
        g.setColor(dim);
        if (b.isPresent()) {
            String time = TIME.format(b.get().start().atZone(zone)) + " – " + TIME.format(b.get().end().atZone(zone));
            g.drawString(time, x, h / 2 + 13);
            int bx = x + g.getFontMetrics().stringWidth(time) + 8, bw = right - bx;
            if (bw > 20) {
                g.setColor(t.sunkenBg());
                g.fillRect(bx, h / 2 + 5, bw, 7);
                AmadeUI.paintBevel(g, bx, h / 2 + 5, bw, 7, false, 1);
                g.setColor(selected ? t.selectionText() : t.activeTitleBg());
                g.fillRect(bx + 1, h / 2 + 6, (int) ((bw - 2) * b.get().progressAt(now)), 5);
            }
        } else {
            String none = channel.guide() == Channel.GuideSourceId.NONE ? "live.cell.no-guide" : "live.cell.no-info";
            g.drawString(clip(g, tr(none), w - x - 8), x, h / 2 + 13);
        }
        g.dispose();
    }

    static String clip(Graphics2D g, String s, int width) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= width) return s;
        String dots = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + dots) > width) n--;
        return s.substring(0, n) + dots;
    }
}
