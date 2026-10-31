package eu.amadeos.apps.tv.ui.main;

import eu.amadeos.commons.desktop.Notification;
import eu.amadeos.commons.i18n.Texts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** The reminder that comes when a program starts, as a desktop notification. */
class ReminderNotificationTest {

    @BeforeAll
    static void english() {
        Locale.setDefault(Locale.ENGLISH);
        Texts.use(ReminderNotificationTest.class, "eu.amadeos.apps.tv.messages");
    }

    @Test
    void aReminderOffersWatchAndSwitchesToTheChannelOnTheEdt() throws Exception {
        AtomicBoolean onEdt = new AtomicBoolean();
        Notification n = MainWindow.reminder("Tagesschau", "Tagesschau starts on Das Erste at 20:00 & more.", null,
                () -> onEdt.set(SwingUtilities.isEventDispatchThread()));

        assertEquals("Tagesschau", n.summary());
        assertEquals("Tagesschau starts on Das Erste at 20:00 &amp; more.", n.body());
        assertEquals("x-amade.tv.reminder", n.category());
        assertEquals(1, n.actions().size());
        assertEquals("watch", n.actions().get(0).id());
        assertEquals("Watch", n.actions().get(0).label());

        // The daemon's answer comes on a background thread; the switch happens on the EDT.
        Thread t = new Thread(n.actions().get(0).run());
        t.start();
        t.join();
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(onEdt.get());

        onEdt.set(false);
        n.defaultAction().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(onEdt.get());
    }
}
