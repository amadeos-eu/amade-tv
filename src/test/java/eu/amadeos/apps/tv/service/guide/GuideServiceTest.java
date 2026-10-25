package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.testing.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static eu.amadeos.apps.tv.testing.Fixtures.channel;
import static org.junit.jupiter.api.Assertions.*;

class GuideServiceTest {
    static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    static final Instant EVENING = Instant.parse("2026-10-25T19:30:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 10, 25);
    static final Channel ERSTE = channel("das_erste", "Das Erste", Channel.GuideSourceId.ARD);
    static final Channel ZDF = channel("zdf", "ZDF", Channel.GuideSourceId.ZDF);
    static final Channel PARLIAMENT = channel("parlamentsfernsehen_1", "Parlamentsfernsehen 1", Channel.GuideSourceId.NONE);
    static final List<Channel> CHANNELS = List.of(ERSTE, ZDF, PARLIAMENT);

    @TempDir
    Path cache;

    /** A source that answers one item per channel and day, or fails. */
    static final class CountingSource implements GuideSource {
        final Channel.GuideSourceId id;
        final List<LocalDate> asked = new ArrayList<>();
        boolean fail;

        CountingSource(Channel.GuideSourceId id) {
            this.id = id;
        }

        @Override
        public Channel.GuideSourceId id() {
            return id;
        }

        @Override
        public List<Broadcast> day(LocalDate day, List<Channel> channels) throws IOException {
            asked.add(day);
            if (fail) throw new IOException("HTTP 503");
            Instant start = day.atTime(20, 15).atZone(BROADCAST_ZONE).toInstant();
            return channels.stream().map(c -> new Broadcast(c.id(), start, start.plusSeconds(5400),
                    id + " " + day, "", "")).toList();
        }
    }

    static Clock at(Instant t) {
        return Clock.fixed(t, ZoneId.of("Europe/Berlin"));
    }

    @Test
    void loadsEachSourceForItsChannelsAndKeepsTheDay() {
        CountingSource ard = new CountingSource(Channel.GuideSourceId.ARD);
        CountingSource zdf = new CountingSource(Channel.GuideSourceId.ZDF);
        AtomicInteger changes = new AtomicInteger();
        GuideService guide = new GuideService(List.of(ard, zdf), new ZappService(new Fixtures.FakeWeb()), cache, at(NOW));
        guide.addListener(changes::incrementAndGet);
        guide.loadDay(DAY, CHANNELS);
        // ARD's day begins at 5 am: the day before is needed for the early hours
        assertEquals(List.of(DAY, DAY.minusDays(1)), ard.asked);
        assertEquals(List.of(DAY), zdf.asked);
        assertEquals("ARD 2026-10-25", guide.schedule().at("das_erste", Instant.parse("2026-10-25T19:30:00Z")).orElseThrow().title());
        assertEquals(1, guide.schedule().of("zdf").size());
        assertEquals(1, changes.get());
        assertTrue(Files.isRegularFile(cache.resolve("guide/ZDF-2026-10-25.json")));

        // A second start within six hours reads the disk
        GuideService later = new GuideService(List.of(ard, zdf), new ZappService(new Fixtures.FakeWeb()), cache,
                at(NOW.plusSeconds(5 * 3600)));
        later.loadDay(DAY, CHANNELS);
        assertEquals(1, zdf.asked.size());
        assertEquals(1, later.schedule().of("zdf").size());

        // After six hours today is asked for again, the past day never
        GuideService evening = new GuideService(List.of(ard, zdf), new ZappService(new Fixtures.FakeWeb()), cache,
                at(NOW.plusSeconds(7 * 3600)));
        evening.loadDay(DAY, CHANNELS);
        assertEquals(2, zdf.asked.size());
        assertEquals(List.of(DAY, DAY.minusDays(1), DAY), ard.asked);
    }

    @Test
    void aFailingSourceKeepsWhatIsOnDiskAndSaysSo() {
        CountingSource zdf = new CountingSource(Channel.GuideSourceId.ZDF);
        new GuideService(List.of(zdf), new ZappService(new Fixtures.FakeWeb()), cache, at(NOW)).loadDay(DAY, CHANNELS);
        zdf.fail = true;
        GuideService guide = new GuideService(List.of(zdf), new ZappService(new Fixtures.FakeWeb()), cache,
                at(NOW.plusSeconds(8 * 3600)));
        guide.loadDay(DAY, CHANNELS);
        assertEquals(1, guide.schedule().of("zdf").size());
        assertEquals("HTTP 503", guide.problems().get(Channel.GuideSourceId.ZDF));
        zdf.fail = false;
        guide.loadDay(DAY, CHANNELS);
        assertTrue(guide.problems().isEmpty());
    }

    @Test
    void zappFillsWhatTheSourcesDoNotKnowAndIsNotAskedTooOften() {
        Fixtures.FakeWeb web = new Fixtures.FakeWeb()
                .answer(ZappService.BASE + "shows/parlamentsfernsehen_1", "{\"shows\": [{\"title\": \"Plenarsitzung\","
                        + " \"startTime\": \"2026-10-25T19:00:00Z\", \"endTime\": \"2026-10-25T21:00:00Z\"}]}");
        GuideService guide = new GuideService(List.of(), new ZappService(web), cache, at(EVENING));
        guide.refreshNow(CHANNELS);
        assertEquals("Plenarsitzung", guide.schedule().at("parlamentsfernsehen_1", EVENING).orElseThrow().title());
        assertEquals(3, web.requests.size());
        // Known now: not asked again; the others failed and wait five minutes
        guide.refreshNow(CHANNELS);
        assertEquals(3, web.requests.size());
    }

    @Test
    void theDiskCacheRoundTrips() {
        Path file = cache.resolve("guide/ARD-2026-10-25.json");
        Broadcast b = new Broadcast("das_erste", NOW, NOW.plusSeconds(60), "Tatort", "Nebel \"am\" Hafen", "Zeile 1\nZeile 2");
        GuideService.write(file, NOW, List.of(b));
        GuideService.Cached cached = GuideService.read(file).orElseThrow();
        assertEquals(NOW, cached.fetched());
        assertEquals(List.of(b), cached.items());
    }

    @Test
    void cleanUpDeletesDaysOlderThanAWeek() throws IOException {
        Files.createDirectories(cache.resolve("guide"));
        Files.writeString(cache.resolve("guide/ARD-2026-10-01.json"), "{}");
        Files.writeString(cache.resolve("guide/ZDF-2026-10-24.json"), "{}");
        Files.writeString(cache.resolve("guide/notes.txt"), "x");
        new GuideService(List.of(), new ZappService(new Fixtures.FakeWeb()), cache, at(NOW)).cleanUp();
        assertFalse(Files.exists(cache.resolve("guide/ARD-2026-10-01.json")));
        assertTrue(Files.exists(cache.resolve("guide/ZDF-2026-10-24.json")));
        assertTrue(Files.exists(cache.resolve("guide/notes.txt")));
    }
}
