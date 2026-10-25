package eu.amadeos.apps.tv.service.mpv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VideoOutputTest {
    @Test
    void linuxStaysOnX11AndFallsBackToXvAndX11() {
        Map<String, String> o = VideoOutput.options("Linux", true);
        assertEquals("gpu,xv,x11", o.get("vo"));
        assertEquals("x11egl,x11vk", o.get("gpu-context"));
        assertEquals("auto-safe", o.get("hwdec"));
        assertFalse(o.containsKey("profile"));
    }

    @Test
    void withoutAGpuTheFastProfileKeepsThePictureVisible() {
        assertEquals("fast", VideoOutput.options("Linux", false).get("profile"));
    }

    @Test
    void windowsUsesMpvsOwnContextChoice() {
        Map<String, String> o = VideoOutput.options("Windows 11", true);
        assertEquals("gpu", o.get("vo"));
        assertFalse(o.containsKey("gpu-context"));
    }

    @Test
    void renderNodeMeansGpu(@TempDir Path dri) throws Exception {
        assertFalse(VideoOutput.hasRenderNode(dri.resolve("missing")));
        Files.createFile(dri.resolve("card0"));
        assertFalse(VideoOutput.hasRenderNode(dri));
        Files.createFile(dri.resolve("renderD128"));
        assertTrue(VideoOutput.hasRenderNode(dri));
    }
}
