package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.testing.Fixtures;
import eu.amadeos.commons.json.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static eu.amadeos.apps.tv.testing.Fixtures.channel;
import static org.junit.jupiter.api.Assertions.*;

class ZdfEpgSourceTest {
    static final List<Channel> CHANNELS = List.of(
            channel("zdf", "ZDF", Channel.GuideSourceId.ZDF, "ZDF"),
            channel("kika", "KiKA", Channel.GuideSourceId.ZDF, "KI.KA"),
            channel("arte", "ARTE", Channel.GuideSourceId.ZDF, "arte"),
            channel("das_erste", "Das Erste", Channel.GuideSourceId.ARD, "daserste"));
    static final String TOKEN = "5bb200097db507149612d7d983131d06c79706d5";

    @Test
    void findsTheTokenInThePage() throws IOException {
        assertEquals(TOKEN, ZdfEpgSource.token(Fixtures.read("zdf-live-tv.html")));
        assertEquals("abcdefghijklmnopqrstuvwxyz", ZdfEpgSource.token(
                "<script>{\"appToken\": {\"apiToken\": \"abcdefghijklmnopqrstuvwxyz\"}}</script>"));
        assertThrows(IOException.class, () -> ZdfEpgSource.token("<html>no token</html>"));
    }

    @Test
    void asksForTheBroadcastersDayInGermanTime() {
        Map<String, Object> request = Json.parseObject(ZdfEpgSource.request(Instant.parse("2026-10-24T22:00:00Z"),
                Instant.parse("2026-10-25T23:00:00Z")));
        assertEquals("getEpg", Json.str(request, "operationName"));
        Object filter = Json.member(Json.member(request, "variables"), "filter");
        assertEquals("2026-10-24T22:00:00Z", Json.str(filter, "from"));
        assertEquals("2026-10-25T23:00:00Z", Json.str(filter, "to"));
        assertEquals(ZdfEpgSource.BROADCASTERS, Json.list(filter, "broadcasterIds"));
        Object persisted = Json.member(Json.member(request, "extensions"), "persistedQuery");
        assertEquals(ZdfEpgSource.EPG_QUERY_HASH, Json.str(persisted, "sha256Hash"));
        assertEquals(1, Json.integer(persisted, "version"));
    }

    @Test
    void readsTheDay() throws IOException {
        Fixtures.FakeWeb web = new Fixtures.FakeWeb()
                .answer(ZdfEpgSource.PAGE, Fixtures.read("zdf-live-tv.html"))
                .answer(ZdfEpgSource.GRAPHQL, Fixtures.read("zdf-epg.json"));
        List<Broadcast> items = new ZdfEpgSource(web).day(LocalDate.of(2026, 10, 25), CHANNELS);
        assertEquals(List.of("GET " + ZdfEpgSource.PAGE, "POST " + ZdfEpgSource.GRAPHQL), web.requests);
        assertEquals("Bearer " + TOKEN, web.headers.get(1).get("api-auth"));
        // The day of 25 October 2026 in German time: the clocks go back that night
        Object filter = Json.member(Json.member(Json.parseObject(web.bodies.get(0)), "variables"), "filter");
        assertEquals("2026-10-24T22:00:00Z", Json.str(filter, "from"));
        assertEquals("2026-10-25T23:00:00Z", Json.str(filter, "to"));

        assertEquals(4, items.size());
        Broadcast journal = items.get(0);
        assertEquals("zdf", journal.channelId());
        assertEquals("heute journal", journal.title());
        assertEquals(Instant.parse("2026-10-25T20:45:00Z"), journal.start());
        assertEquals("Vom Ätna bis Island.\nTeil 1.", items.get(1).description());
        assertEquals("kika", items.get(2).channelId());
        Broadcast arte = items.get(3);
        assertEquals("arte", arte.channelId());
        assertEquals("", arte.subtitle());
        assertEquals("", arte.description());
    }

    @Test
    void theTokenIsReadOnceAndAgainWhenRefused() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        Fixtures.FakeWeb web = new Fixtures.FakeWeb().answer(ZdfEpgSource.PAGE, Fixtures.read("zdf-live-tv.html"));
        web.answers.put(ZdfEpgSource.GRAPHQL, url -> calls.incrementAndGet() == 2 ? null : Fixtures.read("zdf-epg.json"));
        ZdfEpgSource source = new ZdfEpgSource(web);
        source.day(LocalDate.of(2026, 10, 25), CHANNELS);
        source.day(LocalDate.of(2026, 10, 26), CHANNELS);
        assertEquals(List.of("GET " + ZdfEpgSource.PAGE, "POST " + ZdfEpgSource.GRAPHQL,
                "POST " + ZdfEpgSource.GRAPHQL, "GET " + ZdfEpgSource.PAGE, "POST " + ZdfEpgSource.GRAPHQL), web.requests);
    }

    @Test
    void graphQlErrorsAreErrors() {
        assertThrows(IOException.class, () -> ZdfEpgSource.parse(
                "{\"errors\": [{\"message\": \"PersistedQueryNotFound\"}]}", CHANNELS));
        assertThrows(IOException.class, () -> ZdfEpgSource.parse("Forbidden", CHANNELS));
    }
}
