package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.service.net.Web;
import eu.amadeos.commons.json.Json;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ZDF's program service, the GraphQL API behind zdf.de. It needs the token the zdf.de page hands to its own
 * player, read from the live TV page; with it, one request gives a day for ZDF, ZDFneo, ZDFinfo, 3sat,
 * KiKA, phoenix and ARTE. This is the most fragile of the sources: if ZDF changes the page or the stored
 * query, the day schedule of these channels stays empty and the Live page shows what runs now from the
 * Zapp service instead.
 *
 * <pre>
 * POST https://api.zdf.de/graphql   api-auth: Bearer &lt;token&gt;
 * { "operationName": "getEpg", "variables": { "filter": { "broadcasterIds": [...], "from": ..., "to": ... } },
 *   "extensions": { "persistedQuery": { "version": 1, "sha256Hash": "..." } } }
 * { "data": { "epg": [ { "broadcaster": { "id": "ZDF", "title": "ZDF" },
 *                        "broadcasts": [ { "title": ..., "subheadline": ..., "text": ...,
 *                                          "airtimeBegin": "2026-10-25T20:15:00+01:00", "airtimeEnd": ... } ] } ] } }
 * </pre>
 */
public final class ZdfEpgSource implements GuideSource {
    static final String PAGE = "https://www.zdf.de/live-tv";
    static final String GRAPHQL = "https://api.zdf.de/graphql";
    static final String EPG_QUERY_HASH = "e9b840c60dab34c0cc2ab81673b4e34a680684b01502635a217bce7e91acf9a8";
    static final List<String> BROADCASTERS = List.of("ZDF", "ZDFinfo", "ZDFneo", "3sat", "KI.KA", "PHOENIX", "arte");
    /** The token in the page, inside escaped JSON ({@code \"appToken\":{\"apiToken\":\"...\"}) or plain. */
    static final Pattern TOKEN = Pattern.compile(
            "appToken\\\\?\"\\s*:\\s*\\{\\s*\\\\?\"apiToken\\\\?\"\\s*:\\s*\\\\?\"([^\"\\\\]{20,})");

    private final Web web;
    private volatile String token;

    public ZdfEpgSource(Web web) {
        this.web = web;
    }

    @Override
    public Channel.GuideSourceId id() {
        return Channel.GuideSourceId.ZDF;
    }

    @Override
    public List<Broadcast> day(LocalDate day, List<Channel> channels) throws IOException {
        Instant from = day.atStartOfDay(BROADCAST_ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(BROADCAST_ZONE).toInstant();
        String body = request(from, to);
        String answer;
        try {
            answer = web.postJson(GRAPHQL, body, headers(token()));
        } catch (IOException e) {
            // The token may have expired: read the page once more.
            token = null;
            answer = web.postJson(GRAPHQL, body, headers(token()));
        }
        return parse(answer, channels);
    }

    private String token() throws IOException {
        String t = token;
        if (t == null) {
            t = token(web.get(PAGE));
            token = t;
        }
        return t;
    }

    static String token(String page) throws IOException {
        Matcher m = TOKEN.matcher(page);
        if (!m.find()) throw new IOException("ZDF: no app token on " + PAGE);
        return m.group(1);
    }

    private static Map<String, String> headers(String token) {
        return Map.of("api-auth", "Bearer " + token, "Origin", "https://www.zdf.de", "Referer", "https://www.zdf.de/");
    }

    static String request(Instant from, Instant to) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("broadcasterIds", BROADCASTERS);
        filter.put("from", DateTimeFormatter.ISO_INSTANT.format(from));
        filter.put("to", DateTimeFormatter.ISO_INSTANT.format(to));
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("operationName", "getEpg");
        query.put("variables", Map.of("filter", filter));
        query.put("extensions", Map.of("persistedQuery", Map.of("version", 1, "sha256Hash", EPG_QUERY_HASH)));
        return Json.write(query);
    }

    static List<Broadcast> parse(String json, List<Channel> channels) throws IOException {
        Map<String, Object> answer;
        try {
            answer = Json.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("ZDF program: not JSON", e);
        }
        if (!Json.list(answer, "errors").isEmpty()) {
            throw new IOException("ZDF program: " + Json.str(Json.list(answer, "errors").get(0), "message"));
        }
        List<Broadcast> items = new ArrayList<>();
        for (Object entry : Json.list(Json.member(answer, "data"), "epg")) {
            Object broadcaster = Json.member(entry, "broadcaster");
            Optional<Channel> channel = channels.stream().filter(c -> c.guide() == Channel.GuideSourceId.ZDF)
                    .filter(c -> c.isCalled(Json.str(broadcaster, "id")) || c.isCalled(Json.str(broadcaster, "title")))
                    .findFirst();
            if (channel.isEmpty()) continue;
            for (Object b : Json.list(entry, "broadcasts")) {
                Instant start = Times.parse(Json.str(b, "airtimeBegin"));
                Instant end = Times.parse(Json.str(b, "airtimeEnd"));
                if (start == null || end == null) continue;
                items.add(new Broadcast(channel.get().id(), start, end, Times.plain(Json.str(b, "title")),
                        Times.plain(Json.str(b, "subheadline")), Times.plain(Json.str(b, "text"))));
            }
        }
        return items;
    }
}
