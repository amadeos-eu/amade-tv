package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Reminder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReminderStoreTest {
    static final Instant NOW = Instant.parse("2026-10-25T18:00:00Z");

    @TempDir
    Path config;

    static Broadcast at(String channel, int minutes, String title) {
        Instant start = NOW.plusSeconds(minutes * 60L);
        return new Broadcast(channel, start, start.plusSeconds(1800), title, "", "");
    }

    @Test
    void toggleSetsAndCancelsAndIsKept() {
        ReminderStore store = new ReminderStore(config, NOW);
        Broadcast tatort = at("das_erste", 135, "Tatort");
        Broadcast news = at("zdf", 30, "heute");
        assertTrue(store.toggle(tatort));
        assertTrue(store.toggle(news));
        assertTrue(store.has(tatort));
        assertEquals(List.of("heute", "Tatort"), store.all().stream().map(Reminder::title).toList());
        ReminderStore again = new ReminderStore(config, NOW);
        assertEquals(2, again.all().size());
        assertFalse(again.toggle(tatort));
        assertFalse(new ReminderStore(config, NOW).has(tatort));
    }

    @Test
    void dueRemindersAreTakenOnce() {
        ReminderStore store = new ReminderStore(config, NOW);
        store.toggle(at("zdf", 30, "heute"));
        store.toggle(at("das_erste", 135, "Tatort"));
        assertEquals(List.of(), store.takeDue(NOW.plusSeconds(29 * 60)));
        assertEquals(List.of("heute"), store.takeDue(NOW.plusSeconds(31 * 60)).stream().map(Reminder::title).toList());
        assertEquals(List.of(), store.takeDue(NOW.plusSeconds(32 * 60)));
        // Far too late (the computer slept): dropped without a dialog
        assertEquals(List.of(), store.takeDue(NOW.plusSeconds(200 * 60)));
        assertTrue(store.all().isEmpty());
    }

    @Test
    void oldRemindersAreDroppedWhenLoading() {
        new ReminderStore(config, NOW).toggle(at("zdf", 30, "heute"));
        assertTrue(new ReminderStore(config, NOW.plusSeconds(3600)).all().isEmpty());
    }

    @Test
    void anUnreadableFileIsIgnored() throws Exception {
        Files.writeString(config.resolve("reminders.json"), "[{\"channel\": \"zdf\", \"start\": \"gestern\"}]");
        assertTrue(new ReminderStore(config, NOW).all().isEmpty());
    }
}
