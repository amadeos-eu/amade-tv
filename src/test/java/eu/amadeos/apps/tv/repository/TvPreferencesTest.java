package eu.amadeos.apps.tv.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TvPreferencesTest {
    @TempDir
    Path config;

    @Test
    void defaultsAndStoredValues() throws Exception {
        TvPreferences p = new TvPreferences(config);
        assertEquals("das_erste", p.lastChannel());
        assertEquals(80, p.volume());
        assertFalse(p.showRegional());
        p.setLastChannel("arte");
        p.setVolume(35);
        p.setShowRegional(true);
        TvPreferences again = new TvPreferences(config);
        assertEquals("arte", again.lastChannel());
        assertEquals(35, again.volume());
        assertTrue(again.showRegional());
        Files.writeString(config.resolve("tv.properties"), "volume=laut\n");
        assertEquals(80, new TvPreferences(config).volume());
    }
}
