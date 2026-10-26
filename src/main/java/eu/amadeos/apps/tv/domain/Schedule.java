package eu.amadeos.apps.tv.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The program items known for all channels. Immutable: {@link #with} returns a new schedule in which the
 * given items replace the known ones they overlap on the same channel, so a fresh day from a guide source
 * wins over an older one and over the single "now" items of the Zapp service.
 */
public final class Schedule {
    public static final Schedule EMPTY = new Schedule(Map.of());

    private final Map<String, List<Broadcast>> byChannel;

    private Schedule(Map<String, List<Broadcast>> byChannel) {
        this.byChannel = byChannel;
    }

    public static Schedule of(Collection<Broadcast> items) {
        return EMPTY.with(items);
    }

    // --8<-- [start:with]
    public Schedule with(Collection<Broadcast> items) {
        if (items.isEmpty()) return this;
        Map<String, List<Broadcast>> fresh = new HashMap<>();
        for (Broadcast b : items) fresh.computeIfAbsent(b.channelId(), k -> new ArrayList<>()).add(b);
        Map<String, List<Broadcast>> next = new HashMap<>(byChannel);
        fresh.forEach((channel, added) -> {
            TreeSet<Broadcast> merged = new TreeSet<>(added);
            for (Broadcast old : byChannel.getOrDefault(channel, List.of())) {
                boolean replaced = added.stream().anyMatch(a -> a.overlaps(old.start(), old.end())
                        || a.start().equals(old.start()));
                if (!replaced) merged.add(old);
            }
            next.put(channel, List.copyOf(merged));
        });
        return new Schedule(Map.copyOf(next));
    }
    // --8<-- [end:with]

    /** All items of a channel, by start time. */
    public List<Broadcast> of(String channelId) {
        return byChannel.getOrDefault(channelId, List.of());
    }

    public Optional<Broadcast> at(String channelId, Instant t) {
        return of(channelId).stream().filter(b -> b.isRunningAt(t)).findFirst();
    }

    /** The first item that starts after {@code t}. */
    public Optional<Broadcast> after(String channelId, Instant t) {
        return of(channelId).stream().filter(b -> b.start().isAfter(t)).findFirst();
    }

    /** The items of a channel that run (partly) between {@code from} and {@code to}. */
    public List<Broadcast> between(String channelId, Instant from, Instant to) {
        return of(channelId).stream().filter(b -> b.overlaps(from, to)).toList();
    }

    /** Items between {@code from} and {@code to} whose title, subtitle or description contains {@code text}. */
    public List<Broadcast> find(String text, Instant from, Instant to) {
        String needle = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.of();
        List<Broadcast> hits = new ArrayList<>();
        for (List<Broadcast> items : byChannel.values()) {
            for (Broadcast b : items) {
                if (b.overlaps(from, to) && matches(b, needle)) hits.add(b);
            }
        }
        hits.sort(null);
        return hits;
    }

    static boolean matches(Broadcast b, String needle) {
        return b.title().toLowerCase(Locale.ROOT).contains(needle)
                || b.subtitle().toLowerCase(Locale.ROOT).contains(needle)
                || b.description().toLowerCase(Locale.ROOT).contains(needle);
    }

    public boolean isEmpty() {
        return byChannel.isEmpty();
    }
}
