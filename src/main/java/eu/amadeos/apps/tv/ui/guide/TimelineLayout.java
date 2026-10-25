package eu.amadeos.apps.tv.ui.guide;

import java.time.Duration;
import java.time.Instant;

/**
 * Where things go in the guide's time grid: a day from {@code from}, {@code pixelsPerHour} wide per hour,
 * one row per channel. Pure arithmetic, so it can be tested without a window.
 */
record TimelineLayout(Instant from, Instant to, int pixelsPerHour, int rowHeight) {

    int width() {
        return x(to);
    }

    int x(Instant t) {
        return (int) Math.round(Duration.between(from, t).toSeconds() * pixelsPerHour / 3600.0);
    }

    Instant at(int x) {
        return from.plusSeconds(Math.round(x * 3600.0 / pixelsPerHour));
    }

    int row(int y) {
        return y < 0 ? -1 : y / rowHeight;
    }

    /** The visible cell of an item, clipped to the day; at least a few pixels wide. */
    int[] span(Instant start, Instant end) {
        int x0 = Math.max(0, x(start));
        int x1 = Math.min(width(), x(end));
        return new int[]{x0, Math.max(x0 + 3, x1)};
    }
}
