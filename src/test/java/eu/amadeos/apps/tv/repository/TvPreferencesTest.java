package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.ChannelGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TvPreferencesTest {
    @TempDir
    Path config;

    @Test
    void defaultsAndStoredValues() throws Exception {
        TvPreferences p = new TvPreferences(config);
        assertEquals("das_erste", p.lastChannel());
        assertEquals(80, p.volume());
        assertTrue(p.groups().isEmpty());
        p.setLastChannel("arte");
        p.setVolume(35);
        p.setGroup(ChannelGroup.REGIONAL, true);
        p.setGroup(ChannelGroup.INTERNATIONAL, true);
        p.setGroup(ChannelGroup.REGIONAL, false);
        TvPreferences again = new TvPreferences(config);
        assertEquals("arte", again.lastChannel());
        assertEquals(35, again.volume());
        assertEquals(Set.of(ChannelGroup.INTERNATIONAL), again.groups());
        Files.writeString(config.resolve("tv.properties"), "volume=laut\n");
        assertEquals(80, new TvPreferences(config).volume());
    }

    @Test
    void theRegionalSettingOfTheFirstVersionCarriesOver() throws Exception {
        Files.writeString(config.resolve("tv.properties"), "regional=true\n");
        TvPreferences p = new TvPreferences(config);
        assertEquals(Set.of(ChannelGroup.REGIONAL), p.groups());
        p.setGroup(ChannelGroup.ATCH, true);
        assertEquals(Set.of(ChannelGroup.REGIONAL, ChannelGroup.ATCH), new TvPreferences(config).groups());
    }
}
