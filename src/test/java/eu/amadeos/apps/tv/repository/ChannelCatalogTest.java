package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
        List<Channel> all = new ChannelCatalog(cache).all();
        assertEquals(30, all.size());
        Set<String> ids = new HashSet<>();
        for (Channel c : all) {
            assertTrue(ids.add(c.id()), "twice: " + c.id());
            assertTrue(c.streamUrl().startsWith("https://"), c.id() + " " + c.streamUrl());
            assertTrue(c.streamUrl().contains(".m3u8"), c.id());
            assertTrue(c.logoUrl().isEmpty() || c.logoUrl().startsWith("https://raw.githubusercontent.com/jnk22/kodinerds-iptv/"), c.id());
        }
        assertEquals("das_erste", all.get(0).id());
        assertEquals(Channel.GuideSourceId.ARD, all.get(0).guide());
        Channel arte = all.stream().filter(c -> c.id().equals("arte")).findFirst().orElseThrow();
        assertEquals(Channel.GuideSourceId.ZDF, arte.guide());
        assertEquals(90, arte.logoTurn());
    }

    @Test
    void regionalChannelsOnlyWhenAskedFor() {
        ChannelCatalog catalog = new ChannelCatalog(cache);
        List<String> main = catalog.shown(false).stream().map(Channel::id).toList();
        assertEquals(List.of("das_erste", "zdf", "arte", "dreisat", "phoenix", "tagesschau24", "zdf_neo", "zdf_info",
                "kika", "ard_alpha", "one"), main);
        assertEquals(30, catalog.shown(true).size());
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
        assertEquals(1, catalog.shown(false).size());
    }
}
