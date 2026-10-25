package eu.amadeos.apps.tv.ui.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Schedule;
import eu.amadeos.apps.tv.ui.main.LogoSource;
import eu.amadeos.commons.i18n.Texts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static eu.amadeos.apps.tv.testing.Fixtures.channel;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The guide page with a made-up evening; needs a display (CI runs the tests under Xvfb). */
class GuidePageTest {
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final Instant NOW = Instant.parse("2026-10-25T19:30:00Z");
    static final List<Channel> CHANNELS = List.of(channel("das_erste", "Das Erste", Channel.GuideSourceId.ARD),
            channel("zdf", "ZDF", Channel.GuideSourceId.ZDF));
    static final Broadcast TATORT = new Broadcast("das_erste", Instant.parse("2026-10-25T19:15:00Z"),
            Instant.parse("2026-10-25T20:45:00Z"), "Tatort", "Nebel über dem Hafen", "Ein Frachter läuft ein.");
    static final Broadcast JOURNAL = new Broadcast("zdf", Instant.parse("2026-10-25T20:45:00Z"),
            Instant.parse("2026-10-25T21:15:00Z"), "heute journal", "", "");
    static final Broadcast TERRA = new Broadcast("zdf", Instant.parse("2026-10-25T18:30:00Z"),
            Instant.parse("2026-10-25T19:15:00Z"), "Terra X", "", "");

    @BeforeAll
    static void texts() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Texts.use(GuidePageTest.class, "eu.amadeos.apps.tv.messages");
    }

    static final class Recorder implements GuidePage.Actions {
        final List<String> calls = new ArrayList<>();
        final Set<Broadcast> reminded = new HashSet<>();

        public void showDay(LocalDate day) {
            calls.add("day " + day);
        }

        public void watch(Broadcast b) {
            calls.add("watch " + b.title());
        }

        public void toggleReminder(Broadcast b) {
            calls.add("remind " + b.title());
            if (!reminded.remove(b)) reminded.add(b);
        }
    }

    GuidePage page(Recorder r) throws Exception {
        GuidePage[] page = new GuidePage[1];
        SwingUtilities.invokeAndWait(() -> {
            page[0] = new GuidePage(r, BERLIN, LogoSource.NONE);
            page[0].setReminded(r.reminded::contains);
            page[0].setChannels(CHANNELS);
            page[0].setDay(LocalDate.of(2026, 10, 25), NOW);
            page[0].setSchedule(Schedule.of(List.of(TATORT, JOURNAL, TERRA)), NOW);
            page[0].setSize(1000, 500);
            page[0].doLayout();
        });
        return page[0];
    }

    @Test
    void clickingSelectsAndShowsTheDetails() throws Exception {
        Recorder r = new Recorder();
        GuidePage p = page(r);
        SwingUtilities.invokeAndWait(() -> {
            TimelineLayout t = p.grid.timeline();
            Point inTatort = new Point(t.x(NOW), GuideGrid.ROW_HEIGHT / 2);
            assertEquals(TATORT, p.grid.itemAt(inTatort));
            assertNull(p.grid.itemAt(new Point(t.x(NOW), GuideGrid.ROW_HEIGHT * 5)));
            p.grid.select(TATORT);
            assertTrue(p.detailTitle.getText().startsWith("Tatort – Nebel über dem Hafen  ·  Das Erste  ·  20:15 – 21:45"));
            assertEquals("Ein Frachter läuft ein.", p.detailText.getText());
            // Running: watch yes, remind no
            assertTrue(p.watch.isEnabled());
            assertFalse(p.remind.isEnabled());
            p.watch.doClick();
            p.grid.select(JOURNAL);
            assertFalse(p.watch.isEnabled());
            assertTrue(p.remind.isEnabled());
            assertEquals("Remind Me", p.remind.getText());
            p.remind.doClick();
            assertEquals("Cancel Reminder", p.remind.getText());
            p.grid.select(TERRA);
            assertFalse(p.watch.isEnabled());
            assertFalse(p.remind.isEnabled());
        });
        assertEquals(List.of("watch Tatort", "remind heute journal"), r.calls);
    }

    @Test
    void findMarksAndStepsThroughTheHits() throws Exception {
        Recorder r = new Recorder();
        GuidePage p = page(r);
        SwingUtilities.invokeAndWait(() -> {
            p.find.setText("t");
            assertEquals("3 found, Enter shows them", p.findResult.getText());
            p.find.setText("tatort");
            assertEquals("1 found, Enter shows them", p.findResult.getText());
            p.find.postActionEvent();
            assertEquals(TATORT, p.grid.selected());
            assertEquals("1 of 1", p.findResult.getText());
            p.find.setText("Sportschau");
            assertEquals("nothing found", p.findResult.getText());
            p.find.setText("");
            assertEquals(" ", p.findResult.getText());
        });
    }

    @Test
    void dayButtonsAskForTheNeighbours() throws Exception {
        Recorder r = new Recorder();
        GuidePage p = page(r);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(p.day.getText().contains("2026"), p.day.getText());
            ((JButton) ((JPanel) p.getComponent(0)).getComponent(0)).doClick();
            ((JButton) ((JPanel) p.getComponent(0)).getComponent(2)).doClick();
        });
        assertEquals(List.of("day 2026-10-24", "day 2026-10-26"), r.calls);
    }

    @Test
    void paintsWithoutErrors() throws Exception {
        GuidePage p = page(new Recorder());
        SwingUtilities.invokeAndWait(() -> {
            p.grid.setSize(p.grid.getPreferredSize());
            BufferedImage img = new BufferedImage(1200, 120, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setClip(0, 0, 1200, 120);
            g.translate(-p.grid.timeline().x(NOW) + 300, 0);
            p.grid.setHighlight("journal");
            p.grid.paint(g);
            g.dispose();
            // The red now line
            assertEquals(GuideGrid.NOW_LINE.getRGB(), img.getRGB(300, 60));
        });
    }
}
