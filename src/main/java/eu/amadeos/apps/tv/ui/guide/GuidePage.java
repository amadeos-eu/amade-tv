package eu.amadeos.apps.tv.ui.guide;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Recording;
import eu.amadeos.apps.tv.domain.Schedule;
import eu.amadeos.apps.tv.ui.main.LogoSource;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The program guide: a day of all channels in a time grid, with day buttons, "Now", "Prime time" and a
 * search that marks the hits; below, the selected item with Watch live, Remind me and Record.
 */
public final class GuidePage extends JPanel {
    /** What the page asks of the presenter. */
    public interface Actions {
        void showDay(LocalDate day);

        void watch(Broadcast b);

        void toggleReminder(Broadcast b);

        /** Plans a recording of the program, or cancels or stops it. */
        void toggleRecording(Broadcast b);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    final GuideGrid grid = new GuideGrid();
    final JLabel day = new JLabel();
    final JTextField find = new JTextField(14);
    final JLabel findResult = new JLabel(" ");
    final JLabel detailTitle = new JLabel(" ");
    final JTextArea detailText = new JTextArea(3, 40);
    final JButton watch = new JButton();
    final JButton remind = new JButton();
    final JButton record = new JButton();
    private final Actions actions;
    private final ZoneId zone;
    private LocalDate shown;
    private List<Broadcast> hits = List.of();
    private int hit = -1;
    private Instant now = Instant.now();
    private Predicate<Broadcast> reminded = b -> false;
    private Function<Broadcast, Recording.Status> recording = b -> null;

    public GuidePage(Actions actions, ZoneId zone, LogoSource logos) {
        super(new BorderLayout());
        this.actions = actions;
        this.zone = zone;
        grid.setLogos(logos);

        JButton previous = new JButton("◀");
        previous.setToolTipText(tr("guide.previous-day"));
        previous.getAccessibleContext().setAccessibleName(tr("guide.previous-day"));
        previous.addActionListener(e -> actions.showDay(shown.minusDays(1)));
        JButton next = new JButton("▶");
        next.setToolTipText(tr("guide.next-day"));
        next.getAccessibleContext().setAccessibleName(tr("guide.next-day"));
        next.addActionListener(e -> actions.showDay(shown.plusDays(1)));
        JButton nowButton = Mnemonics.apply(new JButton(), tr("guide.now"));
        nowButton.addActionListener(e -> {
            if (!LocalDate.ofInstant(this.now, zone).equals(shown)) actions.showDay(LocalDate.ofInstant(this.now, zone));
            grid.scrollTo(this.now);
        });
        JButton prime = Mnemonics.apply(new JButton(), tr("guide.prime-time"));
        prime.addActionListener(e -> grid.scrollTo(shown.atTime(LocalTime.of(20, 15)).atZone(zone).toInstant()));
        day.setFont(day.getFont().deriveFont(Font.BOLD));
        day.setPreferredSize(new Dimension(210, day.getFontMetrics(day.getFont()).getHeight() + 4));
        day.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel findLabel = Mnemonics.apply(new JLabel(), tr("guide.find"));
        findLabel.setLabelFor(find);
        find.addActionListener(e -> nextHit());
        find.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                findChanged();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                findChanged();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                findChanged();
            }
        });
        findResult.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(previous);
        top.add(day);
        top.add(next);
        top.add(nowButton);
        top.add(prime);
        top.add(Box.createHorizontalStrut(16));
        top.add(findLabel);
        top.add(find);
        top.add(findResult);
        add(top, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setColumnHeaderView(grid.ruler());
        scroll.setRowHeaderView(grid.channelColumn());
        scroll.getViewport().setBackground(UIManager.getColor("TextField.background"));
        add(scroll, BorderLayout.CENTER);

        detailTitle.setFont(detailTitle.getFont().deriveFont(Font.BOLD, 13f));
        detailText.setEditable(false);
        detailText.setLineWrap(true);
        detailText.setWrapStyleWord(true);
        detailText.setOpaque(false);
        detailText.setBorder(null);
        detailText.setFont(UIManager.getFont("Label.font"));
        Mnemonics.apply(watch, tr("guide.watch"));
        watch.addActionListener(e -> Optional.ofNullable(grid.selected()).ifPresent(actions::watch));
        remind.addActionListener(e -> Optional.ofNullable(grid.selected()).ifPresent(b -> {
            actions.toggleReminder(b);
            showDetails(b);
            grid.repaint();
        }));
        record.addActionListener(e -> Optional.ofNullable(grid.selected()).ifPresent(b -> {
            actions.toggleRecording(b);
            showDetails(b);
            grid.repaint();
        }));
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.add(detailTitle, BorderLayout.NORTH);
        text.add(detailText, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(watch);
        buttons.add(remind);
        buttons.add(record);
        JPanel details = new JPanel(new BorderLayout(10, 0));
        details.setBorder(new EmptyBorder(6, 8, 6, 8));
        details.add(text, BorderLayout.CENTER);
        details.add(buttons, BorderLayout.EAST);
        add(details, BorderLayout.SOUTH);

        grid.onSelect(this::showDetails);
        grid.onActivate(b -> {
            if (b.isRunningAt(this.now)) {
                actions.watch(b);
            } else if (b.start().isAfter(this.now)) {
                actions.toggleReminder(b);
                showDetails(b);
                grid.repaint();
            }
        });
        showDetails(null);
    }

    /** Called by the presenter when the shown day changed. */
    public void setDay(LocalDate d, Instant now) {
        this.shown = d;
        this.now = now;
        grid.setLayout(d.atStartOfDay(zone).toInstant(), zone);
        day.setText(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(d));
        findChanged();
    }

    public LocalDate day() {
        return shown;
    }

    public void setChannels(List<Channel> channels) {
        grid.setChannels(channels);
    }

    public void setSchedule(Schedule schedule, Instant now) {
        this.now = now;
        grid.setSchedule(schedule, now);
        if (!find.getText().isBlank()) findChanged();
        Broadcast selected = grid.selected();
        if (selected != null) showDetails(selected);
    }

    public void setReminded(Predicate<Broadcast> reminded) {
        this.reminded = reminded;
        grid.setReminded(reminded);
    }

    /** What is being recorded: the status of the program's planned or running recording, or null. */
    public void setRecording(Function<Broadcast, Recording.Status> recording) {
        this.recording = recording;
        grid.setRecording(recording);
    }

    /** Scrolls to the current time; on first show. */
    public void scrollToNow() {
        grid.scrollTo(now);
    }

    public void focusFind() {
        find.requestFocusInWindow();
        find.selectAll();
    }

    private void findChanged() {
        grid.setHighlight(find.getText());
        if (shown == null || find.getText().isBlank()) {
            hits = List.of();
            findResult.setText(" ");
            return;
        }
        hits = grid.channelsSchedule().find(find.getText(), grid.timeline().from(), grid.timeline().to())
                .stream().filter(b -> grid.channel(b.channelId()).isPresent()).toList();
        hit = -1;
        findResult.setText(hits.isEmpty() ? tr("guide.find.none") : tr("guide.find.hits", hits.size()));
    }

    private void nextHit() {
        if (hits.isEmpty()) return;
        hit = (hit + 1) % hits.size();
        grid.select(hits.get(hit));
        findResult.setText(tr("guide.find.position", hit + 1, hits.size()));
    }

    /** The text of the record button: Record, Cancel Recording or Stop Recording. */
    public static String recordKey(Recording.Status status) {
        if (status == Recording.Status.RECORDING) return "record.stop";
        return status == Recording.Status.PLANNED ? "record.cancel" : "record";
    }

    private void showDetails(Broadcast b) {
        watch.setEnabled(b != null && b.isRunningAt(now));
        remind.setEnabled(b != null && b.start().isAfter(now));
        boolean set = b != null && reminded.test(b);
        Mnemonics.apply(remind, tr(set ? "guide.remind.cancel" : "guide.remind"));
        Recording.Status rec = b == null ? null : recording.apply(b);
        record.setEnabled(b != null && b.end().isAfter(now));
        Mnemonics.apply(record, tr(recordKey(rec)));
        if (b == null) {
            detailTitle.setText(tr("guide.select"));
            detailText.setText("");
            return;
        }
        String channel = grid.channel(b.channelId()).map(Channel::name).orElse(b.channelId());
        detailTitle.setText(b.fullTitle() + "  ·  " + channel + "  ·  " + TIME.format(b.start().atZone(zone))
                + " – " + TIME.format(b.end().atZone(zone)));
        detailText.setText(b.description().isEmpty() ? tr("guide.no-description") : b.description());
        detailText.setCaretPosition(0);
    }
}
