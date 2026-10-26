package eu.amadeos.apps.tv.service.logo;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.service.net.Web;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Channel logos for the tiles. They are not part of the app: on first use each is downloaded from the
 * channel's {@link Channel#logoUrl()} into {@code ~/.cache/amadeos/tv/logos/}, then turned upright,
 * cropped to the drawn part and scaled into the tile. Without a logo (offline, no address) the tile shows
 * the channel name.
 */
public final class LogoCache {
    private static final Logger LOG = LoggerFactory.getLogger(LogoCache.class);

    /** A prepared logo; {@code light} logos (white lettering) need a dark tile. */
    public record Logo(BufferedImage image, boolean light) {
    }

    private final Web web;
    private final Path directory;
    private final Executor background;
    private final int width;
    private final int height;
    private final Map<String, Optional<Logo>> logos = new ConcurrentHashMap<>();
    private final Map<String, Boolean> loading = new ConcurrentHashMap<>();

    public LogoCache(Web web, Path cacheDirectory, Executor background, int width, int height) {
        this.web = web;
        this.directory = cacheDirectory.resolve("logos");
        this.background = background;
        this.width = width;
        this.height = height;
    }

    // --8<-- [start:logo]
    /**
     * The logo if it is ready; otherwise starts loading it and calls {@code ready} (on a background thread)
     * once it is there.
     */
    public Optional<Logo> logo(Channel channel, Consumer<Channel> ready) {
        Optional<Logo> logo = logos.get(channel.id());
        if (logo != null) return logo;
        if (channel.logoUrl().isEmpty()) {
            logos.put(channel.id(), Optional.empty());
            return Optional.empty();
        }
        if (loading.putIfAbsent(channel.id(), true) == null) {
            background.execute(() -> {
                logos.put(channel.id(), load(channel));
                loading.remove(channel.id());
                ready.accept(channel);
            });
        }
        return Optional.empty();
    }
    // --8<-- [end:logo]

    private Optional<Logo> load(Channel channel) {
        String url = channel.logoUrl();
        Path file = directory.resolve(fileName(url));
        try {
            byte[] bytes;
            if (Files.isRegularFile(file)) {
                bytes = Files.readAllBytes(file);
            } else {
                bytes = web.bytes(url);
                Files.createDirectories(directory);
                Files.write(file, bytes);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return Optional.empty();
            return Optional.of(prepare(image, channel.logoTurn(), width, height));
        } catch (IOException | RuntimeException e) {
            LOG.info("No logo for {} ({}): {}", channel.id(), url, e.getMessage());
            return Optional.empty();
        }
    }

    /** "…/logos/tv/daserste.png" is kept as "daserste.png"; other characters become "_". */
    static String fileName(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isEmpty() ? Integer.toHexString(url.hashCode()) + ".png" : name;
    }

    // --8<-- [start:prepare]
    /** Turns the picture by {@code turn} degrees clockwise, crops the transparent margin and fits it in. */
    public static Logo prepare(BufferedImage source, int turn, int maxWidth, int maxHeight) {
        BufferedImage argb = turned(toArgb(source), turn);
        Rectangle box = drawnArea(argb);
        BufferedImage cropped = argb.getSubimage(box.x, box.y, box.width, box.height);
        double scale = Math.min((double) maxWidth / cropped.getWidth(), (double) maxHeight / cropped.getHeight());
        int w = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(cropped.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cropped, 0, 0, w, h, null);
        g.dispose();
        return new Logo(out, isLight(cropped));
    }
    // --8<-- [end:prepare]

    private static BufferedImage toArgb(BufferedImage source) {
        BufferedImage argb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return argb;
    }

    static BufferedImage turned(BufferedImage image, int turn) {
        int quarter = Math.floorMod(Math.round(turn / 90f), 4);
        if (quarter == 0) return image;
        int w = image.getWidth(), h = image.getHeight();
        boolean swap = quarter % 2 == 1;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        AffineTransform t = new AffineTransform();
        switch (quarter) {
            case 1 -> t.translate(h, 0);
            case 2 -> t.translate(w, h);
            default -> t.translate(0, w);
        }
        t.rotate(Math.toRadians(90.0 * quarter));
        g.drawImage(image, t, null);
        g.dispose();
        return out;
    }

    /** The smallest rectangle around the pixels that are not (almost) transparent; the whole picture if none. */
    static Rectangle drawnArea(BufferedImage argb) {
        int x0 = argb.getWidth(), y0 = argb.getHeight(), x1 = -1, y1 = -1;
        for (int y = 0; y < argb.getHeight(); y++) {
            for (int x = 0; x < argb.getWidth(); x++) {
                if ((argb.getRGB(x, y) >>> 24) > 16) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (x1 < 0) return new Rectangle(0, 0, argb.getWidth(), argb.getHeight());
        return new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /** True if the drawn pixels are mostly bright (white lettering on a transparent background). */
    static boolean isLight(BufferedImage argb) {
        double sum = 0, weight = 0;
        for (int y = 0; y < argb.getHeight(); y++) {
            for (int x = 0; x < argb.getWidth(); x++) {
                int p = argb.getRGB(x, y);
                double a = (p >>> 24) / 255.0;
                if (a < 0.1) continue;
                double lum = (0.2126 * ((p >> 16) & 0xFF) + 0.7152 * ((p >> 8) & 0xFF) + 0.0722 * (p & 0xFF)) / 255;
                sum += lum * a;
                weight += a;
            }
        }
        return weight > 0 && sum / weight > 0.75;
    }
}
