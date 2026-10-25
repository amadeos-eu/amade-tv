package eu.amadeos.apps.tv.domain;

import java.time.Instant;
import java.util.Objects;

/** "Remind me": when {@code start} comes, the app offers to switch to the channel. */
public record Reminder(String channelId, Instant start, String title) {
    public Reminder {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(start, "start");
        title = title == null ? "" : title;
    }

    public static Reminder of(Broadcast b) {
        return new Reminder(b.channelId(), b.start(), b.fullTitle());
    }

    /** The same program: channel and start time. */
    public boolean isFor(Broadcast b) {
        return channelId.equals(b.channelId()) && start.equals(b.start());
    }
}
