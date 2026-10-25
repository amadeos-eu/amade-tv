package eu.amadeos.apps.tv.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One program item of a channel's schedule.
 *
 * @param channelId   {@link Channel#id()}
 * @param subtitle    episode or subline, may be empty
 * @param description synopsis, may be empty
 */
public record Broadcast(String channelId, Instant start, Instant end, String title, String subtitle,
                        String description) implements Comparable<Broadcast> {

    public Broadcast {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) end = start;
        title = title == null ? "" : title.strip();
        subtitle = subtitle == null ? "" : subtitle.strip();
        description = description == null ? "" : description.strip();
    }

    public boolean isRunningAt(Instant t) {
        return !t.isBefore(start) && t.isBefore(end);
    }

    public boolean overlaps(Instant from, Instant to) {
        return start.isBefore(to) && end.isAfter(from);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    /** How much has run at {@code t}, 0 to 1. */
    public double progressAt(Instant t) {
        long total = duration().toSeconds();
        if (total <= 0) return t.isBefore(start) ? 0 : 1;
        double done = Duration.between(start, t).toSeconds() / (double) total;
        return Math.max(0, Math.min(1, done));
    }

    /** Title and subtitle as one line. */
    public String fullTitle() {
        return subtitle.isEmpty() || title.contains(subtitle) ? title : title + " – " + subtitle;
    }

    @Override
    public int compareTo(Broadcast o) {
        int c = start.compareTo(o.start);
        return c != 0 ? c : channelId.compareTo(o.channelId);
    }
}
