package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.ChannelGroup;
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
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The channels: shipped with the app ({@code channels.json}), with the stream addresses updated from the
 * Zapp service and the Kodinerds lists and kept in the cache ({@code ~/.cache/amadeos/tv/stream-urls.json}),
 * because broadcasters move their streams now and then. A channel whose stream needs a program that is not
 * installed (yt-dlp for YouTube) is left out.
 */
public final class ChannelCatalog {
    private static final Logger LOG = LoggerFactory.getLogger(ChannelCatalog.class);
    static final String RESOURCE = "/eu/amadeos/apps/tv/channels.json";

    private final Path urlCache;
    private final List<Channel> shipped;
    private final Map<String, ChannelGroup> groups = new LinkedHashMap<>();
    /** Channel id by the tvg-id of the Kodinerds lists. */
    private final Map<String, String> kodinerdsIds = new LinkedHashMap<>();
    private volatile Map<String, String> updatedUrls;
    private volatile List<Channel> channels;

    public ChannelCatalog(Path cacheDirectory) {
        this(cacheDirectory, shippedJson(), ChannelCatalog::onPath);
    }

    ChannelCatalog(Path cacheDirectory, String json) {
        this(cacheDirectory, json, program -> true);
    }

    /** @param installed whether a program a stream needs (e.g. yt-dlp) is installed */
    // --8<-- [start:read]
    ChannelCatalog(Path cacheDirectory, String json, Predicate<String> installed) {
        this.urlCache = cacheDirectory.resolve("stream-urls.json");
        List<Channel> list = new ArrayList<>();
        for (Object o : Json.list(Json.parseObject(json), "channels")) {
            String needs = Json.str(o, "needs");
            if (!needs.isEmpty() && !installed.test(needs)) continue;
            Channel c = channel(o);
            list.add(c);
            // "regional": true is the form of the first version of channels.json
            groups.put(c.id(), Json.member(o, "regional") instanceof Boolean b && b ? ChannelGroup.REGIONAL
                    : ChannelGroup.of(Json.str(o, "group")));
            String kodinerds = Json.str(o, "kodinerds");
            if (!kodinerds.isEmpty()) kodinerdsIds.put(kodinerds, c.id());
        }
        this.shipped = List.copyOf(list);
        this.updatedUrls = cachedUrls();
        this.channels = withUrls(shipped, updatedUrls);
    }
    // --8<-- [end:read]

    /** All channels in the shipped order. */
    public List<Channel> all() {
        return channels;
    }

    // --8<-- [start:shown]
    /** The main channels, plus those of the groups switched on, in the shipped order. */
    public List<Channel> shown(Set<ChannelGroup> switchedOn) {
        return channels.stream()
                .filter(c -> group(c) == ChannelGroup.MAIN || switchedOn.contains(group(c)))
                .toList();
    }
    // --8<-- [end:shown]

    public ChannelGroup group(Channel c) {
        return groups.getOrDefault(c.id(), ChannelGroup.MAIN);
    }

    /** The channels to refresh from the Kodinerds lists: channel id by tvg-id. */
    public Map<String, String> kodinerdsIds() {
        return Map.copyOf(kodinerdsIds);
    }

    public Channel byId(String id) {
        return channels.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    // --8<-- [start:urls]
    /**
     * New stream addresses (from the Zapp service or the Kodinerds lists) by channel id; unknown ids and
     * empty addresses are ignored, channels not named keep theirs. Saved, so the next start uses them
     * before the services answer.
     */
    public synchronized void updateStreamUrls(Map<String, String> urls) {
        Map<String, String> known = new LinkedHashMap<>(updatedUrls);
        for (Channel c : shipped) {
            String url = urls.get(c.id());
            if (url != null && !url.isBlank()) known.put(c.id(), url);
        }
        updatedUrls = Map.copyOf(known);
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
    // --8<-- [end:urls]

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

    /** True if the program is an executable file in one of the directories of PATH. */
    static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, program))) return true;
        }
        return false;
    }

    static String shippedJson() {
        try (InputStream in = ChannelCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
