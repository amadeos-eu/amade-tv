package eu.amadeos.apps.tv.service.mpv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * mpv's video output options for this machine. On Linux the GPU renderer may only use X11 contexts
 * (otherwise mpv tries DRM and the console), with xv and plain x11 as fallbacks. Without a GPU (QEMU,
 * Xvfb) mpv's high-quality scalers do not compile on software OpenGL and the picture stays blank when
 * downscaling, so the "fast" profile is used there.
 */
final class VideoOutput {
    private VideoOutput() {
    }

    static Map<String, String> options(String osName, boolean gpu) {
        Map<String, String> options = new LinkedHashMap<>();
        if (osName.startsWith("Windows") || osName.startsWith("Mac")) {
            options.put("vo", "gpu");
        } else {
            options.put("vo", "gpu,xv,x11");
            // mpv 0.41 has no GLX context any more.
            options.put("gpu-context", "x11egl,x11vk");
        }
        options.put("hwdec", "auto-safe");
        if (!gpu) options.put("profile", "fast");
        return options;
    }

    static Map<String, String> forThisMachine() {
        String os = System.getProperty("os.name", "");
        boolean gpu = !os.startsWith("Linux") || hasRenderNode(Path.of("/dev/dri"));
        return options(os, gpu);
    }

    /** True if the kernel offers a GPU render node ({@code /dev/dri/renderD128}, ...). */
    static boolean hasRenderNode(Path dri) {
        if (!Files.isDirectory(dri)) return false;
        try (Stream<Path> nodes = Files.list(dri)) {
            return nodes.anyMatch(p -> p.getFileName().toString().startsWith("renderD"));
        } catch (IOException e) {
            return false;
        }
    }
}
