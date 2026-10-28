package eu.amadeos.apps.tv.service.library;

import eu.amadeos.apps.tv.domain.Film;
import eu.amadeos.apps.tv.service.net.Web;
import eu.amadeos.commons.json.Json;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The films of the media libraries, from the TV service of amadeOS (amadeos-tv-api), which collects them
 * from the broadcasters once for everyone. The system property {@code amade.tv.api} points TV to another
 * instance, e.g. {@code http://localhost:8085/tv/v1/} for one started locally.
 *
 * <pre>
 * GET https://api.amadeos.eu/tv/v1/films?q=tatort&amp;page=0&amp;size=50
 * { "total": 12, "page": 0, "size": 50,
 *   "films": [ { "id": "6f1c…", "channel": "Das Erste", "topic": "Tatort", "title": "Tod am Deich",
 *                "publishedAt": "2026-10-25T19:15:00Z", "durationSeconds": 5310, "videoHls": "https://….m3u8",
 *                … }, … ] }
 * </pre>
 */
public final class FilmLibrary {
    static final String URL = "https://api.amadeos.eu/tv/v1/";
    public static final int PAGE_SIZE = 50;

    /** One page of a search; {@code page} counts from 0. */
    public record Page(long total, int page, List<Film> films) {
        public boolean hasMore() {
            return (long) (page + 1) * PAGE_SIZE < total;
        }
    }

    private final Web web;
    private final String base;

    public FilmLibrary(Web web) {
        this(web, System.getProperty("amade.tv.api", URL));
    }

    FilmLibrary(Web web, String base) {
        this.web = web;
        this.base = base.endsWith("/") ? base : base + "/";
    }

    /** Films matching the words as people type them; without words, the newest first. */
    public Page search(String text, int page) throws IOException {
        String q = URLEncoder.encode(text.strip(), StandardCharsets.UTF_8);
        return parse(web.get(base + "films?q=" + q + "&page=" + page + "&size=" + PAGE_SIZE));
    }

    // --8<-- [start:parse]
    static Page parse(String json) throws IOException {
        Map<String, Object> page;
        try {
            page = Json.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("TV service: not JSON", e);
        }
        List<Film> films = new ArrayList<>();
        for (Object f : Json.list(page, "films")) {
            if (Json.str(f, "id").isEmpty()) continue;
            int seconds = Json.integer(f, "durationSeconds");
            films.add(new Film(Json.str(f, "id"), Json.str(f, "channel"), Json.str(f, "topic"), Json.str(f, "title"),
                    Json.str(f, "description"), time(Json.str(f, "publishedAt")),
                    seconds > 0 ? Duration.ofSeconds(seconds) : null, time(Json.str(f, "availableUntil")),
                    Json.str(f, "websiteUrl"), Json.str(f, "subtitleUrl"), Json.str(f, "videoSd"),
                    Json.str(f, "videoHd"), Json.str(f, "videoHls"),
                    Boolean.TRUE.equals(Json.member(f, "audioDescription")),
                    Boolean.TRUE.equals(Json.member(f, "signLanguage"))));
        }
        return new Page(number(page, "total"), Json.integer(page, "page"), films);
    }
    // --8<-- [end:parse]

    private static long number(Object object, String key) {
        return Json.member(object, key) instanceof Number n ? n.longValue() : 0;
    }

    /** The service writes UTC times ("2026-10-25T19:15:00Z"); anything else is null. */
    private static Instant time(String text) {
        if (text.isBlank()) return null;
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
