package eu.amadeos.apps.tv.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A live TV channel of a public broadcaster.
 *
 * @param id         stable id, the same as the Zapp API uses ({@code das_erste}, {@code zdf}, {@code dreisat}, ...)
 * @param name       what the channel list shows
 * @param streamUrl  the broadcaster's HLS stream ({@code master.m3u8})
 * @param logoUrl    picture for the channel tile, downloaded on first use; empty for a text tile
 * @param logoTurn   degrees clockwise the logo picture has to be turned (some are stored sideways)
 * @param guide      where the day schedule comes from
 * @param guideNames names the guide source uses for this channel, compared without case and spaces
 */
public record Channel(String id, String name, String streamUrl, String logoUrl, int logoTurn, GuideSourceId guide,
                      List<String> guideNames) {

    /** The program data sources a channel's schedule can come from. */
    public enum GuideSourceId {
        /** ARD's program service: Das Erste, the regional channels, ONE, ARD-alpha, tagesschau24. */
        ARD,
        /** ZDF's program service: ZDF, ZDFneo, ZDFinfo, 3sat, KiKA, phoenix, ARTE. */
        ZDF,
        /** No day schedule; only what runs now, from the Zapp service. */
        NONE
    }

    public Channel {
        Objects.requireNonNull(id, "id");
        name = name == null || name.isBlank() ? id : name;
        streamUrl = streamUrl == null ? "" : streamUrl;
        logoUrl = logoUrl == null ? "" : logoUrl;
        guide = guide == null ? GuideSourceId.NONE : guide;
        guideNames = guideNames == null ? List.of() : List.copyOf(guideNames);
        logoTurn = Math.floorMod(logoTurn, 360);
    }

    public Channel withStreamUrl(String url) {
        return new Channel(id, name, url, logoUrl, logoTurn, guide, guideNames);
    }

    /** True if the guide source calls this channel {@code sourceName} (or its id). */
    public boolean isCalled(String sourceName) {
        String wanted = normalize(sourceName);
        if (wanted.isEmpty()) return false;
        if (normalize(id).equals(wanted) || normalize(name).equals(wanted)) return true;
        return guideNames.stream().anyMatch(n -> normalize(n).equals(wanted));
    }

    /** Lower case without spaces, dashes, dots and underscores: "KI.KA" and "KiKA" are the same. */
    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\s._\\-\\u00ad]", "");
    }
}
