package eu.amadeos.apps.tv.service.net;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stream addresses of the Kodinerds IPTV list of free-to-air channels (an M3U playlist on GitHub), by
 * their {@code tvg-id}. The channels of the groups beyond the German public ones refresh their addresses
 * from it, as those do from the Zapp service.
 */
public final class KodinerdsList {
    static final String URL = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/iptv/clean/clean_tv.m3u";
    private static final Pattern TVG_ID = Pattern.compile("tvg-id=\"([^\"]*)\"");

    private final Web web;

    public KodinerdsList(Web web) {
        this.web = web;
    }

    /** tvg-id → stream address, http(s) only. */
    public Map<String, String> streamUrls() throws IOException {
        return parse(web.get(URL));
    }

    // --8<-- [start:parse]
    /** Reads {@code #EXTINF} lines with their tvg-id, each followed by the address on the next line. */
    static Map<String, String> parse(String m3u) {
        Map<String, String> urls = new LinkedHashMap<>();
        String id = null;
        for (String line : m3u.split("\\R")) {
            line = line.strip();
            if (line.startsWith("#EXTINF")) {
                Matcher m = TVG_ID.matcher(line);
                id = m.find() ? m.group(1) : null;
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                if (id != null && !id.isEmpty() && (line.startsWith("https://") || line.startsWith("http://"))) {
                    urls.putIfAbsent(id, line);
                }
                id = null;
            }
        }
        return urls;
    }
    // --8<-- [end:parse]
}
