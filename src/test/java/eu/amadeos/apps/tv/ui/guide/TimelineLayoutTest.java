package eu.amadeos.apps.tv.ui.guide;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimelineLayoutTest {
    static final Instant FROM = Instant.parse("2026-10-25T23:00:00Z");
    final TimelineLayout layout = new TimelineLayout(FROM, FROM.plusSeconds(24 * 3600), 220, 44);

    @Test
    void timeAndPixels() {
        assertEquals(24 * 220, layout.width());
        assertEquals(0, layout.x(FROM));
        assertEquals(330, layout.x(FROM.plusSeconds(5400)));
        assertEquals(FROM.plusSeconds(5400), layout.at(330));
        assertEquals(0, layout.row(0));
        assertEquals(1, layout.row(44));
        assertEquals(-1, layout.row(-3));
    }

    @Test
    void cellsAreClippedToTheDayAndNeverVanish() {
        assertArrayEquals(new int[]{0, 110}, layout.span(FROM.minusSeconds(3600), FROM.plusSeconds(1800)));
        assertArrayEquals(new int[]{layout.width() - 55, layout.width()},
                layout.span(FROM.plusSeconds(24 * 3600 - 900), FROM.plusSeconds(26 * 3600)));
        int[] tiny = layout.span(FROM.plusSeconds(3600), FROM.plusSeconds(3610));
        assertEquals(3, tiny[1] - tiny[0]);
    }
}
