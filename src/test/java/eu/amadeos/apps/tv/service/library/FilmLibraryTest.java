package eu.amadeos.apps.tv.service.library;

import eu.amadeos.apps.tv.domain.Film;
import eu.amadeos.apps.tv.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilmLibraryTest {
    @Test
    void readsAPageOfFilms() throws IOException {
        FilmLibrary.Page page = FilmLibrary.parse(Fixtures.read("tv-api-films.json"));
        assertEquals(51, page.total());
        assertEquals(0, page.page());
        assertTrue(page.hasMore());
        Film tatort = page.films().getFirst();
        assertEquals("Tatort: Tod am Deich", tatort.fullTitle());
        assertEquals("Das Erste", tatort.channel());
        assertEquals(Instant.parse("2026-10-25T19:15:00Z"), tatort.publishedAt());
        assertEquals(Duration.ofSeconds(5310), tatort.duration());
        assertEquals(Instant.parse("2026-11-24T22:59:00Z"), tatort.availableUntil());
        assertEquals("https://example.org/t.m3u8", tatort.videoUrl());
    }

    @Test
    void missingValuesAreEmpty() throws IOException {
        Film report = FilmLibrary.parse(Fixtures.read("tv-api-films.json")).films().get(1);
        assertEquals("Nordseereport", report.fullTitle());
        assertEquals("", report.description());
        assertNull(report.publishedAt());
        assertNull(report.duration());
        assertNull(report.availableUntil());
        assertTrue(report.audioDescription());
        assertEquals("https://example.org/n-sd.mp4", report.videoUrl());
    }

    @Test
    void asksTheServiceWithTheWordsAndThePage() throws IOException {
        Fixtures.FakeWeb web = new Fixtures.FakeWeb().answer("http://localhost:8085/tv/v1/films",
                "{\"total\":0,\"page\":2,\"films\":[]}");
        FilmLibrary.Page page = new FilmLibrary(web, "http://localhost:8085/tv/v1").search(" tatort \"am see\" ", 2);
        assertEquals(List.of("GET http://localhost:8085/tv/v1/films?q=tatort+%22am+see%22&page=2&size=50"),
                web.requests);
        assertFalse(page.hasMore());
    }

    @Test
    void anAnswerThatIsNoJsonFails() {
        assertThrows(IOException.class, () -> FilmLibrary.parse("<html>Bad Gateway</html>"));
    }
}
