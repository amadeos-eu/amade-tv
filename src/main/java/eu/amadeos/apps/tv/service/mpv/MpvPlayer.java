package eu.amadeos.apps.tv.service.mpv;

import eu.amadeos.apps.tv.domain.PlayerState;
import eu.amadeos.commons.mpv.LibMpv;
import eu.amadeos.commons.mpv.Mpv;
import eu.amadeos.commons.mpv.MpvEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The player of TV: amade-commons' {@link Mpv} for libmpv, and the state of a live stream on top: volume,
 * mute, subtitles, audio tracks, and how far the picture is behind live after a pause or going back in
 * the stream's time-shift window. Commands can be sent from any thread; state changes are published to the
 * listeners on the player's event thread.
 */
public final class MpvPlayer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MpvPlayer.class);
    // reply_userdata of the observed properties
    static final long TIME = 1, PAUSE = 3, EOF = 6, TITLE = 7, VOLUME = 8, MUTE = 9, SUB_VISIBLE = 10;

    private final Mpv mpv;
    private final Clock clock;
    private final List<Consumer<PlayerState>> listeners = new CopyOnWriteArrayList<>();
    /** Guards state updates; not the player's monitor, which close() holds while joining the event thread. */
    private final Object stateLock = new Object();
    private volatile PlayerState state = PlayerState.IDLE;
    /** When the picture was paused, to add the pause to {@link PlayerState#behind()}. */
    private volatile Instant pausedAt;

    public MpvPlayer() {
        this(new Mpv(), Clock.systemUTC());
    }

    MpvPlayer(Mpv mpv, Clock clock) {
        this.mpv = mpv;
        this.clock = clock;
    }

    /** False if libmpv is not installed; nothing can be played then. */
    public boolean isAvailable() {
        return mpv.isAvailable();
    }

    public String unavailableReason() {
        return mpv.unavailableReason();
    }

    /** libmpv's client API version, e.g. "2.5". */
    public String apiVersion() {
        return mpv.apiVersion();
    }

    // --8<-- [start:attach]
    /**
     * Creates the player inside the given native window (X11 window id, HWND). The window must exist,
     * i.e. its component must be displayable.
     */
    public synchronized void attach(long windowId, int volume) {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("idle", "yes");
        options.put("input-default-bindings", "no");
        options.put("input-vo-keyboard", "no");
        options.put("osd-level", "1");
        options.put("volume", Integer.toString(Math.max(0, Math.min(100, volume))));
        // German first for the audio track, the original language comes with the audio button.
        options.put("alang", "deu,ger,de");
        options.put("slang", "deu,ger,de");
        // Subtitles off until asked for; mpv still loads them so switching on is instant.
        options.put("sub-visibility", "no");
        // Keep the time-shift window of live streams seekable.
        options.put("cache", "yes");
        mpv.attach(windowId, options, this::handle);
        mpv.observe(TIME, "time-pos", LibMpv.FORMAT_DOUBLE);
        mpv.observe(PAUSE, "pause", LibMpv.FORMAT_FLAG);
        mpv.observe(EOF, "eof-reached", LibMpv.FORMAT_FLAG);
        mpv.observe(TITLE, "media-title", LibMpv.FORMAT_STRING);
        mpv.observe(VOLUME, "volume", LibMpv.FORMAT_DOUBLE);
        mpv.observe(MUTE, "mute", LibMpv.FORMAT_FLAG);
        mpv.observe(SUB_VISIBLE, "sub-visibility", LibMpv.FORMAT_FLAG);
    }
    // --8<-- [end:attach]

    public boolean isAttached() {
        return mpv.isAttached();
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
        if (!mpv.isAttached()) return;
        pausedAt = null;
        update(s -> s.loading(title));
        mpv.command("loadfile", url, "replace");
        mpv.command("set", "pause", "no");
    }

    public void togglePause() {
        mpv.command("cycle", "pause");
    }

    // --8<-- [start:seek]
    /** Back (negative) or forward in the stream; forward never goes past the live edge. */
    public void seekBy(double seconds) {
        PlayerState s = state;
        double delta = seconds > 0 ? Math.min(seconds, s.behind()) : seconds;
        if (delta == 0) return;
        mpv.command("seek", Double.toString(delta), "relative");
        update(st -> st.withBehind(st.behind() - delta));
    }
    // --8<-- [end:seek]

    public void setVolume(int volume) {
        mpv.command("set", "volume", Integer.toString(Math.max(0, Math.min(100, volume))));
    }

    public void toggleMute() {
        mpv.command("cycle", "mute");
    }

    public void toggleSubtitles() {
        mpv.command("cycle", "sub-visibility");
    }

    /** Next audio track, e.g. original language or audio description. */
    public void nextAudioTrack() {
        mpv.command("cycle", "aid");
    }

    /** The current audio track's language and title as mpv reports them, e.g. "deu" or "Audiodeskription". */
    public String audioTrack() {
        String lang = mpv.property("current-tracks/audio/lang");
        String title = mpv.property("current-tracks/audio/title");
        return title.isEmpty() ? lang : lang.isEmpty() ? title : title + " (" + lang + ")";
    }

    public void stop() {
        pausedAt = null;
        mpv.command("stop");
    }

    @Override
    public synchronized void close() {
        mpv.close();
    }

    // ---- events ----------------------------------------------------------------------------------

    void handle(MpvEvent event) {
        switch (event) {
            case MpvEvent.StartFile s -> update(st -> st.status() == PlayerState.Status.LOADING ? st
                    : st.loading(st.title()));
            case MpvEvent.FileLoaded f -> update(st -> st.withStatus(PlayerState.Status.PLAYING));
            case MpvEvent.EndFile e -> update(st -> switch (e.reason()) {
                case MpvEvent.END_ERROR -> st.failed(mpv.errorString(e.error()));
                case MpvEvent.END_EOF -> st.withStatus(PlayerState.Status.ENDED);
                case MpvEvent.END_STOP, MpvEvent.END_QUIT -> st.status() == PlayerState.Status.LOADING ? st
                        : PlayerState.IDLE.withVolume(st.volume()).withMuted(st.muted()).withSubtitles(st.subtitles());
                default -> st;
            });
            case MpvEvent.PropertyChange p -> {
                if (p.id() == PAUSE) pauseChanged(Boolean.TRUE.equals(p.value()));
                update(st -> propertyChanged(st, p));
            }
            default -> LOG.debug("mpv event {}", event);
        }
    }

    // --8<-- [start:pause]
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
    // --8<-- [end:pause]

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
}
