package eu.amadeos.apps.tv.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BroadcastTest {
    static final Instant T = Instant.parse("2026-10-25T19:00:00Z");
    private final Broadcast news = new Broadcast("das_erste", T, T.plusSeconds(900), " Tagesschau ", null, null);

    @Test
    void runsFromStartUpToButNotIncludingTheEnd() {
        assertTrue(news.isRunningAt(T));
        assertTrue(news.isRunningAt(T.plusSeconds(899)));
        assertFalse(news.isRunningAt(T.plusSeconds(900)));
        assertFalse(news.isRunningAt(T.minusSeconds(1)));
        assertEquals(Duration.ofMinutes(15), news.duration());
    }

    @Test
    void progressIsClamped() {
        assertEquals(0, news.progressAt(T.minusSeconds(60)));
        assertEquals(0.5, news.progressAt(T.plusSeconds(450)), 1e-9);
        assertEquals(1, news.progressAt(T.plusSeconds(5000)));
        Broadcast empty = new Broadcast("x", T, T, "x", "", "");
        assertEquals(1, empty.progressAt(T));
        assertEquals(0, empty.progressAt(T.minusSeconds(1)));
    }

    @Test
    void anEndBeforeTheStartIsTheStart() {
        assertEquals(T, new Broadcast("x", T, T.minusSeconds(10), "", "", "").end());
    }

    @Test
    void fullTitleJoinsTheSubtitleOnce() {
        assertEquals("Tagesschau", news.fullTitle());
        Broadcast tatort = new Broadcast("x", T, T, "Tatort", "Nebel über dem Hafen", "");
        assertEquals("Tatort – Nebel über dem Hafen", tatort.fullTitle());
        assertEquals("Terra X: Vulkane", new Broadcast("x", T, T, "Terra X: Vulkane", "Vulkane", "").fullTitle());
    }

    @Test
    void overlaps() {
        assertTrue(news.overlaps(T.plusSeconds(100), T.plusSeconds(200)));
        assertTrue(news.overlaps(T.minusSeconds(100), T.plusSeconds(1)));
        assertFalse(news.overlaps(T.plusSeconds(900), T.plusSeconds(1000)));
        assertFalse(news.overlaps(T.minusSeconds(100), T));
    }
}
