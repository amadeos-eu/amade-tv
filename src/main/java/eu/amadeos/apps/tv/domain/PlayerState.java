package eu.amadeos.apps.tv.domain;

import java.util.Objects;

/**
 * What the player is doing, as the UI shows it. Immutable; the player publishes a new state on every change.
 * Like amade-video's, without chapters, with volume, mute and subtitles.
 *
 * @param title     media title (the channel name), empty when idle
 * @param position  seconds from the start of what mpv has buffered, -1 if unknown
 * @param behind    seconds the picture is behind the live edge (after "10 minutes back"), 0 at live
 * @param volume    0 to 100
 * @param subtitles true while subtitles are shown
 * @param error     why playback failed, empty otherwise
 */
public record PlayerState(Status status, String title, double position, double behind, boolean paused, int volume,
                          boolean muted, boolean subtitles, String error) {
    public enum Status {
        /** Nothing loaded. */
        IDLE,
        /** Connecting to a stream. */
        LOADING,
        PLAYING,
        /** The stream ended (a broadcast-only stream went off air). */
        ENDED,
        FAILED
    }

    public static final PlayerState IDLE = new PlayerState(Status.IDLE, "", -1, 0, false, 100, false, false, "");

    public PlayerState {
        Objects.requireNonNull(status, "status");
        title = title == null ? "" : title;
        error = error == null ? "" : error;
        volume = Math.max(0, Math.min(100, volume));
        behind = Math.max(0, behind);
    }

    public boolean hasMedia() {
        return status == Status.PLAYING || status == Status.ENDED;
    }

    /** At the live edge (within half a minute). */
    public boolean isLive() {
        return behind < 30;
    }

    public PlayerState withStatus(Status s) {
        return new PlayerState(s, title, position, behind, paused, volume, muted, subtitles,
                s == Status.FAILED ? error : "");
    }

    public PlayerState withTitle(String t) {
        return new PlayerState(status, t, position, behind, paused, volume, muted, subtitles, error);
    }

    public PlayerState withPosition(double p) {
        return new PlayerState(status, title, p, behind, paused, volume, muted, subtitles, error);
    }

    public PlayerState withBehind(double b) {
        return new PlayerState(status, title, position, b, paused, volume, muted, subtitles, error);
    }

    public PlayerState withPaused(boolean p) {
        return new PlayerState(status, title, position, behind, p, volume, muted, subtitles, error);
    }

    public PlayerState withVolume(int v) {
        return new PlayerState(status, title, position, behind, paused, v, muted, subtitles, error);
    }

    public PlayerState withMuted(boolean m) {
        return new PlayerState(status, title, position, behind, paused, volume, m, subtitles, error);
    }

    public PlayerState withSubtitles(boolean s) {
        return new PlayerState(status, title, position, behind, paused, volume, muted, s, error);
    }

    public PlayerState failed(String why) {
        return new PlayerState(Status.FAILED, title, -1, 0, false, volume, muted, subtitles, why);
    }

    /** A new stream is being opened: position and errors of the previous one are forgotten. */
    public PlayerState loading(String newTitle) {
        return new PlayerState(Status.LOADING, newTitle, -1, 0, false, volume, muted, subtitles, "");
    }
}
