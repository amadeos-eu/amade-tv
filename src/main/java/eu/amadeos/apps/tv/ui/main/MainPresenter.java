package eu.amadeos.apps.tv.ui.main;

import static eu.amadeos.commons.i18n.Texts.tr;

import com.google.inject.Inject;
import com.sun.jna.Native;
import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.PlayerState;
import eu.amadeos.apps.tv.domain.Reminder;
import eu.amadeos.apps.tv.repository.ChannelCatalog;
import eu.amadeos.apps.tv.repository.ReminderStore;
import eu.amadeos.apps.tv.repository.TvPreferences;
import eu.amadeos.apps.tv.service.guide.GuideService;
import eu.amadeos.apps.tv.service.guide.ZappService;
import eu.amadeos.apps.tv.service.logo.LogoCache;
import eu.amadeos.apps.tv.service.mpv.MpvPlayer;
import eu.amadeos.apps.tv.ui.guide.GuidePage;
import eu.amadeos.apps.tv.ui.live.LivePage;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Connects the window to the player, the channels and the guide. Runs on the EDT; player states and new
 * program data arrive through the EDT executor. A timer ticks every 20 seconds: progress bars, reminders,
 * and every few minutes what runs now on channels without a day schedule.
 */
public final class MainPresenter implements LivePage.Actions, GuidePage.Actions {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    static final int TICK_MILLIS = 20_000;
    static final Duration NOW_REFRESH = Duration.ofMinutes(5);
    static final double BACK_SECONDS = 600;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final MpvPlayer player;
    private final ChannelCatalog catalog;
    private final GuideService guide;
    private final ZappService zapp;
    private final LogoCache logos;
    private final ReminderStore reminders;
    private final TvPreferences preferences;
    private final EdtExecutor edt;
    private final ExecutorService executor;
    private final Clock clock;
    private final ZoneId zone;
    private MainWindow window;
    private Channel playing;
    private Timer timer;
    private Instant lastNowRefresh = Instant.EPOCH;
    private LocalDate today;
    private boolean fullScreen;

    @Inject
    public MainPresenter(MpvPlayer player, ChannelCatalog catalog, GuideService guide, ZappService zapp,
                         LogoCache logos, ReminderStore reminders, TvPreferences preferences, EdtExecutor edt,
                         ExecutorService executor, Clock clock) {
        this.player = player;
        this.catalog = catalog;
        this.guide = guide;
        this.zapp = zapp;
        this.logos = logos;
        this.reminders = reminders;
        this.preferences = preferences;
        this.edt = edt;
        this.executor = executor;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    /** Shows the window, starts the player on the last channel and loads today's program. */
    public void start() {
        LogoSource logoSource = c -> logos.logo(c, ready -> edt.execute(this::repaintLogos));
        LivePage live = new LivePage(this, zone, logoSource);
        GuidePage guidePage = new GuidePage(this, zone, logoSource);
        live.setReminded(reminders::has);
        guidePage.setReminded(reminders::has);
        window = new MainWindow(this, live, guidePage);
        window.regional.setSelected(preferences.showRegional());
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });
        showChannels();
        today = LocalDate.now(clock);
        guidePage.setDay(today, clock.instant());
        guide.addListener(() -> edt.execute(this::showSchedule));
        player.addListener(state -> edt.execute(() -> showPlayer(state)));
        window.setVisible(true);
        showPlayer(player.state());
        SwingUtilities.invokeLater(guidePage::scrollToNow);

        background(() -> {
            guide.cleanUp();
            guide.loadDay(today, catalog.all());
            guide.refreshNow(catalog.all());
            lastNowRefresh = clock.instant();
            edt.execute(this::showProblems);
        });
        background(this::refreshStreamUrls);

        timer = new Timer(TICK_MILLIS, e -> tick());
        timer.start();

        if (!player.isAvailable()) {
            live.video.setHint(tr("no-mpv.hint"));
            JOptionPane.showMessageDialog(window, tr("no-mpv.text", player.unavailableReason()), tr("app.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // The canvas is displayable now, so it has a native window for mpv.
        long windowId = Native.getComponentID(live.video);
        Channel last = catalog.byId(preferences.lastChannel());
        background(() -> {
            try {
                // mpv_initialize can take a moment (GPU detection), so not on the EDT.
                player.attach(windowId, preferences.volume());
                if (last != null) edt.execute(() -> watch(last));
            } catch (RuntimeException e) {
                LOG.error("Cannot start the player", e);
                edt.execute(() -> JOptionPane.showMessageDialog(window, tr("player.failed", e.getMessage()),
                        tr("app.title"), JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    // ---- channels and program --------------------------------------------------------------------

    private void showChannels() {
        List<Channel> shown = catalog.shown(preferences.showRegional());
        window.live.setChannels(shown);
        window.guide.setChannels(shown);
        if (playing != null) window.live.setPlaying(playing);
    }

    private void showSchedule() {
        Instant now = clock.instant();
        window.live.setSchedule(guide.schedule(), now);
        window.guide.setSchedule(guide.schedule(), now);
        updateTitle();
    }

    private void showProblems() {
        Map<Channel.GuideSourceId, String> problems = guide.problems();
        window.setSideStatus(problems.isEmpty() ? "" : tr("guide.problem", String.join(", ",
                problems.keySet().stream().map(Enum::name).toList())));
    }

    private void repaintLogos() {
        window.live.repaint();
        window.guide.repaint();
    }

    private void refreshStreamUrls() {
        try {
            Map<String, String> urls = zapp.streamUrls();
            catalog.updateStreamUrls(urls);
            edt.execute(this::showChannels);
        } catch (IOException e) {
            LOG.info("Keeping the known stream addresses: {}", e.getMessage());
        }
    }

    void tick() {
        Instant now = clock.instant();
        showSchedule();
        for (Reminder r : reminders.takeDue(now)) remind(r);
        LocalDate date = LocalDate.ofInstant(now, zone);
        if (!date.equals(today)) {
            today = date;
            background(() -> guide.loadDay(date, catalog.all()));
        }
        if (Duration.between(lastNowRefresh, now).compareTo(NOW_REFRESH) >= 0) {
            lastNowRefresh = now;
            background(() -> {
                guide.refreshNow(catalog.all());
                edt.execute(this::showProblems);
            });
        }
    }

    void refresh() {
        LocalDate day = window.guide.day();
        background(() -> {
            refreshStreamUrls();
            guide.loadDay(day == null ? today : day, catalog.all());
            guide.refreshNow(catalog.all());
            edt.execute(this::showProblems);
        });
    }

    void setShowRegional(boolean show) {
        preferences.setShowRegional(show);
        showChannels();
        showSchedule();
    }

    // ---- LivePage / GuidePage --------------------------------------------------------------------

    @Override
    public void watch(Channel c) {
        if (c == null) return;
        playing = c;
        preferences.setLastChannel(c.id());
        window.live.setPlaying(c);
        updateTitle();
        if (c.streamUrl().isBlank()) {
            window.live.video.setHint(tr("live.no-stream", c.name()));
            return;
        }
        player.open(c.streamUrl(), c.name());
    }

    @Override
    public void watch(Broadcast b) {
        Channel c = catalog.byId(b.channelId());
        if (c == null) return;
        window.showPage(MainWindow.LIVE);
        watch(c);
    }

    @Override
    public void toggleReminder(Broadcast b) {
        if (!b.start().isAfter(clock.instant())) return;
        reminders.toggle(b);
        window.live.repaint();
        window.guide.repaint();
    }

    @Override
    public void showDay(LocalDate day) {
        window.guide.setDay(day, clock.instant());
        window.guide.setSchedule(guide.schedule(), clock.instant());
        background(() -> guide.loadDay(day, catalog.all()));
    }

    /** Channel up/down in the shown list. */
    void switchChannel(int delta) {
        List<Channel> shown = catalog.shown(preferences.showRegional());
        if (shown.isEmpty()) return;
        int i = playing == null ? -1 : shown.stream().map(Channel::id).toList().indexOf(playing.id());
        watch(shown.get(Math.floorMod(i + delta, shown.size())));
    }

    private void remind(Reminder r) {
        Channel c = catalog.byId(r.channelId());
        if (c == null) return;
        Toolkit.getDefaultToolkit().beep();
        String text = tr("reminder.text", r.title(), c.name(), TIME.format(r.start().atZone(zone)));
        Object[] options = {Mnemonics.strip(tr("reminder.watch")), Mnemonics.strip(tr("reminder.dismiss"))};
        int answer = JOptionPane.showOptionDialog(window, text, tr("reminder.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (answer == 0) {
            window.showPage(MainWindow.LIVE);
            watch(c);
        }
    }

    // ---- player ----------------------------------------------------------------------------------

    private void showPlayer(PlayerState s) {
        String track = s.hasMedia() ? player.audioTrack() : "";
        window.live.showPlayer(s, track);
        if (s.volume() != preferences.volume() && s.status() != PlayerState.Status.IDLE) preferences.setVolume(s.volume());
    }

    private void updateTitle() {
        if (playing == null) {
            window.setTitle(tr("app.title"));
            return;
        }
        String now = guide.schedule().at(playing.id(), clock.instant()).map(Broadcast::title).orElse("");
        window.setTitle((now.isEmpty() ? playing.name() : playing.name() + ": " + now) + " - " + tr("app.title"));
    }

    @Override
    public void togglePause() {
        if (player.state().status() == PlayerState.Status.IDLE || player.state().status() == PlayerState.Status.FAILED) {
            if (playing != null) watch(playing);
            return;
        }
        player.togglePause();
    }

    @Override
    public void back() {
        if (player.state().hasMedia()) player.seekBy(-BACK_SECONDS);
    }

    /** Back to the live edge: the stream is opened again, which is what every HLS player does. */
    @Override
    public void toLive() {
        if (playing != null && player.state().hasMedia()) watch(playing);
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public void setVolume(int volume) {
        player.setVolume(volume);
    }

    void changeVolume(int delta) {
        player.setVolume(player.state().volume() + delta);
    }

    @Override
    public void toggleMute() {
        player.toggleMute();
    }

    @Override
    public void toggleSubtitles() {
        player.toggleSubtitles();
    }

    @Override
    public void nextAudioTrack() {
        player.nextAudioTrack();
        // mpv switches asynchronously; show the new track a moment later.
        Timer later = new Timer(400, e -> showPlayer(player.state()));
        later.setRepeats(false);
        later.start();
    }

    /** Full screen shows only the picture; F11 or Escape returns. */
    @Override
    public void toggleFullScreen() {
        GraphicsDevice device = window.getGraphicsConfiguration().getDevice();
        fullScreen = !fullScreen;
        if (fullScreen) window.showPage(MainWindow.LIVE);
        window.live.setChromeVisible(!fullScreen);
        window.sidebar.setVisible(!fullScreen);
        window.getJMenuBar().setVisible(!fullScreen);
        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(fullScreen ? window : null);
        } else {
            window.setExtendedState(fullScreen ? Frame.MAXIMIZED_BOTH : Frame.NORMAL);
        }
        installEscape();
    }

    private void installEscape() {
        JRootPane root = window.getRootPane();
        KeyStroke escape = KeyStroke.getKeyStroke("ESCAPE");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "leave-full-screen");
        root.getActionMap().put("leave-full-screen", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (fullScreen) toggleFullScreen();
            }
        });
    }

    // ---- window ----------------------------------------------------------------------------------

    void about() {
        String version = player.isAvailable() ? "libmpv " + player.apiVersion() : tr("no-mpv.short");
        Icon icon = window.getIconImages().isEmpty() ? null : new ImageIcon(window.getIconImages().get(0));
        JOptionPane.showMessageDialog(window, tr("about.text", version) + "\nhttps://github.com/datazuul/amade-tv",
                Mnemonics.strip(tr("menu.about")), JOptionPane.INFORMATION_MESSAGE, icon);
    }

    void quit() {
        if (timer != null) timer.stop();
        player.close();
        for (Window w : Window.getWindows()) w.dispose();
        executor.shutdownNow();
    }

    private void background(Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    LOG.warn("Background task failed", e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.debug("Not started after quit", e);
        }
    }
}
