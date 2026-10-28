package eu.amadeos.apps.tv.ui.library;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.domain.Film;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * The media libraries: a search over the films the broadcasters offer on demand, the hits in a table
 * (newest first without words, best first with them), the description of the selected film below, and
 * Play, which shows the film on the Live page.
 */
public final class LibraryPage extends JPanel {
    /** What the page asks of the presenter. */
    public interface Actions {
        /** A new search; empty text lists the newest films. */
        void search(String text);

        /** The next page of the same search. */
        void moreFilms();

        void play(Film f);
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    final JTextField find = new JTextField(30);
    final JButton play = new JButton();
    final JButton more = new JButton();
    final Model model = new Model();
    final JTable table = new JTable(model);
    final JTextArea details = new JTextArea(6, 40);
    final JLabel status = new JLabel(" ");
    private final ZoneId zone;
    private boolean searched;

    public LibraryPage(Actions actions, ZoneId zone) {
        super(new BorderLayout());
        this.zone = zone;
        JLabel findLabel = Mnemonics.apply(new JLabel(), tr("library.find"));
        findLabel.setLabelFor(find);
        find.addActionListener(e -> actions.search(find.getText()));
        Mnemonics.apply(play, tr("library.play"));
        play.addActionListener(e -> withSelected(actions::play));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(findLabel);
        top.add(find);
        top.add(play);
        add(top, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 8);
        table.getColumnModel().getColumn(0).setPreferredWidth(420);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getAccessibleContext().setAccessibleName(tr("page.library"));
        table.getSelectionModel().addListSelectionListener(e -> showSelected());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) withSelected(actions::play);
            }
        });
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBorder(new EmptyBorder(6, 8, 6, 8));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(details));
        split.setResizeWeight(0.75);
        add(split, BorderLayout.CENTER);

        Mnemonics.apply(more, tr("library.more"));
        more.addActionListener(e -> actions.moreFilms());
        status.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(new EmptyBorder(4, 8, 4, 8));
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(more, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        setFilms(List.of(), 0, false);
    }

    /** True once the first search was asked for; the presenter lists the newest films on the first visit. */
    public boolean searched() {
        return searched;
    }

    public void focusFind() {
        find.requestFocusInWindow();
        find.selectAll();
    }

    public void setLoading() {
        searched = true;
        status.setText(tr("library.loading"));
        more.setEnabled(false);
    }

    /** The films found so far ({@code total} in all); keeps the selection when more were added. */
    public void setFilms(List<Film> films, long total, boolean hasMore) {
        Film before = selected();
        model.set(films);
        if (before != null && films.contains(before)) {
            int i = films.indexOf(before);
            table.setRowSelectionInterval(i, i);
        }
        status.setText(!searched ? " " : total == 0 ? tr("library.none") : tr("library.count", films.size(), total));
        more.setVisible(hasMore);
        more.setEnabled(hasMore);
        showSelected();
    }

    /** The service did not answer; what was shown stays. */
    public void setFailed(String reason) {
        status.setText(tr("library.failed", reason));
        more.setEnabled(more.isVisible());
    }

    Film selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.rows.size() ? null : model.rows.get(row);
    }

    private void withSelected(java.util.function.Consumer<Film> action) {
        Film f = selected();
        if (f != null && !f.videoUrl().isBlank()) action.accept(f);
    }

    private void showSelected() {
        Film f = selected();
        play.setEnabled(f != null && !f.videoUrl().isBlank());
        details.setText(f == null ? "" : details(f));
        details.setCaretPosition(0);
    }

    // --8<-- [start:details]
    /** Title, channel, time and length, how long it can be watched, then the description. */
    String details(Film f) {
        List<String> facts = new ArrayList<>();
        facts.add(f.channel());
        if (f.publishedAt() != null) facts.add(when(f));
        if (f.duration() != null) facts.add(minutes(f.duration()));
        if (f.audioDescription()) facts.add(tr("library.audio-description"));
        if (f.signLanguage()) facts.add(tr("library.sign-language"));
        StringBuilder text = new StringBuilder(f.fullTitle()).append('\n').append(String.join(", ", facts));
        if (f.availableUntil() != null) {
            text.append('\n').append(tr("library.until", DAY.format(f.availableUntil().atZone(zone))));
        }
        if (!f.description().isBlank()) text.append("\n\n").append(f.description());
        return text.toString();
    }
    // --8<-- [end:details]

    private String when(Film f) {
        return DAY.format(f.publishedAt().atZone(zone)) + "  " + TIME.format(f.publishedAt().atZone(zone));
    }

    static String minutes(Duration d) {
        return tr("library.minutes", Math.max(1, Math.round(d.toSeconds() / 60.0)));
    }

    final class Model extends AbstractTableModel {
        List<Film> rows = List.of();

        void set(List<Film> films) {
            rows = List.copyOf(films);
            fireTableDataChanged();
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
                case 0 -> "library.title";
                case 1 -> "library.channel";
                case 2 -> "library.when";
                default -> "library.length";
            });
        }

        @Override
        public Object getValueAt(int row, int column) {
            Film f = rows.get(row);
            return switch (column) {
                case 0 -> f.fullTitle();
                case 1 -> f.channel();
                case 2 -> f.publishedAt() == null ? "" : when(f);
                default -> f.duration() == null ? "" : minutes(f.duration());
            };
        }
    }
}
