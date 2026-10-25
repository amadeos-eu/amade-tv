package eu.amadeos.apps.tv.service.mpv;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import eu.amadeos.apps.tv.domain.PlayerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The player: libmpv rendering into a native window of the UI ({@link #attach}), decoding and playing the
 * sound itself. Taken over from amade-video (the second user of this binding) and extended for live
 * streams: volume, mute, subtitles, audio tracks and going back in the stream's time-shift window.
 * Commands can be sent from any thread; state changes are published to the listeners on the player's
 * event thread.
 *
 * <p>mpv options can be overridden with system properties {@code mpv.<option>}, e.g. {@code -Dmpv.ao=null}.
 */
public final class MpvPlayer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MpvPlayer.class);
    private static final String OVERRIDE_PREFIX = "mpv.";
    // reply_userdata of the observed properties
    static final long TIME = 1, PAUSE = 3, EOF = 6, TITLE = 7, VOLUME = 8, MUTE = 9, SUB_VISIBLE = 10;

    private final LibMpv mpv;
    private final String unavailableReason;
    private final Clock clock;
    private final List<Consumer<PlayerState>> listeners = new CopyOnWriteArrayList<>();
    /** Guards state updates; not the player's monitor, which close() holds while joining the event thread. */
    private final Object stateLock = new Object();
    private volatile PlayerState state = PlayerState.IDLE;
    private volatile Pointer handle;
    private volatile boolean running;
    private volatile boolean fastProfile;
    /** When the picture was paused, to add the pause to {@link PlayerState#behind()}. */
    private volatile Instant pausedAt;
    private Thread events;

    public MpvPlayer() {
        this(Clock.systemUTC());
    }

    MpvPlayer(Clock clock) {
        this.clock = clock;
        LibMpv lib = null;
        StringBuilder why = new StringBuilder();
        for (String name : LibMpv.NAMES) {
            try {
                lib = Native.load(name, LibMpv.class);
                break;
            } catch (UnsatisfiedLinkError e) {
                why.append(name).append(": ").append(e.getMessage()).append('\n');
            }
        }
        this.mpv = lib;
        this.unavailableReason = lib == null ? why.toString().strip() : "";
        if (lib == null) LOG.warn("libmpv is not installed:\n{}", unavailableReason);
    }

    /** False if libmpv is not installed; nothing can be played then. */
    public boolean isAvailable() {
        return mpv != null;
    }

    public String unavailableReason() {
        return unavailableReason;
    }

    /** libmpv's client API version, e.g. "2.5". */
    public String apiVersion() {
        long v = mpv.mpv_client_api_version();
        return (v >> 16) + "." + (v & 0xFFFF);
    }

    /**
     * Creates the player inside the given native window (X11 window id, HWND). The window must exist,
     * i.e. its component must be displayable.
     */
    public synchronized void attach(long windowId, int volume) {
        if (mpv == null) throw new IllegalStateException("libmpv is not installed");
        if (handle != null) throw new IllegalStateException("already attached");
        // libmpv refuses to start unless LC_NUMERIC is "C"; the JVM sets the user's locale.
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            Native.load("c", LibMpv.LibC.class).setlocale(LibMpv.LibC.LC_NUMERIC, "C");
        }
        Pointer h = mpv.mpv_create();
        if (h == null) throw new IllegalStateException("mpv_create failed");
        handle = h;
        option("wid", Long.toString(windowId));
        Map<String, String> output = new LinkedHashMap<>(VideoOutput.forThisMachine());
        Map<String, String> overrides = overrides();
        output.keySet().removeAll(overrides.keySet());
        fastProfile = "fast".equals(output.get("profile"));
        output.forEach(this::outputOption);
        option("idle", "yes");
        option("input-default-bindings", "no");
        option("input-vo-keyboard", "no");
        option("osd-level", "1");
        option("volume", Integer.toString(Math.max(0, Math.min(100, volume))));
        // German first for the audio track, the original language comes with the audio button.
        option("alang", "deu,ger,de");
        option("slang", "deu,ger,de");
        // Subtitles off until asked for; mpv still loads them so switching on is instant.
        option("sub-visibility", "no");
        // Keep the time-shift window of live streams seekable.
        option("cache", "yes");
        overrides.forEach(this::option);
        check(mpv.mpv_initialize(h), "mpv_initialize");
        mpv.mpv_request_log_messages(h, LOG.isDebugEnabled() ? "info" : "warn");
        mpv.mpv_observe_property(h, TIME, "time-pos", LibMpv.FORMAT_DOUBLE);
        mpv.mpv_observe_property(h, PAUSE, "pause", LibMpv.FORMAT_FLAG);
        mpv.mpv_observe_property(h, EOF, "eof-reached", LibMpv.FORMAT_FLAG);
        mpv.mpv_observe_property(h, TITLE, "media-title", LibMpv.FORMAT_STRING);
        mpv.mpv_observe_property(h, VOLUME, "volume", LibMpv.FORMAT_DOUBLE);
        mpv.mpv_observe_property(h, MUTE, "mute", LibMpv.FORMAT_FLAG);
        mpv.mpv_observe_property(h, SUB_VISIBLE, "sub-visibility", LibMpv.FORMAT_FLAG);
        running = true;
        events = Thread.ofPlatform().daemon().name("mpv-events").start(this::eventLoop);
        LOG.info("libmpv {} attached to window 0x{}", apiVersion(), Long.toHexString(windowId));
    }

    public boolean isAttached() {
        return handle != null;
    }

    public PlayerState state() {
        return state;
    }

    /** Called on the player's event thread with every new state. */
    public void addListener(Consumer<PlayerState> listener) {
        listeners.add(listener);
    }

    /** Opens a stream (or file); the title is what the window shows, e.g. the channel name. */
    public void open(String url, String title) {
        if (handle == null) return;
        pausedAt = null;
        update(s -> s.loading(title));
        command("loadfile", url, "replace");
        command("set", "pause", "no");
    }

    public void togglePause() {
        command("cycle", "pause");
    }

    /** Back (negative) or forward in the stream; forward never goes past the live edge. */
    public void seekBy(double seconds) {
        PlayerState s = state;
        double delta = seconds > 0 ? Math.min(seconds, s.behind()) : seconds;
        if (delta == 0) return;
        command("seek", Double.toString(delta), "relative");
        update(st -> st.withBehind(st.behind() - delta));
    }

    public void setVolume(int volume) {
        command("set", "volume", Integer.toString(Math.max(0, Math.min(100, volume))));
    }

    public void toggleMute() {
        command("cycle", "mute");
    }

    public void toggleSubtitles() {
        command("cycle", "sub-visibility");
    }

    /** Next audio track, e.g. original language or audio description. */
    public void nextAudioTrack() {
        command("cycle", "aid");
    }

    /** The current audio track's language and title as mpv reports them, e.g. "deu" or "Audiodeskription". */
    public String audioTrack() {
        Pointer h = handle;
        if (h == null) return "";
        String lang = property("current-tracks/audio/lang");
        String title = property("current-tracks/audio/title");
        return title.isEmpty() ? lang : lang.isEmpty() ? title : title + " (" + lang + ")";
    }

    public void stop() {
        pausedAt = null;
        command("stop");
    }

    @Override
    public synchronized void close() {
        Pointer h = handle;
        if (h == null) return;
        running = false;
        // The event thread must leave mpv_wait_event before the handle is destroyed.
        mpv.mpv_wakeup(h);
        try {
            if (events != null) events.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        handle = null;
        mpv.mpv_terminate_destroy(h);
    }

    // ---- events ----------------------------------------------------------------------------------

    private void eventLoop() {
        while (running) {
            Pointer h = handle;
            if (h == null) break;
            handle(MpvEvent.read(mpv.mpv_wait_event(h, 1.0)));
        }
    }

    void handle(MpvEvent event) {
        switch (event) {
            case MpvEvent.None n -> {
            }
            case MpvEvent.Shutdown s -> running = false;
            case MpvEvent.StartFile s -> update(st -> st.status() == PlayerState.Status.LOADING ? st
                    : st.loading(st.title()));
            case MpvEvent.FileLoaded f -> update(st -> st.withStatus(PlayerState.Status.PLAYING));
            case MpvEvent.EndFile e -> update(st -> switch (e.reason()) {
                case MpvEvent.END_ERROR -> st.failed(mpv.mpv_error_string(e.error()));
                case MpvEvent.END_EOF -> st.withStatus(PlayerState.Status.ENDED);
                case MpvEvent.END_STOP, MpvEvent.END_QUIT -> st.status() == PlayerState.Status.LOADING ? st
                        : PlayerState.IDLE.withVolume(st.volume()).withMuted(st.muted()).withSubtitles(st.subtitles());
                default -> st;
            });
            case MpvEvent.PropertyChange p -> {
                if (p.id() == PAUSE) pauseChanged(Boolean.TRUE.equals(p.value()));
                update(st -> propertyChanged(st, p));
            }
            case MpvEvent.Log l -> log(l);
            case MpvEvent.Other o -> LOG.debug("mpv event {}", mpv.mpv_event_name(o.id()));
        }
    }

    /** A pause lets the picture fall behind the live edge by as long as it lasted. */
    private void pauseChanged(boolean paused) {
        if (paused) {
            if (pausedAt == null) pausedAt = clock.instant();
        } else if (pausedAt != null) {
            double seconds = Duration.between(pausedAt, clock.instant()).toMillis() / 1000.0;
            pausedAt = null;
            update(st -> st.withBehind(st.behind() + seconds));
        }
    }

    static PlayerState propertyChanged(PlayerState st, MpvEvent.PropertyChange p) {
        Object v = p.value();
        if (p.id() == TIME) return st.withPosition(v instanceof Double d ? d : -1);
        if (p.id() == PAUSE) return st.withPaused(Boolean.TRUE.equals(v));
        if (p.id() == VOLUME && v instanceof Double d) return st.withVolume((int) Math.round(d));
        if (p.id() == MUTE) return st.withMuted(Boolean.TRUE.equals(v));
        if (p.id() == SUB_VISIBLE) return st.withSubtitles(Boolean.TRUE.equals(v));
        if (p.id() == TITLE && v instanceof String t && !t.isBlank() && st.title().isEmpty()) return st.withTitle(t);
        if (p.id() == EOF && Boolean.TRUE.equals(v) && st.status() == PlayerState.Status.PLAYING) {
            return st.withStatus(PlayerState.Status.ENDED);
        }
        return st;
    }

    private void log(MpvEvent.Log l) {
        if (isSoftwareRenderer(l) && !fastProfile) {
            // The renderer found no GPU (QEMU, Xvfb) although /dev/dri suggested one: the high-quality
            // scalers would leave the picture blank, so switch to the simple ones while running.
            fastProfile = true;
            LOG.info("Software rendering: switching mpv to the fast profile");
            command("apply-profile", "fast");
        }
        // Probing hardware decoders fails normally on machines without them (no VAAPI driver, ...).
        boolean probing = l.prefix().contains("vaapi") || l.prefix().contains("vdpau");
        switch (probing ? "debug" : l.level()) {
            case "fatal", "error" -> LOG.error("mpv {}: {}", l.prefix(), l.text());
            case "warn" -> LOG.warn("mpv {}: {}", l.prefix(), l.text());
            default -> LOG.debug("mpv {}: {}", l.prefix(), l.text());
        }
    }

    /** mpv's warning when its OpenGL renderer runs without a GPU (llvmpipe). */
    static boolean isSoftwareRenderer(MpvEvent.Log l) {
        return l.prefix().startsWith("vo/gpu") && l.text().contains("software renderer");
    }

    private void update(UnaryOperator<PlayerState> change) {
        synchronized (stateLock) {
            PlayerState next = change.apply(state);
            if (next.equals(state)) return;
            state = next;
            for (Consumer<PlayerState> l : listeners) {
                try {
                    l.accept(next);
                } catch (RuntimeException e) {
                    LOG.warn("Player listener failed", e);
                }
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void command(String... args) {
        Pointer h = handle;
        if (h == null) return;
        int r = mpv.mpv_command(h, args);
        if (r < 0) LOG.warn("mpv {}: {}", String.join(" ", args), mpv.mpv_error_string(r));
    }

    private String property(String name) {
        Pointer h = handle;
        if (h == null) return "";
        Pointer p = mpv.mpv_get_property_string(h, name);
        if (p == null) return "";
        try {
            return p.getString(0, "UTF-8");
        } finally {
            mpv.mpv_free(p);
        }
    }

    private void option(String name, String value) {
        check(mpv.mpv_set_option_string(handle, name, value), "option " + name + "=" + value);
    }

    /** The {@code -Dmpv.<option>=<value>} system properties, which replace the defaults of the same name. */
    static Map<String, String> overrides() {
        Map<String, String> overrides = new TreeMap<>();
        System.getProperties().stringPropertyNames().stream().filter(k -> k.startsWith(OVERRIDE_PREFIX))
                .forEach(k -> overrides.put(k.substring(OVERRIDE_PREFIX.length()), System.getProperty(k)));
        return overrides;
    }

    /**
     * Sets a video output option. These depend on the mpv version (older ones know fewer GPU contexts), so
     * a value this libmpv rejects is logged and its own default stays, instead of no player at all.
     */
    private void outputOption(String name, String value) {
        int result = mpv.mpv_set_option_string(handle, name, value);
        if (result < 0) {
            LOG.warn("libmpv {} rejects {}={} ({}); using its default", apiVersion(), name, value,
                    mpv.mpv_error_string(result));
        }
    }

    private void check(int result, String what) {
        if (result < 0) throw new IllegalStateException(what + ": " + mpv.mpv_error_string(result));
    }
}
