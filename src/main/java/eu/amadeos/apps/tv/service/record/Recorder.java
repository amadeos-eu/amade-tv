package eu.amadeos.apps.tv.service.record;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Recording;
import eu.amadeos.apps.tv.repository.RecordingStore;
import eu.amadeos.commons.process.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Records live streams to files with ffmpeg. The recordings are planned in a {@link RecordingStore}; the
 * presenter calls {@link #tick} regularly, which starts the ones that are due and gives up the ones whose
 * time passed while TV was not running. ffmpeg copies the stream as it is ({@code -c copy}), so recording
 * costs almost no processor time, and stops by itself at the end ({@code -t}).
 *
 * <p>Listeners hear about every change: a recording started or ended, and about once a second the size of
 * the running ones. They are called on the recorder's threads.
 */
public final class Recorder {
    private static final Logger LOG = LoggerFactory.getLogger(Recorder.class);
    /** Error text of a recording whose time passed while TV was closed. */
    public static final String MISSED = "missed";
    /** Error text of a recording of a channel without a stream address. */
    public static final String NO_STREAM = "no-stream";
    static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(1);

    private final RecordingStore store;
    private final CommandRunner runner;
    private final Path folder;
    private final ZoneId zone;
    private final Map<String, Running> running = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private static final class Running {
        final Process process;
        volatile long bytes;
        volatile boolean stopped;
        volatile String lastError = "";

        Running(Process process) {
            this.process = process;
        }
    }

    public Recorder(RecordingStore store, CommandRunner runner, Path folder, ZoneId zone) {
        this.store = store;
        this.runner = runner;
        this.folder = folder;
        this.zone = zone;
    }

    public void addListener(Runnable l) {
        listeners.add(l);
    }

    /** Where recordings are saved. */
    public Path folder() {
        return folder;
    }

    public List<Recording> all() {
        return store.all();
    }

    /** The planned or running recording of the program, if there is one. */
    public Optional<Recording> recordingOf(Broadcast b) {
        return store.all().stream().filter(r -> !r.status().isOver() && r.isFor(b)).findFirst();
    }

    /** Plans a recording of the program; one that runs already starts at the next {@link #tick}. */
    public Recording plan(Broadcast b, Channel channel, Instant now) {
        Recording r = Recording.plan(b, channel, folder, now, zone);
        store.add(r);
        changed();
        return r;
    }

    /** Size of the file so far: live while it records. */
    public long bytes(Recording r) {
        Running run = running.get(r.id());
        return run == null ? r.bytes() : run.bytes;
    }

    public boolean isRecording() {
        return !running.isEmpty();
    }

    // --8<-- [start:tick]
    /** Starts what is due, gives up what was missed. {@code channels} finds a channel by its id, or null. */
    public void tick(Instant now, Function<String, Channel> channels) {
        for (Recording r : store.all()) {
            if (r.isMissed(now)) {
                store.update(r.id(), x -> x.withStatus(Recording.Status.FAILED, MISSED));
                changed();
            } else if (r.isDue(now) && !running.containsKey(r.id())) {
                Channel c = channels.apply(r.channelId());
                if (c == null || c.streamUrl().isBlank()) {
                    store.update(r.id(), x -> x.withStatus(Recording.Status.FAILED, NO_STREAM));
                    changed();
                } else {
                    start(r, c.streamUrl(), now);
                }
            }
        }
    }
    // --8<-- [end:tick]

    /** Stops a running recording (the file keeps what was recorded) or cancels a planned one. */
    public void stop(String id) {
        Running run = running.get(id);
        if (run != null) {
            run.stopped = true;
            // ffmpeg ends on SIGTERM like on "q": it writes the end of the file first.
            run.process.destroy();
        } else {
            store.get(id).filter(r -> r.status() == Recording.Status.PLANNED).ifPresent(r -> {
                store.remove(id);
                changed();
            });
        }
    }

    /** Removes a recording from the list; a running one is stopped first. */
    public void remove(String id) {
        stop(id);
        store.remove(id);
        changed();
    }

    /** Stops all running recordings and waits up to {@code wait} for ffmpeg to close the files. */
    public void stopAll(Duration wait) {
        List<Running> runs = new ArrayList<>(running.values());
        running.keySet().forEach(this::stop);
        long until = System.nanoTime() + wait.toNanos();
        for (Running run : runs) {
            try {
                long left = until - System.nanoTime();
                if (left <= 0 || !run.process.waitFor(left, TimeUnit.NANOSECONDS)) run.process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // --8<-- [start:command]
    /** The ffmpeg call that records {@code seconds} of the stream into {@code file}. */
    static List<String> command(String streamUrl, long seconds, Path file) {
        return List.of("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                "-reconnect", "1", "-reconnect_on_network_error", "1", "-reconnect_delay_max", "20",
                "-i", streamUrl, "-t", Long.toString(seconds),
                "-c", "copy", "-sn", "-dn",
                "-progress", "pipe:1", "-nostats", "-y", file.toString());
    }
    // --8<-- [end:command]

    private void start(Recording r, String streamUrl, Instant now) {
        Path file = freeName(r.file());
        long seconds = Math.max(1, r.remaining(now).toSeconds());
        Process process;
        try {
            Files.createDirectories(file.getParent());
            process = runner.start(command(streamUrl, seconds, file));
        } catch (IOException e) {
            LOG.warn("Cannot start recording {}", r.title(), e);
            store.update(r.id(), x -> x.withStatus(Recording.Status.FAILED, e.getMessage()));
            changed();
            return;
        }
        Running run = new Running(process);
        running.put(r.id(), run);
        store.update(r.id(), x -> new Recording(x.id(), x.channelId(), x.channelName(), x.title(), x.programStart(),
                x.start(), x.end(), file, Recording.Status.RECORDING, 0, ""));
        changed();
        Thread errors = Thread.ofVirtual().name("recording-errors").start(() -> readErrors(process.getErrorStream(), run));
        Thread.ofVirtual().name("recording").start(() -> follow(r.id(), run, errors));
    }

    /** Reads ffmpeg's progress lines until it ends, then notes how it ended. */
    private void follow(String id, Running run, Thread errors) {
        long lastReport = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(run.process.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("total_size=")) {
                    try {
                        run.bytes = Long.parseLong(line.substring("total_size=".length()).strip());
                    } catch (NumberFormatException e) {
                        // "N/A" before the first packet
                    }
                } else if (line.startsWith("progress=")) {
                    long t = System.nanoTime();
                    if (t - lastReport >= PROGRESS_INTERVAL.toNanos()) {
                        lastReport = t;
                        changed();
                    }
                }
            }
        } catch (IOException e) {
            LOG.debug("Progress of recording {} ended", id, e);
        }
        int exit;
        try {
            exit = run.process.waitFor();
            errors.join(Duration.ofSeconds(2));
        } catch (InterruptedException e) {
            run.process.destroy();
            Thread.currentThread().interrupt();
            exit = -1;
        }
        Recording.Status status = run.stopped ? Recording.Status.STOPPED
                : exit == 0 ? Recording.Status.DONE : Recording.Status.FAILED;
        String why = status == Recording.Status.FAILED
                ? (run.lastError.isBlank() ? "ffmpeg: exit code " + exit : run.lastError) : "";
        long bytes = run.bytes;
        store.update(id, x -> x.withStatus(status, why).withBytes(bytes));
        running.remove(id);
        LOG.info("Recording {} ended: {} {}", id, status, why);
        changed();
    }

    private static void readErrors(InputStream err, Running run) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) run.lastError = line.strip();
            }
        } catch (IOException e) {
            // ended with the process
        }
    }

    /** The file, or "name (2).mkv" and so on if it exists already: an earlier recording is never overwritten. */
    static Path freeName(Path file) {
        if (!Files.exists(file)) return file;
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        for (int i = 2; ; i++) {
            Path p = file.resolveSibling(base + " (" + i + ")" + ext);
            if (!Files.exists(p)) return p;
        }
    }

    private void changed() {
        for (Runnable l : listeners) {
            try {
                l.run();
            } catch (RuntimeException e) {
                LOG.warn("Recording listener failed", e);
            }
        }
    }
}
