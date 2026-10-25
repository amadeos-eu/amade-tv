package eu.amadeos.apps.tv.service.mpv;

import eu.amadeos.apps.tv.domain.PlayerState;
import org.junit.jupiter.api.Test;

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

    @Test
    void recognisesTheSoftwareRendererWarning() {
        assertTrue(MpvPlayer.isSoftwareRenderer(new MpvEvent.Log("warn", "vo/gpu/opengl",
                "Suspected software renderer or indirect context.")));
        assertFalse(MpvPlayer.isSoftwareRenderer(new MpvEvent.Log("warn", "vo/gpu/vaapi", "libva: vaGetDriverNames() failed")));
    }

    @Test
    void systemPropertiesOverrideTheDefaults() {
        System.setProperty("mpv.gpu-context", "auto");
        try {
            assertEquals("auto", MpvPlayer.overrides().get("gpu-context"));
        } finally {
            System.clearProperty("mpv.gpu-context");
        }
        assertFalse(MpvPlayer.overrides().containsKey("gpu-context"));
    }
}
