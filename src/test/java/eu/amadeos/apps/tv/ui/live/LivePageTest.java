package eu.amadeos.apps.tv.ui.live;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Recording;
import eu.amadeos.apps.tv.domain.PlayerState;
import eu.amadeos.apps.tv.domain.Schedule;
import eu.amadeos.apps.tv.ui.main.LogoSource;
import eu.amadeos.commons.i18n.Texts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static eu.amadeos.apps.tv.testing.Fixtures.channel;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Live page without a player; needs a display (CI runs the tests under Xvfb). */
class LivePageTest {
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final Instant NOW = Instant.parse("2026-10-25T19:30:00Z");
    static final Channel ERSTE = channel("das_erste", "Das Erste", Channel.GuideSourceId.ARD);
    static final Channel ARTE = channel("arte", "ARTE", Channel.GuideSourceId.ZDF);
    static final Broadcast NEWS = new Broadcast("arte", Instant.parse("2026-10-25T18:40:00Z"),
            Instant.parse("2026-10-25T19:15:00Z"), "Arte Journal", "", "");
    static final Broadcast COASTS = new Broadcast("arte", Instant.parse("2026-10-25T19:15:00Z"),
            Instant.parse("2026-10-25T20:00:00Z"), "Wilde Küsten Europas", "Teil 1", "");
    static final Broadcast FILM = new Broadcast("arte", Instant.parse("2026-10-25T20:45:00Z"),
            Instant.parse("2026-10-25T22:20:00Z"), "Die stille Bucht", "", "");

    @BeforeAll
    static void texts() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Texts.use(LivePageTest.class, "eu.amadeos.apps.tv.messages");
    }

    static final class Recorder implements LivePage.Actions {
        final List<String> calls = new ArrayList<>();
        final Set<Broadcast> reminded = new HashSet<>();

        public void watch(Channel c) {
            calls.add("watch " + c.id());
        }

        public void toggleReminder(Broadcast b) {
            calls.add("remind " + b.title());
            if (!reminded.remove(b)) reminded.add(b);
        }

        final Map<Broadcast, Recording.Status> recording = new HashMap<>();

        public void toggleRecording(Broadcast b) {
            calls.add("record " + b.title());
            Recording.Status s = recording.get(b);
            if (s == null) recording.put(b, b.start().isAfter(NOW) ? Recording.Status.PLANNED : Recording.Status.RECORDING);
            else recording.remove(b);
        }

        public void togglePause() {
            calls.add("pause");
        }

        public void back() {
            calls.add("back");
        }

        public void toLive() {
            calls.add("live");
        }

        public void stop() {
            calls.add("stop");
        }

        public void setVolume(int volume) {
            calls.add("volume " + volume);
        }

        public void toggleMute() {
            calls.add("mute");
        }

        public void toggleSubtitles() {
            calls.add("subtitles");
        }

        public void nextAudioTrack() {
            calls.add("audio");
        }

        public void toggleFullScreen() {
            calls.add("full screen");
        }
    }

    LivePage page(Recorder r) throws Exception {
        LivePage[] page = new LivePage[1];
        SwingUtilities.invokeAndWait(() -> {
            page[0] = new LivePage(r, BERLIN, LogoSource.NONE);
            page[0].setReminded(r.reminded::contains);
            page[0].setChannels(List.of(ERSTE, ARTE));
            page[0].setSchedule(Schedule.of(List.of(NEWS, COASTS, FILM)), NOW);
            page[0].setPlaying(ARTE);
        });
        return page[0];
    }

    @Test
    void theChannelThatPlaysShowsItsDay() throws Exception {
        Recorder r = new Recorder();
        LivePage p = page(r);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(ARTE, p.channels.getSelectedValue());
            assertEquals("Today on ARTE", p.todayBorder.getTitle());
            assertEquals(3, p.todayModel.size());
            p.today.setSelectedIndex(2);
            assertTrue(p.remind.isEnabled());
            p.remind.doClick();
            assertEquals("Cancel Reminder", p.remind.getText());
            p.today.setSelectedIndex(1);
            assertFalse(p.remind.isEnabled());
            p.channels.setSelectedIndex(0);
            p.channels.getActionMap().get("watch").actionPerformed(null);
        });
        assertEquals(List.of("remind Die stille Bucht", "watch das_erste"), r.calls);
    }

    @Test
    void controlsFollowThePlayer() throws Exception {
        Recorder r = new Recorder();
        LivePage p = page(r);
        SwingUtilities.invokeAndWait(() -> {
            PlayerState playing = PlayerState.IDLE.withVolume(55).loading("ARTE").withStatus(PlayerState.Status.PLAYING);
            p.showPlayer(playing, "deu");
            LiveControls c = p.controls;
            assertEquals("● LIVE", c.live.getText());
            assertFalse(c.toLive.isEnabled());
            assertEquals(55, c.volume.getValue());
            assertEquals("Audio: deu", c.audio.getText());
            assertEquals("ARTE", c.status.getText());
            p.showPlayer(playing.withBehind(600), "deu");
            assertEquals("−10 min", c.live.getText());
            assertTrue(c.toLive.isEnabled());
            c.rewind.doClick();
            c.toLive.doClick();
            c.mute.doClick();
            c.subtitles.doClick();
            c.audio.doClick();
            c.fullScreen.doClick();
            c.volume.setValue(30);
            p.showPlayer(PlayerState.IDLE.loading("ARTE").failed("HTTP 403"), "");
            assertTrue(c.status.getText().startsWith("Cannot play ARTE: HTTP 403."), c.status.getText());
            assertFalse(c.live.isVisible());
        });
        assertEquals(List.of("back", "live", "mute", "subtitles", "audio", "full screen", "volume 30"), r.calls);
    }

    @Test
    void theChannelCellDescribesWhatRuns() throws Exception {
        LivePage p = page(new Recorder());
        SwingUtilities.invokeAndWait(() -> {
            ChannelCell cell = (ChannelCell) p.channels.getCellRenderer();
            assertEquals("ARTE: Wilde Küsten Europas – Teil 1, 20:15 to 21:00", cell.describe(ARTE));
            assertEquals("Das Erste", cell.describe(ERSTE));
            Component c = cell.getListCellRendererComponent(p.channels, ARTE, 1, true, false);
            c.setSize(320, ChannelCell.HEIGHT);
            BufferedImage img = new BufferedImage(320, ChannelCell.HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            c.paint(g);
            g.dispose();
        });
    }

    @Test
    void fullScreenHidesEverythingButThePicture() throws Exception {
        LivePage p = page(new Recorder());
        SwingUtilities.invokeAndWait(() -> {
            p.setChromeVisible(false);
            assertFalse(p.channelScroll.isVisible());
            assertFalse(p.controls.isVisible());
            assertTrue(p.video.isVisible());
            assertEquals(new Insets(0, 0, 0, 0), p.right.getInsets(), "no margin around the picture");
            p.setChromeVisible(true);
            assertTrue(p.controls.isVisible());
            assertEquals(new Insets(6, 6, 6, 6), p.right.getInsets());
        });
    }
}
