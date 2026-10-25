package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZappServiceTest {
    @Test
    void streamAddressesOnlyOverHttp() throws IOException {
        Fixtures.FakeWeb web = new Fixtures.FakeWeb().answer(ZappService.BASE + "channelinfolist",
                Fixtures.read("zapp-channelinfolist.json"));
        Map<String, String> urls = new ZappService(web).streamUrls();
        assertEquals(3, urls.size());
        assertTrue(urls.get("das_erste").endsWith("master.m3u8"));
        assertFalse(urls.containsKey("broken"));
    }

    @Test
    void whatRunsNow() throws IOException {
        Fixtures.FakeWeb web = new Fixtures.FakeWeb().answer(ZappService.BASE + "shows/dreisat", Fixtures.read("zapp-show.json"));
        Broadcast b = new ZappService(web).now("dreisat").orElseThrow();
        assertEquals("Kulturzeit", b.title());
        assertEquals("Das Magazin", b.subtitle());
        assertEquals(Instant.parse("2026-10-25T18:20:00Z"), b.start());
        assertEquals("dreisat", b.channelId());
    }

    @Test
    void noShowOrAnErrorIsNothing() throws IOException {
        assertTrue(ZappService.parseShow("{\"shows\": []}", "x").isEmpty());
        assertTrue(ZappService.parseShow("{\"shows\": [{\"title\": \"x\"}]}", "x").isEmpty());
        assertTrue(ZappService.parseShow("{\"error\": \"show info currently not available\", \"shows\": [{\"title\": \"x\","
                + " \"startTime\": \"2026-10-25T18:20:00Z\", \"endTime\": \"2026-10-25T19:00:00Z\"}]}", "x").isEmpty());
        assertThrows(IOException.class, () -> ZappService.parseShow("<html/>", "x"));
    }
}
