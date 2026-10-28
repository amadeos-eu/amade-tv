package eu.amadeos.apps.tv.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * A film of a broadcaster's media library, as the TV service (amadeos-tv-api) lists it: metadata and the
 * addresses of the video on the broadcaster's server. Empty strings and null times where a broadcaster
 * gives nothing.
 *
 * @param duration       null if unknown
 * @param availableUntil null if the broadcaster names no end
 */
public record Film(String id, String channel, String topic, String title, String description, Instant publishedAt,
                   Duration duration, Instant availableUntil, String websiteUrl, String subtitleUrl, String videoSd,
                   String videoHd, String videoHls, boolean audioDescription, boolean signLanguage) {

    /** The address to play: HLS (the player picks the quality), else the best MP4; empty if there is none. */
    public String videoUrl() {
        if (!videoHls.isBlank()) return videoHls;
        if (!videoHd.isBlank()) return videoHd;
        return videoSd;
    }

    /** "Tatort: Tod am Deich", or the title alone when the topic says the same. */
    public String fullTitle() {
        if (topic.isBlank() || title.startsWith(topic)) return title;
        return topic + ": " + title;
    }
}
