package eu.amadeos.apps.tv.domain;

import eu.amadeos.apps.tv.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class RecordingTest {
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final Instant START = Instant.parse("2026-10-26T19:15:00Z");
    static final Channel ERSTE = Fixtures.channel("das_erste", "Das Erste", Channel.GuideSourceId.ARD);
    static final Broadcast TATORT = new Broadcast("das_erste", START, START.plus(Duration.ofMinutes(90)),
            "Tatort", "Tod am Deich", "");

    @Test
    void planStartsBeforeAndEndsAfterTheProgram() {
        Recording r = Recording.plan(TATORT, ERSTE, Path.of("/v"), START.minusSeconds(3600), BERLIN);
        assertEquals(START.minus(Recording.LEAD), r.start());
        assertEquals(TATORT.end().plus(Recording.TAIL), r.end());
        assertEquals(Path.of("/v/Tatort – Tod am Deich (Das Erste, 2026-10-26 20.15).mkv"), r.file());
        assertEquals(Recording.Status.PLANNED, r.status());
        assertTrue(r.isFor(TATORT));
    }

    @Test
    void aRunningProgramIsRecordedFromNow() {
        Instant now = START.plusSeconds(600);
        Recording r = Recording.plan(TATORT, ERSTE, Path.of("/v"), now, BERLIN);
        assertEquals(now, r.start());
        assertTrue(r.isDue(now));
        assertEquals(Duration.ofMinutes(85), r.remaining(now));
    }

    @Test
    void dueAndMissed() {
        Recording r = Recording.plan(TATORT, ERSTE, Path.of("/v"), START.minusSeconds(3600), BERLIN);
        assertFalse(r.isDue(START.minusSeconds(3600)));
        assertTrue(r.isDue(START));
        assertFalse(r.isMissed(START));
        assertTrue(r.isMissed(r.end()));
        assertFalse(r.withStatus(Recording.Status.DONE, "").isMissed(r.end()));
        assertEquals(Duration.ZERO, r.remaining(r.end().plusSeconds(1)));
    }

    @Test
    void fileNamesHaveNoCharactersFileSystemsRefuse() {
        assertEquals("AC_DC_ live_ _ (ONE, t).mkv", Recording.fileName("AC/DC: live? *", "ONE", "t"));
        assertEquals("ONE (ONE, t).mkv", Recording.fileName("", "ONE", "t"));
        assertTrue(Recording.fileName("x".repeat(400), "ONE", "t").length() <= 184);
    }
}
