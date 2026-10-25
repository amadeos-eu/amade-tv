package eu.amadeos.apps.tv.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStateTest {
    @Test
    void liveUntilHalfAMinuteBehind() {
        PlayerState s = PlayerState.IDLE.loading("ZDF").withStatus(PlayerState.Status.PLAYING);
        assertTrue(s.isLive());
        assertTrue(s.withBehind(29).isLive());
        assertFalse(s.withBehind(600).isLive());
        assertEquals(0, s.withBehind(-5).behind());
    }

    @Test
    void anotherChannelKeepsVolumeMuteAndSubtitles() {
        PlayerState s = PlayerState.IDLE.withVolume(40).withMuted(true).withSubtitles(true).loading("ZDF")
                .withStatus(PlayerState.Status.PLAYING).withBehind(600).withPosition(12).failed("x");
        PlayerState next = s.loading("ARTE");
        assertEquals(PlayerState.Status.LOADING, next.status());
        assertEquals("ARTE", next.title());
        assertEquals(40, next.volume());
        assertTrue(next.muted());
        assertTrue(next.subtitles());
        assertEquals(0, next.behind());
        assertEquals("", next.error());
    }

    @Test
    void volumeIsClamped() {
        assertEquals(100, PlayerState.IDLE.withVolume(130).volume());
        assertEquals(0, PlayerState.IDLE.withVolume(-3).volume());
    }

    @Test
    void onlyAFailureKeepsTheError() {
        PlayerState failed = PlayerState.IDLE.loading("ZDF").failed("HTTP 403");
        assertEquals("HTTP 403", failed.error());
        assertEquals("", failed.withStatus(PlayerState.Status.LOADING).error());
        assertFalse(failed.hasMedia());
    }
}
