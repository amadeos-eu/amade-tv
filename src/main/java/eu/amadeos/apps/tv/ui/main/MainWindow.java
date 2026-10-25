package eu.amadeos.apps.tv.ui.main;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.ui.guide.GuidePage;
import eu.amadeos.apps.tv.ui.live.LivePage;
import eu.amadeos.commons.i18n.Mnemonics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * The window: menu bar, a sidebar with the pages (Live, Guide; Library and Downloads follow in step 2) and
 * the pages themselves in a card layout.
 */
final class MainWindow extends JFrame {
    static final String LIVE = "live";
    static final String GUIDE = "guide";

    final LivePage live;
    final GuidePage guide;
    final JToggleButton liveButton = new JToggleButton();
    final JToggleButton guideButton = new JToggleButton();
    final JCheckBoxMenuItem regional = new JCheckBoxMenuItem();
    final JLabel sideStatus = new JLabel(" ");
    final JPanel sidebar = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private String page = LIVE;

    MainWindow(MainPresenter p, LivePage live, GuidePage guide) {
        super(tr("app.title"));
        this.live = live;
        this.guide = guide;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-tv.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());

        JMenuBar menus = new JMenuBar();
        JMenu file = menu("menu.file");
        file.add(item("menu.quit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK), p::quit));
        JMenu view = menu("menu.view");
        view.add(item("menu.live", KeyStroke.getKeyStroke(KeyEvent.VK_1, KeyEvent.CTRL_DOWN_MASK), () -> showPage(LIVE)));
        view.add(item("menu.guide", KeyStroke.getKeyStroke(KeyEvent.VK_2, KeyEvent.CTRL_DOWN_MASK), () -> showPage(GUIDE)));
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
        Mnemonics.apply(regional, tr("menu.regional"));
        regional.addActionListener(e -> p.setShowRegional(regional.isSelected()));
        channels.add(regional);
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
        for (JToggleButton b : new JToggleButton[]{liveButton, guideButton}) {
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
        liveButton.addActionListener(e -> showPage(LIVE));
        guideButton.addActionListener(e -> showPage(GUIDE));
        sidebar.add(Box.createVerticalGlue());
        sideStatus.setAlignmentX(0);
        sideStatus.setForeground(UIManager.getColor("Label.disabledForeground"));
        sideStatus.setFont(sideStatus.getFont().deriveFont(Font.PLAIN, 11f));
        sidebar.add(sideStatus);

        pages.add(live, LIVE);
        pages.add(guide, GUIDE);
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
        if (LIVE.equals(name)) live.focusChannels();
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
