package eu.amadeos.apps.tv.ui.live;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.PlayerState;
import eu.amadeos.apps.tv.domain.Recording;
import eu.amadeos.apps.tv.domain.Schedule;
import eu.amadeos.apps.tv.ui.guide.GuidePage;
import eu.amadeos.apps.tv.ui.main.LogoSource;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Live TV: the channel list on the left (Enter or a double click switches), the picture with its controls,
 * and today's schedule of the channel that plays.
 */
public final class LivePage extends JPanel {
    /** What the page asks of the presenter, beyond the player controls. */
    public interface Actions extends LiveControls.Actions {
        void watch(Channel c);

        void toggleReminder(Broadcast b);

        void toggleRecording(Broadcast b);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public final VideoCanvas video = new VideoCanvas();
    final DefaultListModel<Channel> channelModel = new DefaultListModel<>();
    final JList<Channel> channels = new JList<>(channelModel);
    final DefaultListModel<Broadcast> todayModel = new DefaultListModel<>();
    final JList<Broadcast> today = new JList<>(todayModel);
    final TitledBorder todayBorder = new TitledBorder("");
    final JButton remind = new JButton();
    final JButton record = new JButton();
    final LiveControls controls;
    final JScrollPane channelScroll;
    final JPanel todayPanel;
    /** The picture with the controls and today's list; its margin goes in full screen. */
    final JPanel right = new JPanel(new BorderLayout(0, 6));
    private final ChannelCell cell;
    private final ZoneId zone;
    private final Actions actions;
    private Schedule schedule = Schedule.EMPTY;
    private Instant now = Instant.now();
    private Channel playing;
    private Predicate<Broadcast> reminded = b -> false;
    private Function<Broadcast, Recording.Status> recording = b -> null;

    public LivePage(Actions actions, ZoneId zone, LogoSource logos) {
        super(new BorderLayout());
        this.actions = actions;
        this.zone = zone;
        cell = new ChannelCell(() -> schedule, () -> now, logos, zone);
        channels.setCellRenderer(cell);
        channels.setFixedCellHeight(ChannelCell.HEIGHT);
        channels.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        channels.getAccessibleContext().setAccessibleName(tr("live.channels"));
        channels.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1 && channels.getSelectedValue() != null) actions.watch(channels.getSelectedValue());
            }
        });
        channels.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "watch");
        channels.getActionMap().put("watch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (channels.getSelectedValue() != null) actions.watch(channels.getSelectedValue());
            }
        });
        channelScroll = new JScrollPane(channels, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        channelScroll.setPreferredSize(new Dimension(320, 400));

        controls = new LiveControls(actions);

        today.setCellRenderer(new TodayRenderer());
        today.setVisibleRowCount(6);
        today.getAccessibleContext().setAccessibleName(tr("live.today.name"));
        today.addListSelectionListener(e -> updateRemindButton());
        today.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Broadcast b = today.getSelectedValue();
                if (e.getClickCount() == 2 && b != null && b.start().isAfter(now)) toggleReminder(b);
            }
        });
        remind.addActionListener(e -> {
            Broadcast b = today.getSelectedValue();
            if (b != null) toggleReminder(b);
        });
        record.addActionListener(e -> {
            Broadcast b = today.getSelectedValue();
            if (b != null) {
                actions.toggleRecording(b);
                today.repaint();
                updateRemindButton();
            }
        });
        JPanel remindRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        remindRow.add(remind);
        remindRow.add(record);
        todayPanel = new JPanel(new BorderLayout(0, 4));
        todayPanel.setBorder(BorderFactory.createCompoundBorder(todayBorder, new EmptyBorder(2, 4, 4, 4)));
        todayPanel.add(new JScrollPane(today), BorderLayout.CENTER);
        todayPanel.add(remindRow, BorderLayout.SOUTH);
        updateRemindButton();

        JPanel picture = new JPanel(new BorderLayout());
        picture.add(video, BorderLayout.CENTER);
        picture.add(controls, BorderLayout.SOUTH);
        right.setBorder(new EmptyBorder(6, 6, 6, 6));
        right.add(picture, BorderLayout.CENTER);
        right.add(todayPanel, BorderLayout.SOUTH);

        add(channelScroll, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);
    }

    public void setChannels(List<Channel> list) {
        Channel selected = channels.getSelectedValue();
        channelModel.clear();
        list.forEach(channelModel::addElement);
        if (selected != null) channels.setSelectedValue(byId(selected.id()), true);
    }

    private Channel byId(String id) {
        for (int i = 0; i < channelModel.size(); i++) if (channelModel.get(i).id().equals(id)) return channelModel.get(i);
        return null;
    }

    /** The channel that plays now: selected in the list, its schedule below the picture. */
    public void setPlaying(Channel c) {
        playing = c;
        cell.setPlaying(c == null ? null : c.id());
        if (c != null && byId(c.id()) != null) channels.setSelectedValue(byId(c.id()), true);
        channels.repaint();
        showToday();
    }

    public void setSchedule(Schedule schedule, Instant now) {
        this.schedule = schedule;
        this.now = now;
        channels.repaint();
        showToday();
    }

    public void setReminded(Predicate<Broadcast> reminded) {
        this.reminded = reminded;
    }

    /** The status of a program's planned or running recording, or null. */
    public void setRecording(Function<Broadcast, Recording.Status> recording) {
        this.recording = recording;
        today.repaint();
        updateRemindButton();
    }

    public void showPlayer(PlayerState s, String audioTrack) {
        controls.show(s, audioTrack);
        video.setHint(switch (s.status()) {
            case IDLE, LOADING, FAILED -> LiveControls.statusText(s);
            case PLAYING, ENDED -> "";
        });
    }

    /** Everything but the picture, hidden in full screen, and the margin around it. */
    public void setChromeVisible(boolean visible) {
        channelScroll.setVisible(visible);
        todayPanel.setVisible(visible);
        controls.setVisible(visible);
        right.setBorder(visible ? new EmptyBorder(6, 6, 6, 6) : null);
        revalidate();
    }

    public void focusChannels() {
        channels.requestFocusInWindow();
    }

    private void toggleReminder(Broadcast b) {
        actions.toggleReminder(b);
        today.repaint();
        updateRemindButton();
    }

    private void showToday() {
        Broadcast selected = today.getSelectedValue();
        todayModel.clear();
        if (playing == null) {
            todayBorder.setTitle(tr("live.today.none"));
            todayPanel.repaint();
            return;
        }
        todayBorder.setTitle(tr("live.today", playing.name()));
        Instant from = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        Instant to = from.plusSeconds(36 * 3600);
        List<Broadcast> items = schedule.between(playing.id(), from, to);
        items.forEach(todayModel::addElement);
        if (selected != null && items.contains(selected)) {
            today.setSelectedValue(selected, false);
        } else {
            items.stream().filter(b -> b.isRunningAt(now)).findFirst().ifPresent(b -> {
                int i = items.indexOf(b);
                today.ensureIndexIsVisible(Math.min(items.size() - 1, i + 3));
                today.ensureIndexIsVisible(i);
            });
        }
        todayPanel.repaint();
        updateRemindButton();
    }

    private void updateRemindButton() {
        Broadcast b = today.getSelectedValue();
        boolean future = b != null && b.start().isAfter(now);
        remind.setEnabled(future);
        Mnemonics.apply(remind, tr(future && reminded.test(b) ? "guide.remind.cancel" : "guide.remind"));
        record.setEnabled(b != null && b.end().isAfter(now));
        Mnemonics.apply(record, tr(GuidePage.recordKey(b == null ? null : recording.apply(b))));
    }

    /** "20:15  Tatort  (on air)" with the running item in bold. */
    private final class TodayRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            Broadcast b = (Broadcast) value;
            String tag = b.isRunningAt(now) ? "  ·  " + tr("live.today.on-air")
                    : reminded.test(b) ? "  ★" : "";
            if (recording.apply(b) != null) tag += "  ●";
            setText(TIME.format(b.start().atZone(zone)) + "   " + b.fullTitle() + tag);
            setFont(getFont().deriveFont(b.isRunningAt(now) ? Font.BOLD : Font.PLAIN));
            setForeground(isSelected ? list.getSelectionForeground() : b.end().isBefore(now)
                    ? UIManager.getColor("Label.disabledForeground") : list.getForeground());
            setToolTipText(b.description().isEmpty() ? null : "<html><p width='360'>"
                    + b.description().replace("&", "&amp;").replace("<", "&lt;") + "</p></html>");
            return this;
        }
    }
}
