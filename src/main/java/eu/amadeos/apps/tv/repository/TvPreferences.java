package eu.amadeos.apps.tv.repository;

import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Properties;

/** The app's own settings in {@code ~/.config/amadeos/tv/tv.properties}: last channel, volume, regional channels. */
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

    public boolean showRegional() {
        return Boolean.parseBoolean(properties.getProperty("regional", "false"));
    }

    public void setShowRegional(boolean show) {
        set("regional", Boolean.toString(show));
    }

    private void set(String key, String value) {
        if (value.equals(properties.getProperty(key))) return;
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-tv");
    }
}
