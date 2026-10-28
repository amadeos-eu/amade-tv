package eu.amadeos.apps.tv.ui.main;

import eu.amadeos.apps.tv.domain.ChannelGroup;
import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.ui.guide.GuidePage;
import eu.amadeos.apps.tv.ui.library.LibraryPage;
import eu.amadeos.apps.tv.ui.live.LivePage;
import eu.amadeos.apps.tv.ui.recordings.RecordingsPage;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * The window: menu bar, a sidebar with the pages (Live, Guide, Recordings, Library) and the pages
 * themselves in a card layout.
 */
final class MainWindow extends JFrame {
    static final String LIVE = "live";
    static final String GUIDE = "guide";
    static final String RECORDINGS = "recordings";
    static final String LIBRARY = "library";

    final LivePage live;
    final GuidePage guide;
    final RecordingsPage recordings;
    final LibraryPage library;
    final JToggleButton liveButton = new JToggleButton();
    final JToggleButton guideButton = new JToggleButton();
    final JToggleButton recordingsButton = new JToggleButton();
    final JToggleButton libraryButton = new JToggleButton();
    /** The groups of channels besides the main ones, each switched on with its menu item. */
    final Map<ChannelGroup, JCheckBoxMenuItem> groups = new EnumMap<>(ChannelGroup.class);
    final JLabel sideStatus = new JLabel(" ");
    final JPanel sidebar = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final MainPresenter presenter;
    private String page = LIVE;

    MainWindow(MainPresenter p, LivePage live, GuidePage guide, RecordingsPage recordings, LibraryPage library) {
        super(tr("app.title"));
        this.live = live;
        this.guide = guide;
        this.recordings = recordings;
        this.library = library;
        this.presenter = p;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-tv.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());

        JMenuBar menus = new JMenuBar();
        JMenu file = menu("menu.file");
        file.add(item("menu.quit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), p::quit));
        JMenu view = menu("menu.view");
        view.add(item("menu.live", KeyStroke.getKeyStroke(KeyEvent.VK_1, KeyEvent.CTRL_DOWN_MASK), () -> showPage(LIVE)));
        view.add(item("menu.guide", KeyStroke.getKeyStroke(KeyEvent.VK_2, KeyEvent.CTRL_DOWN_MASK), () -> showPage(GUIDE)));
        view.add(item("menu.recordings", KeyStroke.getKeyStroke(KeyEvent.VK_3, KeyEvent.CTRL_DOWN_MASK), () -> showPage(RECORDINGS)));
        view.add(item("menu.library", KeyStroke.getKeyStroke(KeyEvent.VK_4, KeyEvent.CTRL_DOWN_MASK), () -> showPage(LIBRARY)));
        view.addSeparator();
        view.add(item("menu.find", KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), () -> {
            showPage(GUIDE);
            guide.focusFind();
        }));
        view.add(item("menu.full-screen", KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), p::toggleFullScreen));
        JMenu playback = menu("menu.playback");
        playback.add(item("menu.play-pause", KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK), p::togglePause));
        playback.add(item("menu.back", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_DOWN_MASK), p::back));
        playback.add(item("menu.to-live", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK), p::toLive));
        playback.add(item("menu.stop", null, p::stop));
        playback.addSeparator();
        playback.add(item("menu.mute", KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK), p::toggleMute));
        playback.add(item("menu.louder", KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), () -> p.changeVolume(5)));
        playback.add(item("menu.quieter", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), () -> p.changeVolume(-5)));
        playback.add(item("menu.subtitles", KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_DOWN_MASK), p::toggleSubtitles));
        playback.add(item("menu.audio", KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), p::nextAudioTrack));
        JMenu channels = menu("menu.channels");
        channels.add(item("menu.previous-channel", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, KeyEvent.CTRL_DOWN_MASK), () -> p.switchChannel(-1)));
        channels.add(item("menu.next-channel", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, KeyEvent.CTRL_DOWN_MASK), () -> p.switchChannel(1)));
        channels.addSeparator();
        for (ChannelGroup g : ChannelGroup.values()) {
            if (g == ChannelGroup.MAIN) continue;
            JCheckBoxMenuItem item = new JCheckBoxMenuItem();
            Mnemonics.apply(item, tr("menu.group." + g.key()));
            item.addActionListener(e -> p.setGroup(g, item.isSelected()));
            groups.put(g, item);
            channels.add(item);
        }
        channels.addSeparator();
        channels.add(item("menu.refresh", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), p::refresh));
        JMenu help = menu("menu.help");
        help.add(item("menu.about", null, p::about));
        menus.add(file);
        menus.add(view);
        menus.add(playback);
        menus.add(channels);
        menus.add(Box.createHorizontalGlue());
        menus.add(help);
        setJMenuBar(menus);

        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(8, 8, 8, 8));
        ButtonGroup group = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[]{liveButton, guideButton, recordingsButton, libraryButton}) {
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setAlignmentX(0);
            Dimension size = new Dimension(130, 30);
            b.setPreferredSize(size);
            b.setMaximumSize(size);
            b.setFocusable(false);
            group.add(b);
            sidebar.add(b);
            sidebar.add(Box.createVerticalStrut(4));
        }
        Mnemonics.apply(liveButton, tr("page.live"));
        Mnemonics.apply(guideButton, tr("page.guide"));
        Mnemonics.apply(recordingsButton, tr("page.recordings"));
        Mnemonics.apply(libraryButton, tr("page.library"));
        liveButton.addActionListener(e -> showPage(LIVE));
        guideButton.addActionListener(e -> showPage(GUIDE));
        recordingsButton.addActionListener(e -> showPage(RECORDINGS));
        libraryButton.addActionListener(e -> showPage(LIBRARY));
        sidebar.add(Box.createVerticalGlue());
        sideStatus.setAlignmentX(0);
        sideStatus.setForeground(UIManager.getColor("Label.disabledForeground"));
        sideStatus.setFont(sideStatus.getFont().deriveFont(Font.PLAIN, 11f));
        sidebar.add(sideStatus);

        pages.add(live, LIVE);
        pages.add(guide, GUIDE);
        pages.add(recordings, RECORDINGS);
        pages.add(library, LIBRARY);
        JPanel content = new JPanel(new BorderLayout());
        content.add(sidebar, BorderLayout.WEST);
        content.add(pages, BorderLayout.CENTER);
        setContentPane(content);
        showPage(LIVE);
        pack();
        setSize(Math.max(getWidth(), 1100), Math.max(getHeight(), 720));
        setLocationRelativeTo(null);
    }

    void showPage(String name) {
        page = name;
        cards.show(pages, name);
        liveButton.setSelected(LIVE.equals(name));
        guideButton.setSelected(GUIDE.equals(name));
        recordingsButton.setSelected(RECORDINGS.equals(name));
        libraryButton.setSelected(LIBRARY.equals(name));
        if (LIVE.equals(name)) live.focusChannels();
        if (LIBRARY.equals(name)) presenter.libraryShown();
    }

    String page() {
        return page;
    }

    /** Status in the sidebar, e.g. which program source did not answer. */
    void setSideStatus(String html) {
        sideStatus.setText(html == null || html.isBlank() ? " " : "<html>" + html + "</html>");
    }

    private static JMenu menu(String key) {
        return Mnemonics.apply(new JMenu(), tr(key));
    }

    private static JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), tr(key));
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener((ActionEvent e) -> action.run());
        return item;
    }
}
