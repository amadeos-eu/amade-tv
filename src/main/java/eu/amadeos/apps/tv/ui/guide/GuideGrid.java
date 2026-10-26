package eu.amadeos.apps.tv.ui.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Schedule;
import eu.amadeos.apps.tv.ui.main.ChannelTile;
import eu.amadeos.apps.tv.ui.main.LogoSource;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The guide's time grid: one row per channel, one cell per program item, a red line at the current time.
 * Lives in a scroll pane together with its {@link #ruler()} (column header) and {@link #channelColumn()}
 * (row header). A click selects an item, a double click activates it (watch it live or set a reminder).
 */
final class GuideGrid extends JComponent implements Scrollable {
    static final int PIXELS_PER_HOUR = 220;
    static final int ROW_HEIGHT = 44;
    static final Color HIT = new Color(0xF2D27A);
    static final Color NOW_LINE = new Color(0xD84A3A);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private TimelineLayout layout;
    private ZoneId zone = ZoneId.systemDefault();
    private List<Channel> channels = List.of();
    private Schedule schedule = Schedule.EMPTY;
    private Instant now = Instant.now();
    private Broadcast selected;
    private String highlight = "";
    private Predicate<Broadcast> reminded = b -> false;
    private LogoSource logos = LogoSource.NONE;
    private Consumer<Broadcast> onSelect = b -> {
    };
    private Consumer<Broadcast> onActivate = b -> {
    };
    private final JComponent ruler = new Ruler();
    private final JComponent channelColumn = new ChannelColumn();

    GuideGrid() {
        setLayout(Instant.now(), ZoneId.systemDefault());
        setOpaque(true);
        setFocusable(true);
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Broadcast b = itemAt(e.getPoint());
                select(b);
                if (b != null && e.getClickCount() == 2) onActivate.accept(b);
            }
        });
    }

    /** Shows the calendar day (in {@code zone}) that contains {@code day}. */
    void setLayout(Instant day, ZoneId zone) {
        this.zone = zone;
        ZonedDateTime start = day.atZone(zone).toLocalDate().atStartOfDay(zone);
        layout = new TimelineLayout(start.toInstant(), start.plusDays(1).toInstant(), PIXELS_PER_HOUR, ROW_HEIGHT);
        revalidateAll();
    }

    TimelineLayout timeline() {
        return layout;
    }

    void setChannels(List<Channel> channels) {
        this.channels = List.copyOf(channels);
        revalidateAll();
    }

    void setSchedule(Schedule schedule, Instant now) {
        this.schedule = schedule;
        this.now = now;
        repaintAll();
    }

    void setHighlight(String text) {
        highlight = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        repaint();
    }

    void setReminded(Predicate<Broadcast> reminded) {
        this.reminded = reminded;
    }

    void setLogos(LogoSource logos) {
        this.logos = logos;
    }

    void onSelect(Consumer<Broadcast> listener) {
        onSelect = listener;
    }

    void onActivate(Consumer<Broadcast> listener) {
        onActivate = listener;
    }

    Schedule channelsSchedule() {
        return schedule;
    }

    Broadcast selected() {
        return selected;
    }

    void select(Broadcast b) {
        selected = b;
        repaint();
        onSelect.accept(b);
        if (b != null) {
            int row = rowOf(b.channelId());
            int[] span = layout.span(b.start(), b.end());
            if (row >= 0) scrollRectToVisible(new Rectangle(span[0], row * ROW_HEIGHT, Math.min(span[1] - span[0], 600), ROW_HEIGHT));
        }
    }

    /** Scrolls so that {@code t} is a little right of the left edge. */
    void scrollTo(Instant t) {
        int x = Math.max(0, layout.x(t) - PIXELS_PER_HOUR / 3);
        Rectangle view = getVisibleRect();
        scrollRectToVisible(new Rectangle(x, view.y, Math.max(1, view.width), Math.max(1, view.height)));
        if (getParent() instanceof JViewport vp) {
            vp.setViewPosition(new Point(Math.min(x, Math.max(0, getWidth() - vp.getWidth())), vp.getViewPosition().y));
        }
    }

    JComponent ruler() {
        return ruler;
    }

    JComponent channelColumn() {
        return channelColumn;
    }

    Broadcast itemAt(Point p) {
        int row = layout.row(p.y);
        if (row < 0 || row >= channels.size()) return null;
        Instant t = layout.at(p.x);
        return schedule.at(channels.get(row).id(), t).orElse(null);
    }

    private int rowOf(String channelId) {
        for (int i = 0; i < channels.size(); i++) if (channels.get(i).id().equals(channelId)) return i;
        return -1;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Broadcast b = itemAt(e.getPoint());
        if (b == null) return null;
        return "<html><b>" + escape(b.fullTitle()) + "</b><br>" + HOUR.format(b.start().atZone(zone)) + " – "
                + HOUR.format(b.end().atZone(zone)) + "</html>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(layout.width(), Math.max(1, channels.size()) * ROW_HEIGHT);
    }

    // --8<-- [start:paint]
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        DesktopTheme t = AmadeUI.theme();
        Rectangle clip = g.getClipBounds();
        g.setColor(t.fieldBackground());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        Instant from = layout.at(clip.x - 1), to = layout.at(clip.x + clip.width + 1);
        Font font = getFont();
        for (int r = 0; r < channels.size(); r++) {
            int y = r * ROW_HEIGHT;
            if (y > clip.y + clip.height || y + ROW_HEIGHT < clip.y) continue;
            for (Broadcast b : schedule.between(channels.get(r).id(), from, to)) {
                paintCell(g, t, font, b, y);
            }
        }
        if (now.isAfter(layout.from()) && now.isBefore(layout.to())) {
            int x = layout.x(now);
            g.setColor(NOW_LINE);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(x, clip.y, x, clip.y + clip.height);
        }
        g.dispose();
    }
    // --8<-- [end:paint]

    private void paintCell(Graphics2D g, DesktopTheme t, Font font, Broadcast b, int y) {
        int[] span = layout.span(b.start(), b.end());
        int x = span[0], w = span[1] - span[0];
        boolean running = b.isRunningAt(now);
        boolean hit = !highlight.isEmpty() && matches(b);
        boolean isSelected = b.equals(selected);
        Color face = isSelected ? t.selectionBackground() : hit ? HIT : running ? t.softHighlight() : t.sunkenBg();
        g.setColor(face);
        g.fillRect(x + 1, y + 1, w - 2, ROW_HEIGHT - 2);
        AmadeUI.paintBevel(g, x + 1, y + 1, w - 2, ROW_HEIGHT - 2, !isSelected, 1);
        Graphics2D c = (Graphics2D) g.create();
        c.clipRect(x + 4, y, w - 8, ROW_HEIGHT);
        Color text = isSelected ? t.selectionText() : hit ? Color.BLACK : t.textActive();
        c.setColor(text);
        c.setFont(font.deriveFont(running ? Font.BOLD : Font.PLAIN, 12f));
        c.drawString(b.title(), x + 6, y + 18);
        c.setFont(font.deriveFont(Font.PLAIN, 10.5f));
        c.setColor(isSelected ? t.selectionText() : hit ? Color.DARK_GRAY : t.textInactive());
        String time = HOUR.format(b.start().atZone(zone)) + (reminded.test(b) ? "  ★" : "");
        String line = b.subtitle().isEmpty() ? time : time + "  " + b.subtitle();
        c.drawString(line, x + 6, y + 34);
        c.dispose();
    }

    private boolean matches(Broadcast b) {
        return b.title().toLowerCase(Locale.ROOT).contains(highlight)
                || b.subtitle().toLowerCase(Locale.ROOT).contains(highlight);
    }

    private void revalidateAll() {
        revalidate();
        ruler.revalidate();
        channelColumn.revalidate();
        repaintAll();
    }

    private void repaintAll() {
        repaint();
        ruler.repaint();
        channelColumn.repaint();
    }

    // ---- Scrollable ------------------------------------------------------------------------------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(PIXELS_PER_HOUR * 4, ROW_HEIGHT * 10);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.HORIZONTAL ? PIXELS_PER_HOUR / 4 : ROW_HEIGHT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.HORIZONTAL ? PIXELS_PER_HOUR * 2 : ROW_HEIGHT * 5;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport vp && vp.getHeight() > getPreferredSize().height;
    }

    // ---- headers ---------------------------------------------------------------------------------

    /** The hours above the grid. */
    private final class Ruler extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(layout.width(), 26);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            DesktopTheme t = AmadeUI.theme();
            g.setColor(t.base());
            g.fillRect(0, 0, getWidth(), getHeight());
            AmadeUI.paintBevel(g, 0, 0, getWidth(), getHeight(), true, 1);
            g.setColor(t.textActive());
            g.setFont(GuideGrid.this.getFont().deriveFont(Font.BOLD, 12f));
            for (Instant h = layout.from(); h.isBefore(layout.to()); h = h.plusSeconds(1800)) {
                int x = layout.x(h);
                g.drawLine(x, getHeight() - 5, x, getHeight());
                g.drawString(HOUR.format(h.atZone(zone)), x + 4, 18);
            }
            if (now.isAfter(layout.from()) && now.isBefore(layout.to())) {
                g.setColor(NOW_LINE);
                int x = layout.x(now);
                g.fillPolygon(new int[]{x - 5, x + 5, x}, new int[]{getHeight() - 7, getHeight() - 7, getHeight()}, 3);
            }
            g.dispose();
        }
    }

    /** The channel tiles left of the grid. */
    private final class ChannelColumn extends JComponent {
        ChannelColumn() {
            setToolTipText("");
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(ChannelTile.WIDTH + 8, Math.max(1, channels.size()) * ROW_HEIGHT);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int row = layout.row(e.getY());
            return row >= 0 && row < channels.size() ? channels.get(row).name() : null;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            DesktopTheme t = AmadeUI.theme();
            g.setColor(t.base());
            g.fillRect(0, 0, getWidth(), getHeight());
            for (int r = 0; r < channels.size(); r++) {
                Channel c = channels.get(r);
                ChannelTile.paint(g, 4, r * ROW_HEIGHT + (ROW_HEIGHT - ChannelTile.HEIGHT) / 2, ChannelTile.WIDTH,
                        ChannelTile.HEIGHT, c, logos.logo(c), GuideGrid.this.getFont(), t.textActive());
            }
            g.dispose();
        }
    }

    Optional<Channel> channel(String id) {
        return channels.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
