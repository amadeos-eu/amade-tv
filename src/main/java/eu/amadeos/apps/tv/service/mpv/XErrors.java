package eu.amadeos.apps.tv.service.mpv;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;

/**
 * Keeps an X error from ending the program. The X error handler is one for the whole process: AWT sets one
 * that only logs, mpv's video output replaces it with its own while it runs, and when the video output
 * closes (stop, end of the stream, quit) mpv sets it back to Xlib's default, which ends the process on
 * the first error. AWT gets such errors in normal work, such as BadWindow while windows are disposed,
 * so after mpv's video output closed {@link #restore} puts AWT's handler back.
 */
final class XErrors {
    private static final Logger LOG = LoggerFactory.getLogger(XErrors.class);

    interface X11 extends Library {
        Pointer XSetErrorHandler(Pointer handler);
    }

    interface Handler extends Callback {
        int invoke(Pointer display, Pointer event);
    }

    /** Stands in while AWT's handler is read, and when there is none; kept here so it is never collected. */
    private static final Handler IGNORE = (display, event) -> 0;

    private static X11 x11;
    private static Pointer saved;

    private XErrors() {
    }

    /** Reads the handler AWT set; call before mpv opens a video output. */
    static synchronized void remember() {
        if (x11 != null || !isX11()) return;
        try {
            X11 lib = Native.load("X11", X11.class);
            Pointer ignore = CallbackReference.getFunctionPointer(IGNORE);
            Pointer awt = lib.XSetErrorHandler(ignore);
            saved = awt == null ? ignore : awt;
            lib.XSetErrorHandler(saved);
            x11 = lib;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            LOG.debug("libX11 not available, X error handler left alone", e);
        }
    }

    /** Puts AWT's handler back after mpv's video output closed. */
    static synchronized void restore() {
        if (x11 != null) x11.XSetErrorHandler(saved);
    }

    private static boolean isX11() {
        return !GraphicsEnvironment.isHeadless()
                && Toolkit.getDefaultToolkit().getClass().getName().equals("sun.awt.X11.XToolkit");
    }
}
