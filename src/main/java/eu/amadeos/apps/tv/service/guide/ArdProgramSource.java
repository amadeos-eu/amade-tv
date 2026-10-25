package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.service.net.Web;
import eu.amadeos.commons.json.Json;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ARD's program service, the one behind the program pages of the ARD Mediathek: one request per day for
 * all ARD channels (Das Erste, the regional channels, ONE, ARD-alpha, tagesschau24, ...).
 *
 * <pre>
 * GET https://programm-api.ard.de/program/api/program?day=2026-10-25
 * { "channels": [ { "id": "daserste", "publicationService": { "name": "Das Erste" },
 *                   "timeSlots": [ [ { "title": "Tagesschau", "subline": "...", "synopsis": "...",
 *                                      "broadcastedOn": "2026-10-25T19:00:00Z",
 *                                      "broadcastEnd": "2026-10-25T19:15:00Z" }, ... ], ... ] }, ... ] }
 * </pre>
 */
public final class ArdProgramSource implements GuideSource {
    static final String URL = "https://programm-api.ard.de/program/api/program?day=";

    private final Web web;

    public ArdProgramSource(Web web) {
        this.web = web;
    }

    @Override
    public Channel.GuideSourceId id() {
        return Channel.GuideSourceId.ARD;
    }

    @Override
    public List<Broadcast> day(LocalDate day, List<Channel> channels) throws IOException {
        return parse(web.get(URL + day), channels);
    }

    static List<Broadcast> parse(String json, List<Channel> channels) throws IOException {
        Map<String, Object> program;
        try {
            program = Json.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("ARD program: not JSON", e);
        }
        List<Broadcast> items = new ArrayList<>();
        for (Object ch : Json.list(program, "channels")) {
            Optional<Channel> channel = match(ch, channels);
            if (channel.isEmpty()) continue;
            for (Object column : Json.list(ch, "timeSlots")) {
                if (!(column instanceof List<?> slots)) continue;
                for (Object slot : slots) {
                    Broadcast b = broadcast(channel.get().id(), slot);
                    if (b != null) items.add(b);
                }
            }
        }
        return items;
    }

    /** By the service's channel id ("daserste") or its name ("Das Erste"). */
    static Optional<Channel> match(Object serviceChannel, List<Channel> channels) {
        String id = Json.str(serviceChannel, "id");
        String name = Json.str(Json.member(serviceChannel, "publicationService"), "name");
        return channels.stream().filter(c -> c.guide() == Channel.GuideSourceId.ARD)
                .filter(c -> c.isCalled(id) || c.isCalled(name)).findFirst();
    }

    private static Broadcast broadcast(String channelId, Object slot) {
        Instant start = Times.parse(Json.str(slot, "broadcastedOn"));
        Instant end = Times.parse(Json.str(slot, "broadcastEnd"));
        if (start == null || end == null) return null;
        String title = Json.str(slot, "title");
        if (title.isBlank()) title = Json.str(slot, "coreTitle");
        String subline = Json.str(slot, "subline");
        if (subline.isBlank()) subline = Json.str(slot, "coreSubline");
        return new Broadcast(channelId, start, end, Times.plain(title), Times.plain(subline),
                Times.plain(Json.str(slot, "synopsis")));
    }
}
