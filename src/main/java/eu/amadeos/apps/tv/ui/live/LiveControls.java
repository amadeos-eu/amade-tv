package eu.amadeos.apps.tv.ui.live;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.tv.domain.PlayerState;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.TransportButtons;
import eu.amadeos.commons.swing.TransportGlyph;

import javax.swing.*;
import java.awt.*;

/**
 * Below the picture: 10 minutes back, play/pause, back to live, stop; how far behind live the picture is;
 * volume; a status line with subtitles, audio track and full screen.
 */
public final class LiveControls extends JPanel {
    /** What the controls ask of the presenter. */
    public interface Actions {
        void togglePause();

        void back();

        void toLive();

        void stop();

        void setVolume(int volume);

        void toggleMute();

        void toggleSubtitles();

        void nextAudioTrack();

        void toggleFullScreen();
    }

    static final Color LIVE_RED = new Color(0xD8, 0x4A, 0x3A);

    final JButton rewind;
    final JButton playPause;
    final JButton toLive;
    final JButton stop;
    final JLabel live = new JLabel();
    final JToggleButton mute = new JToggleButton();
    final JSlider volume = new JSlider(0, 100, 80);
    final JToggleButton subtitles = new JToggleButton();
    final JButton audio = new JButton();
    final JButton fullScreen = new JButton();
    final JLabel status = new JLabel(" ");
    private final Icon playIcon = TransportButtons.NORMAL.icon(TransportGlyph.PLAY);
    private final Icon pauseIcon = TransportButtons.NORMAL.icon(TransportGlyph.PAUSE);
    /** Set while the slider follows the player, so that does not count as the user moving it. */
    private boolean following;

    LiveControls(Actions a) {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        TransportButtons transport = TransportButtons.NORMAL;
        rewind = transport.button(TransportGlyph.REWIND, a::back);
        playPause = transport.button(TransportGlyph.PLAY, a::togglePause);
        toLive = transport.button(TransportGlyph.NEXT, a::toLive);
        stop = transport.button(TransportGlyph.STOP, a::stop);

        live.setOpaque(true);
        live.setForeground(Color.WHITE);
        live.setFont(live.getFont().deriveFont(Font.BOLD, 12f));
        live.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        Mnemonics.apply(mute, tr("live.mute"));
        mute.addActionListener(e -> a.toggleMute());
        volume.setOpaque(false);
        volume.setFocusable(false);
        volume.setPreferredSize(new Dimension(110, volume.getPreferredSize().height));
        volume.getAccessibleContext().setAccessibleName(tr("live.volume"));
        volume.setToolTipText(tr("live.volume"));
        volume.addChangeListener(e -> {
            if (!following) a.setVolume(volume.getValue());
        });
        Mnemonics.apply(subtitles, tr("live.subtitles"));
        subtitles.addActionListener(e -> a.toggleSubtitles());
        audio.addActionListener(e -> a.nextAudioTrack());
        audio.setToolTipText(tr("live.audio.tip"));
        Mnemonics.apply(fullScreen, tr("live.full-screen"));
        fullScreen.addActionListener(e -> a.toggleFullScreen());

        // Two rows, so nothing overlaps when the picture is narrow: transport, live and volume on top,
        // the status (cut off when long) with the track and full screen buttons below.
        JPanel transportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        transportRow.setOpaque(false);
        transportRow.add(TransportButtons.bar(rewind, playPause, toLive, stop));
        transportRow.add(live);
        JPanel sound = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        sound.setOpaque(false);
        sound.add(mute);
        sound.add(volume);
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        top.add(transportRow, BorderLayout.CENTER);
        top.add(sound, BorderLayout.EAST);

        JPanel tracks = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        tracks.setOpaque(false);
        tracks.add(subtitles);
        tracks.add(audio);
        tracks.add(fullScreen);
        status.setMinimumSize(new Dimension(0, status.getPreferredSize().height));
        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(tracks, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);
        show(PlayerState.IDLE, "");
    }

    void show(PlayerState s, String audioTrack) {
        boolean media = s.hasMedia();
        boolean running = s.status() == PlayerState.Status.PLAYING && !s.paused();
        playPause.setIcon(running ? pauseIcon : playIcon);
        String label = tr(running ? "transport.pause" : "transport.play");
        playPause.setToolTipText(label);
        playPause.getAccessibleContext().setAccessibleName(label);
        playPause.setEnabled(media || s.status() == PlayerState.Status.IDLE);
        rewind.setEnabled(media);
        toLive.setEnabled(media && !s.isLive());
        stop.setEnabled(media || s.status() == PlayerState.Status.LOADING);

        live.setVisible(media);
        live.setText(liveText(s));
        live.setBackground(s.isLive() && !s.paused() ? LIVE_RED : Color.GRAY);

        following = true;
        if (!volume.getValueIsAdjusting()) volume.setValue(s.volume());
        mute.setSelected(s.muted());
        subtitles.setSelected(s.subtitles());
        following = false;
        audio.setText(tr("live.audio", audioTrack.isBlank() ? "–" : audioTrack));
        audio.setEnabled(media);
        subtitles.setEnabled(media);
        status.setText(statusText(s));
    }

    /** "● LIVE", or how far behind: "−12 min". */
    static String liveText(PlayerState s) {
        if (s.isLive()) return "● " + tr("live.on-air");
        long minutes = Math.max(1, Math.round(s.behind() / 60));
        return tr("live.behind", minutes);
    }

    static String statusText(PlayerState s) {
        return switch (s.status()) {
            case IDLE -> tr("status.idle");
            case LOADING -> tr("status.loading", s.title());
            case PLAYING -> s.paused() ? tr("status.paused", s.title()) : s.title();
            case ENDED -> tr("status.ended", s.title());
            case FAILED -> tr("status.failed", s.title(), s.error());
        };
    }
}
