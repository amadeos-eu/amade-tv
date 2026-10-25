package eu.amadeos.apps.tv.service.logo;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.testing.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LogoCacheTest {
    @TempDir
    Path cache;

    /** A 200 x 100 picture with a 100 x 20 red bar in the middle, transparent around it. */
    static BufferedImage bar(Color color) {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(50, 40, 100, 20);
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void cropsTheMarginAndFitsTheTile() {
        LogoCache.Logo logo = LogoCache.prepare(bar(Color.RED), 0, 90, 26);
        // 100 x 20 scaled into 90 x 26: width limits
        assertEquals(90, logo.image().getWidth());
        assertEquals(18, logo.image().getHeight());
        assertFalse(logo.light());
        assertTrue(LogoCache.prepare(bar(Color.WHITE), 0, 90, 26).light());
    }

    @Test
    void sidewaysLogosAreTurnedUpright() {
        // A tall picture whose top half is red: turned clockwise, the red half is on the right.
        BufferedImage tall = new BufferedImage(20, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tall.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 20, 40);
        g.setColor(Color.BLUE);
        g.fillRect(0, 40, 20, 40);
        g.dispose();
        BufferedImage turned = LogoCache.turned(tall, 90);
        assertEquals(80, turned.getWidth());
        assertEquals(20, turned.getHeight());
        assertEquals(Color.RED.getRGB(), turned.getRGB(70, 10));
        assertEquals(Color.BLUE.getRGB(), turned.getRGB(10, 10));
        assertSame(tall, LogoCache.turned(tall, 0));
        assertEquals(Color.RED.getRGB(), LogoCache.turned(tall, 180).getRGB(10, 70));
        assertEquals(Color.RED.getRGB(), LogoCache.turned(tall, 270).getRGB(10, 10));
    }

    @Test
    void anEmptyPictureIsKeptWhole() {
        BufferedImage empty = new BufferedImage(10, 5, BufferedImage.TYPE_INT_ARGB);
        assertEquals(new Rectangle(0, 0, 10, 5), LogoCache.drawnArea(empty));
    }

    @Test
    void fileNames() {
        assertEquals("daserste.png", LogoCache.fileName("https://raw.githubusercontent.com/x/logos/tv/daserste.png"));
        assertEquals("a_b.png", LogoCache.fileName("https://x/a b.png?size=2"));
    }

    @Test
    void downloadsOnceAndTellsWhenReady() throws IOException {
        Fixtures.FakeWeb web = new Fixtures.FakeWeb();
        web.pictures.put("https://logos.example/zdf.png", png(bar(Color.ORANGE)));
        Channel zdf = new Channel("zdf", "ZDF", "https://x", "https://logos.example/zdf.png", 0, null, null);
        Channel none = new Channel("one", "ONE", "https://x", "", 0, null, null);
        Channel missing = new Channel("hr", "HR", "https://x", "https://logos.example/hr.png", 0, null, null);
        List<String> ready = new ArrayList<>();
        LogoCache logos = new LogoCache(web, cache, Runnable::run, 90, 26);

        assertEquals(Optional.empty(), logos.logo(zdf, c -> ready.add(c.id())));
        assertEquals(List.of("zdf"), ready);
        assertTrue(logos.logo(zdf, c -> ready.add(c.id())).isPresent());
        assertTrue(Files.isRegularFile(cache.resolve("logos/zdf.png")));
        assertEquals(Optional.empty(), logos.logo(none, c -> ready.add(c.id())));
        assertEquals(Optional.empty(), logos.logo(missing, c -> ready.add(c.id())));
        assertEquals(Optional.empty(), logos.logo(missing, c -> ready.add(c.id())));
        assertEquals(List.of("GET https://logos.example/zdf.png", "GET https://logos.example/hr.png"), web.requests);

        // The next start reads the cached file
        LogoCache again = new LogoCache(new Fixtures.FakeWeb(), cache, Runnable::run, 90, 26);
        again.logo(zdf, c -> { });
        assertTrue(again.logo(zdf, c -> { }).isPresent());
    }
}
