package eu.amadeos.apps.tv.service.guide;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/** The services write times as ISO 8601 with an offset or a zone; anything unreadable is null. */
final class Times {
    private Times() {
    }

    static Instant parse(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.strip();
        try {
            return OffsetDateTime.parse(t).toInstant();
        } catch (DateTimeParseException e) {
            // "2026-10-25T20:15:00+01:00[Europe/Berlin]" or "...Z"
        }
        try {
            return ZonedDateTime.parse(t).toInstant();
        } catch (DateTimeParseException e) {
            // fall through
        }
        try {
            return Instant.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Program texts come with a little HTML: line breaks become new lines, other tags go. */
    static String plain(String html) {
        if (html == null) return "";
        return html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").strip();
    }
}
