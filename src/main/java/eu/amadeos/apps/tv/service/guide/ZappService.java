package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.commons.json.Json;
import eu.amadeos.apps.tv.service.net.Web;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Zapp service of the MediathekView project (github.com/mediathekview/zapp-backend, MIT): the current
 * stream address of every public channel and what runs on it now. MediathekView's own live tab uses it.
 *
 * <pre>
 * GET https://api.zapp.mediathekview.de/v1/channelinfolist
 * { "das_erste": { "streamUrl": "https://.../master.m3u8", "name": "Das Erste" }, ... }
 * GET https://api.zapp.mediathekview.de/v1/shows/das_erste
 * { "shows": [ { "title": ..., "subtitle": ..., "description": ..., "channel": "das_erste",
 *                "startTime": "2026-10-25T19:00:00.000Z", "endTime": ... } ] }
 * </pre>
 */
public final class ZappService {
    static final String BASE = "https://api.zapp.mediathekview.de/v1/";

    private final Web web;

    public ZappService(Web web) {
        this.web = web;
    }

    /** Channel id to stream address. */
    public Map<String, String> streamUrls() throws IOException {
        return parseStreamUrls(web.get(BASE + "channelinfolist"));
    }

    /** What runs on the channel now, if the service knows. */
    public Optional<Broadcast> now(String channelId) throws IOException {
        return parseShow(web.get(BASE + "shows/" + channelId), channelId);
    }

    static Map<String, String> parseStreamUrls(String json) throws IOException {
        Map<String, Object> list;
        try {
            list = Json.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("Zapp channel list: not JSON", e);
        }
        Map<String, String> urls = new LinkedHashMap<>();
        list.forEach((id, info) -> {
            String url = Json.str(info, "streamUrl");
            if (url.startsWith("https://") || url.startsWith("http://")) urls.put(id, url);
        });
        return urls;
    }

    static Optional<Broadcast> parseShow(String json, String channelId) throws IOException {
        Map<String, Object> answer;
        try {
            answer = Json.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("Zapp show: not JSON", e);
        }
        List<Object> shows = Json.list(answer, "shows");
        if (shows.isEmpty() || !Json.str(answer, "error").isEmpty()) return Optional.empty();
        Object show = shows.get(0);
        Instant start = Times.parse(Json.str(show, "startTime"));
        Instant end = Times.parse(Json.str(show, "endTime"));
        if (start == null || end == null) return Optional.empty();
        return Optional.of(new Broadcast(channelId, start, end, Times.plain(Json.str(show, "title")),
                Times.plain(Json.str(show, "subtitle")), Times.plain(Json.str(show, "description"))));
    }
}
