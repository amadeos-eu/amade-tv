package eu.amadeos.apps.tv.service.guide;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimesTest {
    static final Instant T = Instant.parse("2026-10-25T19:15:00Z");

    @Test
    void readsTheFormsTheServicesUse() {
        assertEquals(T, Times.parse("2026-10-25T19:15:00Z"));
        assertEquals(T, Times.parse("2026-10-25T19:15:00.000Z"));
        assertEquals(T, Times.parse("2026-10-25T20:15:00+01:00"));
        assertEquals(T, Times.parse(" 2026-10-25T20:15:00+01:00[Europe/Berlin] "));
        assertNull(Times.parse("heute"));
        assertNull(Times.parse(""));
        assertNull(Times.parse(null));
    }

    @Test
    void plainText() {
        assertEquals("a\nb & c", Times.plain("a<br />b &amp; <i>c</i>"));
        assertEquals("", Times.plain(null));
    }
}
