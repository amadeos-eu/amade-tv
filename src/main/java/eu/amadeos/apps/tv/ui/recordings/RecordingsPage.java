package eu.amadeos.apps.tv.ui.recordings;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.domain.Recording;
import eu.amadeos.apps.tv.service.record.Recorder;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;

/**
 * The recordings: planned, running and finished ones in a table, with Play, Show in Folder, Stop or Cancel,
 * and Remove. Below, where the files go and that TV records only while it is open.
 */
public final class RecordingsPage extends JPanel {
    /** What the page asks of the presenter. */
    public interface Actions {
        void play(Recording r);

        void showInFolder(Recording r);

        void stopRecording(Recording r);

        void removeRecording(Recording r);
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    final Model model = new Model();
    final JTable table = new JTable(model);
    final JButton play = new JButton();
    final JButton folder = new JButton();
    final JButton stop = new JButton();
    final JButton remove = new JButton();
    final JLabel footer = new JLabel();
    final JLabel empty = new JLabel(tr("recordings.empty"), SwingConstants.CENTER);
    private final ZoneId zone;
    private ToLongFunction<Recording> bytes = Recording::bytes;

    public RecordingsPage(Actions actions, ZoneId zone, Path folderPath) {
        super(new BorderLayout());
        this.zone = zone;
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 8);
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(190);
        table.getColumnModel().getColumn(3).setPreferredWidth(240);
        table.getAccessibleContext().setAccessibleName(tr("page.recordings"));
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Recording r = selected();
                if (e.getClickCount() == 2 && r != null && canPlay(r)) actions.play(r);
            }
        });

        Mnemonics.apply(play, tr("recordings.play"));
        Mnemonics.apply(folder, tr("recordings.folder"));
        Mnemonics.apply(remove, tr("recordings.remove"));
        play.addActionListener(e -> withSelected(actions::play));
        folder.addActionListener(e -> withSelected(actions::showInFolder));
        stop.addActionListener(e -> withSelected(actions::stopRecording));
        remove.addActionListener(e -> withSelected(actions::removeRecording));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttons.add(play);
        buttons.add(folder);
        buttons.add(stop);
        buttons.add(remove);
        add(buttons, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        JPanel center = new JPanel(new CardLayout());
        empty.setForeground(UIManager.getColor("Label.disabledForeground"));
        center.add(scroll, "table");
        center.add(empty, "empty");
        add(center, BorderLayout.CENTER);

        footer.setText(tr("recordings.footer", folderPath));
        footer.setForeground(UIManager.getColor("Label.disabledForeground"));
        footer.setBorder(new EmptyBorder(6, 8, 6, 8));
        add(footer, BorderLayout.SOUTH);
        setRecordings(List.of(), Recording::bytes);
    }

    /** Shows the recordings; {@code bytes} gives the size so far of a running one. Keeps the selection. */
    public void setRecordings(List<Recording> recordings, ToLongFunction<Recording> bytes) {
        this.bytes = bytes;
        Recording before = selected();
        model.set(recordings);
        if (before != null) {
            for (int i = 0; i < recordings.size(); i++) {
                if (recordings.get(i).id().equals(before.id())) table.setRowSelectionInterval(i, i);
            }
        }
        JPanel center = (JPanel) empty.getParent();
        ((CardLayout) center.getLayout()).show(center, recordings.isEmpty() ? "empty" : "table");
        updateButtons();
    }

    Recording selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.rows.size() ? null : model.rows.get(row);
    }

    private void withSelected(java.util.function.Consumer<Recording> action) {
        Recording r = selected();
        if (r != null) action.accept(r);
    }

    private static boolean canPlay(Recording r) {
        return r.status() != Recording.Status.PLANNED && java.nio.file.Files.isRegularFile(r.file());
    }

    private void updateButtons() {
        Recording r = selected();
        play.setEnabled(r != null && canPlay(r));
        folder.setEnabled(r != null);
        boolean running = r != null && r.status() == Recording.Status.RECORDING;
        stop.setEnabled(r != null && !r.status().isOver());
        Mnemonics.apply(stop, tr(running ? "record.stop" : "record.cancel"));
        remove.setEnabled(r != null);
    }

    // --8<-- [start:status]
    /** "Recording, 1.2 GB", "Recorded, 3.4 GB", "Missed: TV was not open". */
    String status(Recording r) {
        String size = size(bytes.applyAsLong(r));
        return switch (r.status()) {
            case PLANNED -> tr("recordings.status.planned");
            case RECORDING -> tr("recordings.status.recording", size);
            case DONE -> tr("recordings.status.done", size);
            case STOPPED -> tr("recordings.status.stopped", size);
            case FAILED -> switch (r.error()) {
                case Recorder.MISSED -> tr("recordings.status.missed");
                case Recorder.NO_STREAM -> tr("recordings.status.no-stream");
                default -> tr("recordings.status.failed", r.error());
            };
        };
    }
    // --8<-- [end:status]

    static String size(long bytes) {
        if (bytes < 1_000_000) return String.format(Locale.getDefault(), "%d kB", bytes / 1000);
        if (bytes < 1_000_000_000) return String.format(Locale.getDefault(), "%d MB", bytes / 1_000_000);
        return String.format(Locale.getDefault(), "%.1f GB", bytes / 1e9);
    }

    final class Model extends AbstractTableModel {
        List<Recording> rows = List.of();

        void set(List<Recording> recordings) {
            boolean sameRows = rows.stream().map(Recording::id).toList()
                    .equals(recordings.stream().map(Recording::id).toList());
            rows = List.copyOf(recordings);
            // Progress only: the rows stay, and so does the selection.
            if (sameRows && !rows.isEmpty()) fireTableRowsUpdated(0, rows.size() - 1);
            else fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return tr(switch (column) {
                case 0 -> "recordings.program";
                case 1 -> "recordings.channel";
                case 2 -> "recordings.when";
                default -> "recordings.status";
            });
        }

        @Override
        public Object getValueAt(int row, int column) {
            Recording r = rows.get(row);
            return switch (column) {
                case 0 -> r.title().isBlank() ? r.channelName() : r.title();
                case 1 -> r.channelName();
                case 2 -> DAY.format(r.programStart().atZone(zone)) + "  " + TIME.format(r.start().atZone(zone))
                        + " – " + TIME.format(r.end().atZone(zone));
                default -> status(r);
            };
        }
    }
}
