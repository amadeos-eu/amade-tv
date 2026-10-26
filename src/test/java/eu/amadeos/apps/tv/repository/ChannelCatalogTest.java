package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.ChannelGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChannelCatalogTest {
    @TempDir
    Path cache;

    @Test
    void theShippedChannelsAreComplete() {
        ChannelCatalog catalog = new ChannelCatalog(cache, ChannelCatalog.shippedJson(), program -> true);
        List<Channel> all = catalog.all();
        assertTrue(all.size() > 100, "channels: " + all.size());
        Set<String> ids = new HashSet<>();
        for (Channel c : all) {
            assertTrue(ids.add(c.id()), "twice: " + c.id());
            assertTrue(c.streamUrl().startsWith("https://") || c.streamUrl().startsWith("http://"), c.id() + " " + c.streamUrl());
            assertTrue(c.logoUrl().isEmpty() || c.logoUrl().startsWith("https://raw.githubusercontent.com/jnk22/kodinerds-iptv/"), c.id());
            if (catalog.group(c) == ChannelGroup.MAIN || catalog.group(c) == ChannelGroup.REGIONAL) {
                // The public German channels: the broadcasters' own HLS streams
                assertTrue(c.streamUrl().startsWith("https://") && c.streamUrl().contains(".m3u8"), c.id());
            } else {
                assertEquals(Channel.GuideSourceId.NONE, c.guide(), c.id());
            }
        }
        assertEquals("das_erste", all.get(0).id());
        assertEquals(Channel.GuideSourceId.ARD, all.get(0).guide());
        Channel arte = all.stream().filter(c -> c.id().equals("arte")).findFirst().orElseThrow();
        assertEquals(Channel.GuideSourceId.ZDF, arte.guide());
        assertEquals(90, arte.logoTurn());
    }

    @Test
    void groupsOnlyWhenSwitchedOn() {
        ChannelCatalog catalog = new ChannelCatalog(cache);
        List<String> main = catalog.shown(Set.of()).stream().map(Channel::id).toList();
        assertEquals(List.of("das_erste", "zdf", "arte", "dreisat", "phoenix", "tagesschau24", "zdf_neo", "zdf_info",
                "kika", "ard_alpha", "one"), main);
        assertEquals(30, catalog.shown(Set.of(ChannelGroup.REGIONAL)).size());
        List<Channel> withAustria = catalog.shown(Set.of(ChannelGroup.ATCH));
        assertTrue(withAustria.stream().anyMatch(c -> c.name().equals("ORF eins")), withAustria.toString());
        assertTrue(withAustria.stream().noneMatch(c -> c.id().equals("wdr")));
        assertEquals(catalog.all().size(), catalog.shown(EnumSet.allOf(ChannelGroup.class)).size());
    }

    @Test
    void aStreamThatNeedsAMissingProgramIsLeftOut() {
        String json = """
                {"channels": [
                  {"id": "a", "name": "A", "stream": "https://a/x.m3u8"},
                  {"id": "yt", "name": "YT", "stream": "https://www.youtube.com/x/live", "needs": "yt-dlp", "group": "more"}
                ]}""";
        assertEquals(List.of("a"), new ChannelCatalog(cache, json, p -> false).all().stream().map(Channel::id).toList());
        assertEquals(2, new ChannelCatalog(cache, json, p -> p.equals("yt-dlp")).all().size());
    }

    @Test
    void addressesFromTwoServicesAddUp() {
        String json = """
                {"channels": [
                  {"id": "zdf", "name": "ZDF", "stream": "https://old/zdf.m3u8"},
                  {"id": "orf1", "name": "ORF eins", "stream": "https://old/orf1.m3u8", "group": "atch", "kodinerds": "ORF1.at"}
                ]}""";
        ChannelCatalog catalog = new ChannelCatalog(cache, json);
        assertEquals(Map.of("ORF1.at", "orf1"), catalog.kodinerdsIds());
        catalog.updateStreamUrls(Map.of("zdf", "https://zapp/zdf.m3u8"));
        catalog.updateStreamUrls(Map.of("orf1", "https://kodinerds/orf1.m3u8"));
        assertEquals("https://zapp/zdf.m3u8", catalog.byId("zdf").streamUrl());
        assertEquals("https://kodinerds/orf1.m3u8", catalog.byId("orf1").streamUrl());
        assertEquals(ChannelGroup.ATCH, catalog.group(catalog.byId("orf1")));
        assertEquals("https://zapp/zdf.m3u8", new ChannelCatalog(cache, json).byId("zdf").streamUrl());
    }

    @Test
    void theFirstFormOfRegionalChannelsStillWorks() {
        String json = "{\"channels\": [{\"id\": \"wdr\", \"name\": \"WDR\", \"stream\": \"https://w\", \"regional\": true}]}";
        ChannelCatalog catalog = new ChannelCatalog(cache, json);
        assertEquals(ChannelGroup.REGIONAL, catalog.group(catalog.byId("wdr")));
        assertTrue(catalog.shown(Set.of()).isEmpty());
    }

    @Test
    void newStreamAddressesAreUsedAndKept() throws Exception {
        ChannelCatalog catalog = new ChannelCatalog(cache);
        catalog.updateStreamUrls(Map.of("zdf", "https://new.example/zdf.m3u8", "unknown", "https://x/y.m3u8", "arte", ""));
        assertEquals("https://new.example/zdf.m3u8", catalog.byId("zdf").streamUrl());
        assertTrue(catalog.byId("arte").streamUrl().contains("artesimulcast"));
        assertNull(catalog.byId("unknown"));
        assertTrue(Files.isRegularFile(cache.resolve("stream-urls.json")));
        // The next start uses them right away
        assertEquals("https://new.example/zdf.m3u8", new ChannelCatalog(cache).byId("zdf").streamUrl());
    }

    @Test
    void anUnreadableCacheIsIgnored() throws Exception {
        Files.writeString(cache.resolve("stream-urls.json"), "{ not json");
        assertTrue(new ChannelCatalog(cache).byId("zdf").streamUrl().contains("zdf-hls"));
    }

    @Test
    void anUnknownGuideSourceMeansNone() {
        String json = "{\"channels\": [{\"id\": \"x\", \"name\": \"X\", \"stream\": \"https://x\", \"guide\": \"SAT1\"}]}";
        ChannelCatalog catalog = new ChannelCatalog(cache, json);
        assertEquals(Channel.GuideSourceId.NONE, catalog.byId("x").guide());
        assertEquals(1, catalog.shown(Set.of()).size());
    }
}
