package eu.amadeos.apps.tv.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleTest {
    static final Instant T = Instant.parse("2026-10-25T19:00:00Z");

    static Broadcast b(String channel, int fromMinute, int toMinute, String title) {
        return new Broadcast(channel, T.plusSeconds(fromMinute * 60L), T.plusSeconds(toMinute * 60L), title, "",
                title.equals("Tatort") ? "Ein Krimi am Hafen" : "");
    }

    @Test
    void itemsAreSortedPerChannel() {
        Schedule s = Schedule.of(List.of(b("a", 15, 105, "Tatort"), b("a", 0, 15, "Tagesschau"), b("b", 0, 30, "heute")));
        assertEquals(List.of("Tagesschau", "Tatort"), s.of("a").stream().map(Broadcast::title).toList());
        assertEquals("Tatort", s.at("a", T.plusSeconds(20 * 60)).orElseThrow().title());
        assertEquals("Tatort", s.after("a", T.plusSeconds(60)).orElseThrow().title());
        assertTrue(s.at("a", T.plusSeconds(200 * 60)).isEmpty());
        assertTrue(s.of("c").isEmpty());
        assertFalse(s.isEmpty());
    }

    // --8<-- [start:replace]
    @Test
    void newItemsReplaceTheOverlappingOldOnes() {
        // Zapp knew only "now", the day schedule then brings the real items.
        Schedule s = Schedule.of(List.of(b("a", 0, 30, "Nachrichten"), b("a", 60, 90, "Später")));
        s = s.with(List.of(b("a", 0, 15, "Tagesschau"), b("a", 15, 45, "Wetter")));
        assertEquals(List.of("Tagesschau", "Wetter", "Später"), s.of("a").stream().map(Broadcast::title).toList());
        // Other channels stay
        Schedule other = s.with(List.of(b("b", 0, 30, "heute")));
        assertEquals(3, other.of("a").size());
        assertSame(s, s.with(List.of()));
    }
    // --8<-- [end:replace]

    @Test
    void theSameItemTwiceIsKeptOnce() {
        Schedule s = Schedule.of(List.of(b("a", 0, 15, "Tagesschau"))).with(List.of(b("a", 0, 15, "Tagesschau")));
        assertEquals(1, s.of("a").size());
    }

    @Test
    void betweenAndFind() {
        Schedule s = Schedule.of(List.of(b("a", 0, 15, "Tagesschau"), b("a", 15, 105, "Tatort"), b("b", 30, 60, "Tatort-Doku")));
        assertEquals(1, s.between("a", T.plusSeconds(600), T.plusSeconds(700)).size());
        assertEquals(2, s.between("a", T.minusSeconds(60), T.plusSeconds(20 * 60)).size());
        List<Broadcast> hits = s.find(" tatort ", T.minusSeconds(3600), T.plusSeconds(7200));
        assertEquals(List.of("Tatort", "Tatort-Doku"), hits.stream().map(Broadcast::title).toList());
        assertEquals(1, s.find("hafen", T.minusSeconds(3600), T.plusSeconds(7200)).size());
        assertEquals(List.of(), s.find("", T, T.plusSeconds(7200)));
        assertEquals(List.of(), s.find("tatort", T.plusSeconds(200 * 60), T.plusSeconds(300 * 60)));
    }
}
