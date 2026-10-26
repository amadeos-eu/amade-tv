package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Recording;
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
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The planned and finished recordings in {@code ~/.config/amadeos/tv/recordings.json}. A recording that was
 * running when TV ended (a crash, a power cut) is stopped when the store is loaded: its file holds what was
 * recorded until then.
 */
public final class RecordingStore {
    private static final Logger LOG = LoggerFactory.getLogger(RecordingStore.class);

    private final Path file;
    private final List<Recording> recordings = new ArrayList<>();
    private final List<Object> unreadable = new ArrayList<>();

    public RecordingStore(Path configDirectory) {
        this.file = configDirectory.resolve("recordings.json");
        load();
    }

    /** All recordings, by start time. */
    public synchronized List<Recording> all() {
        return List.copyOf(recordings);
    }

    public synchronized Optional<Recording> get(String id) {
        return recordings.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public synchronized void add(Recording r) {
        recordings.add(r);
        recordings.sort(Comparator.comparing(Recording::start));
        save();
    }

    public synchronized void remove(String id) {
        if (recordings.removeIf(r -> r.id().equals(id))) save();
    }

    /** Changes a recording and saves; nothing happens if it is gone. */
    public synchronized Optional<Recording> update(String id, UnaryOperator<Recording> change) {
        for (int i = 0; i < recordings.size(); i++) {
            if (recordings.get(i).id().equals(id)) {
                Recording next = change.apply(recordings.get(i));
                recordings.set(i, next);
                save();
                return Optional.of(next);
            }
        }
        return Optional.empty();
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        List<Object> entries;
        try {
            entries = Json.parseArray(Files.readString(file));
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Ignoring unreadable {}", file, e);
            return;
        }
        for (Object o : entries) {
            try {
                recordings.add(read(o));
            } catch (IllegalArgumentException | DateTimeParseException | NullPointerException e) {
                // Kept as it is and written back, so that nothing is lost (a file name this system's
                // language settings cannot represent, for example).
                LOG.warn("Keeping an unreadable recording in {}: {}", file, e.getMessage());
                unreadable.add(o);
            }
        }
        recordings.sort(Comparator.comparing(Recording::start));
    }

    private static Recording read(Object o) {
        Recording.Status status;
        try {
            status = Recording.Status.valueOf(Json.str(o, "status"));
        } catch (IllegalArgumentException e) {
            status = Recording.Status.FAILED;
        }
        Recording r = new Recording(Json.str(o, "id"), Json.str(o, "channel"), Json.str(o, "channelName"),
                Json.str(o, "title"), Instant.parse(Json.str(o, "programStart")),
                Instant.parse(Json.str(o, "start")), Instant.parse(Json.str(o, "end")),
                Path.of(Json.str(o, "file")), status, bytes(o), Json.str(o, "error"));
        return status == Recording.Status.RECORDING ? r.withStatus(Recording.Status.STOPPED, "") : r;
    }

    private static long bytes(Object o) {
        try {
            return Long.parseLong(Json.str(o, "bytes").strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void save() {
        List<Object> list = new ArrayList<>();
        for (Recording r : recordings) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", r.id());
            o.put("channel", r.channelId());
            o.put("channelName", r.channelName());
            o.put("title", r.title());
            o.put("programStart", r.programStart().toString());
            o.put("start", r.start().toString());
            o.put("end", r.end().toString());
            o.put("file", r.file().toString());
            o.put("status", r.status().name());
            o.put("bytes", r.bytes());
            o.put("error", r.error());
            list.add(o);
        }
        list.addAll(unreadable);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), "recordings", ".tmp");
            Files.writeString(tmp, Json.write(list), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("Cannot save the recordings in {}", file, e);
        }
    }
}
