package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Reminder;
import eu.amadeos.commons.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The reminders in {@code ~/.config/amadeos/tv/reminders.json}; past ones are dropped when loading. */
public final class ReminderStore {
    private static final Logger LOG = LoggerFactory.getLogger(ReminderStore.class);

    private final Path file;
    private final List<Reminder> reminders = new ArrayList<>();

    public ReminderStore(Path configDirectory, Instant now) {
        this.file = configDirectory.resolve("reminders.json");
        load(now);
    }

    public synchronized List<Reminder> all() {
        return List.copyOf(reminders);
    }

    public synchronized boolean has(Broadcast b) {
        return reminders.stream().anyMatch(r -> r.isFor(b));
    }

    /** Adds the reminder, or removes it if there is one already; true if it is set afterwards. */
    public synchronized boolean toggle(Broadcast b) {
        boolean removed = reminders.removeIf(r -> r.isFor(b));
        if (!removed) {
            reminders.add(Reminder.of(b));
            reminders.sort(Comparator.comparing(Reminder::start));
        }
        save();
        return !removed;
    }

    /** The reminders due at {@code now} (started within the last ten minutes), removed from the store. */
    public synchronized List<Reminder> takeDue(Instant now) {
        List<Reminder> due = reminders.stream().filter(r -> !r.start().isAfter(now)).toList();
        if (due.isEmpty()) return List.of();
        reminders.removeAll(due);
        save();
        Instant late = now.minusSeconds(600);
        return due.stream().filter(r -> r.start().isAfter(late)).toList();
    }

    private void load(Instant now) {
        if (!Files.isRegularFile(file)) return;
        try {
            for (Object o : Json.parseArray(Files.readString(file))) {
                Instant start = Instant.parse(Json.str(o, "start"));
                if (start.isAfter(now.minusSeconds(600))) {
                    reminders.add(new Reminder(Json.str(o, "channel"), start, Json.str(o, "title")));
                }
            }
            reminders.sort(Comparator.comparing(Reminder::start));
        } catch (IOException | IllegalArgumentException | DateTimeParseException e) {
            LOG.warn("Ignoring unreadable {}", file, e);
        }
    }

    private void save() {
        List<Object> list = new ArrayList<>();
        for (Reminder r : reminders) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("channel", r.channelId());
            o.put("start", r.start().toString());
            o.put("title", r.title());
            list.add(o);
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), "reminders", ".tmp");
            Files.writeString(tmp, Json.write(list), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("Cannot save the reminders in {}", file, e);
        }
    }
}
