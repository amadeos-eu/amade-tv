package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.ChannelGroup;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** The app's own settings in {@code ~/.config/amadeos/tv/tv.properties}: last channel, volume, channel groups. */
public final class TvPreferences {
    private final Path file;
    private final Properties properties;

    public TvPreferences(Path configDirectory) {
        this.file = configDirectory.resolve("tv.properties");
        this.properties = PropertiesFile.load(file);
    }

    public String lastChannel() {
        return properties.getProperty("channel", "das_erste");
    }

    public void setLastChannel(String id) {
        set("channel", id);
    }

    public int volume() {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(properties.getProperty("volume", "80").strip())));
        } catch (NumberFormatException e) {
            return 80;
        }
    }

    public void setVolume(int volume) {
        set("volume", Integer.toString(volume));
    }

    /** The channel groups switched on besides the main channels; none at first. */
    public Set<ChannelGroup> groups() {
        Set<ChannelGroup> groups = EnumSet.noneOf(ChannelGroup.class);
        String list = properties.getProperty("groups");
        if (list == null) {
            // The first version kept only the regional channels: regional=true
            if (Boolean.parseBoolean(properties.getProperty("regional", "false"))) groups.add(ChannelGroup.REGIONAL);
            return groups;
        }
        for (String key : list.split(",")) {
            ChannelGroup g = ChannelGroup.of(key.strip());
            if (g != ChannelGroup.MAIN) groups.add(g);
        }
        return groups;
    }

    public void setGroup(ChannelGroup group, boolean shown) {
        Set<ChannelGroup> groups = groups();
        if (shown) groups.add(group);
        else groups.remove(group);
        properties.remove("regional");
        set("groups", groups.stream().map(ChannelGroup::key).collect(Collectors.joining(",")));
    }

    private void set(String key, String value) {
        if (value.equals(properties.getProperty(key))) return;
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-tv");
    }
}
