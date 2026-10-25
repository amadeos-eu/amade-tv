package eu.amadeos.apps.tv.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReminderTest {
    @Test
    void aReminderBelongsToChannelAndStart() {
        Instant t = Instant.parse("2026-10-25T19:15:00Z");
        Broadcast tatort = new Broadcast("das_erste", t, t.plusSeconds(5400), "Tatort", "Nebel", "");
        Reminder r = Reminder.of(tatort);
        assertEquals("Tatort – Nebel", r.title());
        assertTrue(r.isFor(tatort));
        assertFalse(r.isFor(new Broadcast("zdf", t, t.plusSeconds(60), "Tatort", "", "")));
        assertFalse(r.isFor(new Broadcast("das_erste", t.plusSeconds(60), t.plusSeconds(120), "Tatort", "", "")));
    }
}
