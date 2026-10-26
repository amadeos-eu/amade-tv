package eu.amadeos.apps.tv.domain;

import java.util.Locale;

/**
 * The groups of the channel list. The main channels are always shown; each other group is switched on in
 * the Channels menu. The keys are the {@code group} values of {@code channels.json}.
 */
public enum ChannelGroup {
    /** The national public channels of Germany. */
    MAIN,
    /** The regional channels of the ARD and the Bundestag. */
    REGIONAL,
    /** ORF, ServusTV and the regional channels of Austria and Switzerland. */
    ATCH,
    /** News and public channels of other countries. */
    INTERNATIONAL,
    /** Further free German channels: news, music, documentaries, church and visual radio. */
    MORE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The group of a key; MAIN for an unknown or missing one. */
    public static ChannelGroup of(String key) {
        for (ChannelGroup g : values()) {
            if (g.key().equals(key)) return g;
        }
        return MAIN;
    }
}
