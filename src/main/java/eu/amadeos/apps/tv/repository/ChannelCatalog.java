package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.commons.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The channels: shipped with the app ({@code channels.json}), with the stream addresses updated from the
 * Zapp service and kept in the cache ({@code ~/.cache/amadeos/tv/stream-urls.json}), because broadcasters
 * move their streams now and then.
 */
public final class ChannelCatalog {
    private static final Logger LOG = LoggerFactory.getLogger(ChannelCatalog.class);
    static final String RESOURCE = "/eu/amadeos/apps/tv/channels.json";

    private final Path urlCache;
    private final List<Channel> shipped;
    private final List<Boolean> regional;
    private volatile List<Channel> channels;

    public ChannelCatalog(Path cacheDirectory) {
        this(cacheDirectory, shippedJson());
    }

    ChannelCatalog(Path cacheDirectory, String json) {
        this.urlCache = cacheDirectory.resolve("stream-urls.json");
        List<Channel> list = new ArrayList<>();
        List<Boolean> regionalFlags = new ArrayList<>();
        for (Object o : Json.list(Json.parseObject(json), "channels")) {
            list.add(channel(o));
            regionalFlags.add(Json.member(o, "regional") instanceof Boolean b && b);
        }
        this.shipped = List.copyOf(list);
        this.regional = List.copyOf(regionalFlags);
        this.channels = withUrls(shipped, cachedUrls());
    }

    /** All channels in the shipped order. */
    public List<Channel> all() {
        return channels;
    }

    /** The main channels, plus the regional ones if asked for. */
    public List<Channel> shown(boolean withRegional) {
        List<Channel> out = new ArrayList<>();
        for (int i = 0; i < channels.size(); i++) {
            if (withRegional || !regional.get(i)) out.add(channels.get(i));
        }
        return out;
    }

    public Channel byId(String id) {
        return channels.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * New stream addresses (from the Zapp service) for known channels; unknown ids are ignored. Saved, so
     * the next start uses them before the service answers.
     */
    public void updateStreamUrls(Map<String, String> urls) {
        Map<String, String> known = new LinkedHashMap<>();
        for (Channel c : shipped) {
            String url = urls.get(c.id());
            if (url != null && !url.isBlank()) known.put(c.id(), url);
        }
        channels = withUrls(shipped, known);
        try {
            Files.createDirectories(urlCache.getParent());
            Path tmp = Files.createTempFile(urlCache.getParent(), "stream-urls", ".tmp");
            Files.writeString(tmp, Json.write(new LinkedHashMap<String, Object>(known)), StandardCharsets.UTF_8);
            Files.move(tmp, urlCache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("Cannot keep the stream addresses in {}", urlCache, e);
        }
    }

    private Map<String, String> cachedUrls() {
        if (!Files.isRegularFile(urlCache)) return Map.of();
        try {
            Map<String, String> urls = new LinkedHashMap<>();
            Json.parseObject(Files.readString(urlCache)).forEach((k, v) -> {
                if (v instanceof String s) urls.put(k, s);
            });
            return urls;
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Ignoring unreadable {}", urlCache, e);
            return Map.of();
        }
    }

    private static List<Channel> withUrls(List<Channel> base, Map<String, String> urls) {
        return base.stream().map(c -> urls.containsKey(c.id()) ? c.withStreamUrl(urls.get(c.id())) : c).toList();
    }

    private static Channel channel(Object o) {
        List<String> names = new ArrayList<>();
        for (Object n : Json.list(o, "guideNames")) names.add(String.valueOf(n));
        Channel.GuideSourceId guide;
        try {
            guide = Channel.GuideSourceId.valueOf(Json.str(o, "guide"));
        } catch (IllegalArgumentException e) {
            guide = Channel.GuideSourceId.NONE;
        }
        return new Channel(Json.str(o, "id"), Json.str(o, "name"), Json.str(o, "stream"), Json.str(o, "logo"),
                Json.integer(o, "logoTurn"), guide, names);
    }

    private static String shippedJson() {
        try (InputStream in = ChannelCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
