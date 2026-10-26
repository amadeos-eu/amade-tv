package eu.amadeos.apps.tv.service.mpv;

import eu.amadeos.apps.tv.domain.PlayerState;
import eu.amadeos.commons.mpv.Mpv;
import eu.amadeos.commons.mpv.MpvEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** The state machine behind the observed properties, without libmpv. */
class MpvPlayerTest {
    private static PlayerState change(PlayerState s, long id, Object value) {
        return MpvPlayer.propertyChanged(s, new MpvEvent.PropertyChange(id, "", value));
    }

    @Test
    void propertiesFillTheState() {
        PlayerState s = PlayerState.IDLE.loading("ZDF").withStatus(PlayerState.Status.PLAYING);
        s = change(s, MpvPlayer.TIME, 61.5);
        s = change(s, MpvPlayer.PAUSE, true);
        s = change(s, MpvPlayer.VOLUME, 42.4);
        s = change(s, MpvPlayer.MUTE, true);
        s = change(s, MpvPlayer.SUB_VISIBLE, true);
        s = change(s, MpvPlayer.TITLE, "master.m3u8");
        assertEquals(61.5, s.position());
        assertTrue(s.paused());
        assertEquals(42, s.volume());
        assertTrue(s.muted());
        assertTrue(s.subtitles());
        // The channel name stays the title, not the playlist's file name
        assertEquals("ZDF", s.title());
        assertEquals(-1, change(s, MpvPlayer.TIME, null).position());
        assertEquals(42, change(s, MpvPlayer.VOLUME, null).volume());
    }

    @Test
    void aStreamThatEnds() {
        PlayerState playing = PlayerState.IDLE.loading("a").withStatus(PlayerState.Status.PLAYING);
        assertEquals(PlayerState.Status.ENDED, change(playing, MpvPlayer.EOF, true).status());
        assertEquals(PlayerState.Status.LOADING, change(PlayerState.IDLE.loading("b"), MpvPlayer.EOF, true).status());
    }

    // --8<-- [start:behind]
    @Test
    void aPauseLetsThePictureFallBehindLive() {
        MutableClock clock = new MutableClock();
        MpvPlayer player = new MpvPlayer(new Mpv(), clock);
        player.handle(new MpvEvent.FileLoaded());
        player.handle(new MpvEvent.PropertyChange(MpvPlayer.PAUSE, "pause", true));
        clock.advance(Duration.ofSeconds(90));
        player.handle(new MpvEvent.PropertyChange(MpvPlayer.PAUSE, "pause", false));
        assertEquals(90, player.state().behind(), 0.001);
        assertFalse(player.state().isLive());
        // Forward never goes past live; without libmpv the command is dropped, the state still follows.
        player.seekBy(600);
        assertEquals(0, player.state().behind(), 0.001);
        assertTrue(player.state().isLive());
    }
    // --8<-- [end:behind]

    @Test
    void anErrorBecomesTheFailedState() {
        MpvPlayer player = new MpvPlayer(new Mpv(), Clock.systemUTC());
        player.handle(new MpvEvent.EndFile(MpvEvent.END_ERROR, -13));
        assertEquals(PlayerState.Status.FAILED, player.state().status());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-25T20:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
