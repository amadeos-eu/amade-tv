package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Schedule;
import eu.amadeos.commons.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects the program items of all channels into one {@link Schedule}: whole days from the guide sources,
 * and for channels without a day schedule what runs now from the Zapp service.
 *
 * <p>The services have no published terms, so the app stays polite: a day is kept on disk
 * ({@code ~/.cache/amadeos/tv/guide/ARD-2026-10-25.json}) and asked for again after {@link #FRESH} at the
 * earliest (past days never), and Zapp is asked about a channel at most every {@link #ZAPP_PAUSE}.
 * The methods block; the UI calls them from the background executor.
 */
public final class GuideService {
    private static final Logger LOG = LoggerFactory.getLogger(GuideService.class);
    static final Duration FRESH = Duration.ofHours(6);
    static final Duration ZAPP_PAUSE = Duration.ofMinutes(5);

    private final Map<Channel.GuideSourceId, GuideSource> sources = new EnumMap<>(Channel.GuideSourceId.class);
    private final ZappService zapp;
    private final Path cacheDirectory;
    private final Clock clock;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Instant> zappAsked = new ConcurrentHashMap<>();
    private final Map<Channel.GuideSourceId, String> problems = new ConcurrentHashMap<>();
    private volatile Schedule schedule = Schedule.EMPTY;

    public GuideService(List<GuideSource> sources, ZappService zapp, Path cacheDirectory, Clock clock) {
        for (GuideSource s : sources) this.sources.put(s.id(), s);
        this.zapp = zapp;
        this.cacheDirectory = cacheDirectory.resolve("guide");
        this.clock = clock;
    }

    public Schedule schedule() {
        return schedule;
    }

    /** Called (on a background thread) whenever the schedule changed. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Why a source gave nothing the last time, e.g. for the status line; empty if all answered. */
    public Map<Channel.GuideSourceId, String> problems() {
        return Map.copyOf(problems);
    }

    // --8<-- [start:load]
    /**
     * Loads the day (in the broadcasters' time) for all channels that have a day schedule, from disk if fresh
     * enough, otherwise from the sources. ARD's day starts at 5 am, so its previous day is loaded as well for
     * the early hours.
     */
    public void loadDay(LocalDate day, List<Channel> channels) {
        List<Broadcast> items = new ArrayList<>();
        for (GuideSource source : sources.values()) {
            List<Channel> served = channels.stream().filter(c -> c.guide() == source.id()).toList();
            if (served.isEmpty()) continue;
            items.addAll(day(source, day, served));
            if (source.id() == Channel.GuideSourceId.ARD) items.addAll(day(source, day.minusDays(1), served));
        }
        merge(items);
    }
    // --8<-- [end:load]

    // --8<-- [start:now]
    /**
     * For each channel with nothing known at this moment, asks Zapp what runs now. Also fills gaps when a
     * source fails.
     */
    public void refreshNow(List<Channel> channels) {
        Instant now = clock.instant();
        List<Broadcast> items = new ArrayList<>();
        for (Channel c : channels) {
            if (schedule.at(c.id(), now).isPresent()) continue;
            Instant asked = zappAsked.get(c.id());
            if (asked != null && Duration.between(asked, now).compareTo(ZAPP_PAUSE) < 0) continue;
            zappAsked.put(c.id(), now);
            try {
                zapp.now(c.id()).ifPresent(items::add);
            } catch (IOException e) {
                LOG.debug("Zapp knows nothing about {} now: {}", c.id(), e.getMessage());
            }
        }
        merge(items);
    }
    // --8<-- [end:now]

    // --8<-- [start:day]
    private List<Broadcast> day(GuideSource source, LocalDate day, List<Channel> channels) {
        Path file = cacheDirectory.resolve(source.id() + "-" + day + ".json");
        Optional<Cached> cached = read(file);
        Instant now = clock.instant();
        boolean past = day.isBefore(LocalDate.ofInstant(now, GuideSource.BROADCAST_ZONE));
        if (cached.isPresent() && (past || Duration.between(cached.get().fetched(), now).compareTo(FRESH) < 0)) {
            return cached.get().items();
        }
        try {
            List<Broadcast> items = source.day(day, channels);
            problems.remove(source.id());
            if (!items.isEmpty()) write(file, now, items);
            return items;
        } catch (IOException | RuntimeException e) {
            LOG.warn("No {} program for {}: {}", source.id(), day, e.getMessage());
            problems.put(source.id(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return cached.map(Cached::items).orElse(List.of());
        }
    }
    // --8<-- [end:day]

    private void merge(List<Broadcast> items) {
        if (items.isEmpty()) return;
        synchronized (this) {
            schedule = schedule.with(items);
        }
        for (Runnable l : listeners) {
            try {
                l.run();
            } catch (RuntimeException e) {
                LOG.warn("Guide listener failed", e);
            }
        }
    }

    // ---- disk cache ------------------------------------------------------------------------------

    record Cached(Instant fetched, List<Broadcast> items) {
    }

    static Optional<Cached> read(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            Map<String, Object> o = Json.parseObject(Files.readString(file));
            List<Broadcast> items = new ArrayList<>();
            for (Object b : Json.list(o, "items")) {
                items.add(new Broadcast(Json.str(b, "channel"), Instant.parse(Json.str(b, "start")),
                        Instant.parse(Json.str(b, "end")), Json.str(b, "title"), Json.str(b, "subtitle"),
                        Json.str(b, "description")));
            }
            return Optional.of(new Cached(Instant.parse(Json.str(o, "fetched")), items));
        } catch (IOException | IllegalArgumentException | DateTimeParseException | NullPointerException e) {
            LOG.debug("Ignoring unreadable {}", file, e);
            return Optional.empty();
        }
    }

    static void write(Path file, Instant fetched, List<Broadcast> items) {
        List<Object> list = new ArrayList<>();
        for (Broadcast b : items) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("channel", b.channelId());
            o.put("start", b.start().toString());
            o.put("end", b.end().toString());
            o.put("title", b.title());
            o.put("subtitle", b.subtitle());
            o.put("description", b.description());
            list.add(o);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("fetched", fetched.toString());
        doc.put("items", list);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), "guide", ".tmp");
            Files.writeString(tmp, Json.write(doc), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("Cannot keep the program in {}", file, e);
        }
    }

    /** Deletes cached days older than a week. */
    public void cleanUp() {
        LocalDate oldest = LocalDate.ofInstant(clock.instant(), GuideSource.BROADCAST_ZONE).minusDays(7);
        if (!Files.isDirectory(cacheDirectory)) return;
        try (var files = Files.list(cacheDirectory)) {
            files.filter(f -> isOlder(f.getFileName().toString(), oldest)).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    LOG.debug("Cannot delete {}", f, e);
                }
            });
        } catch (IOException e) {
            LOG.debug("Cannot clean up {}", cacheDirectory, e);
        }
    }

    static boolean isOlder(String fileName, LocalDate oldest) {
        int dash = fileName.indexOf('-');
        if (dash < 0 || !fileName.endsWith(".json")) return false;
        try {
            return LocalDate.parse(fileName.substring(dash + 1, fileName.length() - 5)).isBefore(oldest);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
