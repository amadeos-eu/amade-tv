package eu.amadeos.apps.tv.domain;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * A program to record from a live stream, or recorded. The recording starts a little before the program
 * and ends a little after it ({@link #LEAD}, {@link #TAIL}), because broadcasts rarely keep to the minute.
 *
 * @param programStart the start of the program in the guide; with the channel, it names the program
 * @param start        when recording begins, {@link #LEAD} before the program
 * @param end          when recording ends, {@link #TAIL} after the program
 * @param bytes        size of the file so far
 * @param error        why it failed, empty otherwise
 */
public record Recording(String id, String channelId, String channelName, String title, Instant programStart,
                        Instant start, Instant end, Path file, Status status, long bytes, String error) {
    public static final Duration LEAD = Duration.ofMinutes(2);
    public static final Duration TAIL = Duration.ofMinutes(5);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm");

    public enum Status {
        /** Waits for its start time. */
        PLANNED,
        RECORDING,
        /** Recorded to the end. */
        DONE,
        /** Stopped by the user or because TV was closed; the file holds what was recorded. */
        STOPPED,
        FAILED;

        public boolean isOver() {
            return this == DONE || this == STOPPED || this == FAILED;
        }
    }

    public Recording {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(status, "status");
        channelName = channelName == null ? channelId : channelName;
        title = title == null ? "" : title;
        programStart = programStart == null ? start : programStart;
        error = error == null ? "" : error;
    }

    // --8<-- [start:plan]
    /**
     * A recording of the program, saved in {@code folder}. A program that runs already is recorded from
     * {@code now} on.
     */
    public static Recording plan(Broadcast b, Channel channel, Path folder, Instant now, ZoneId zone) {
        Instant start = b.start().minus(LEAD);
        if (start.isBefore(now)) start = now;
        String name = fileName(b.fullTitle(), channel.name(), FILE_TIME.format(b.start().atZone(zone)));
        Path file;
        try {
            file = folder.resolve(name);
        } catch (InvalidPathException e) {
            // A system without a UTF-8 locale cannot name files "Tödliche Brandung".
            file = folder.resolve(name.replaceAll("[^\\x20-\\x7e]", "_"));
        }
        return new Recording(UUID.randomUUID().toString(), channel.id(), channel.name(), b.fullTitle(), b.start(),
                start, b.end().plus(TAIL), file, Status.PLANNED, 0, "");
    }

    /** "Tatort – Tod am Deich (Das Erste, 2026-10-26 20.15).mkv", without characters file systems refuse. */
    static String fileName(String title, String channel, String time) {
        String name = (title.isBlank() ? channel : title) + " (" + channel + ", " + time + ")";
        name = name.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (name.length() > 180) name = name.substring(0, 180).strip();
        return name + ".mkv";
    }
    // --8<-- [end:plan]

    /** The same program: channel and start time in the guide. */
    public boolean isFor(Broadcast b) {
        return channelId.equals(b.channelId()) && programStart.equals(b.start());
    }

    /** Planned, and its time has come. */
    public boolean isDue(Instant now) {
        return status == Status.PLANNED && !now.isBefore(start) && now.isBefore(end);
    }

    /** Planned, but its time is over: TV was not running. */
    public boolean isMissed(Instant now) {
        return status == Status.PLANNED && !now.isBefore(end);
    }

    /** How long it records from {@code from} until its end. */
    public Duration remaining(Instant from) {
        Duration d = Duration.between(from, end);
        return d.isNegative() ? Duration.ZERO : d;
    }

    public Recording withStatus(Status s, String why) {
        return new Recording(id, channelId, channelName, title, programStart, start, end, file, s, bytes, why);
    }

    public Recording withBytes(long b) {
        return new Recording(id, channelId, channelName, title, programStart, start, end, file, status, b, error);
    }
}
