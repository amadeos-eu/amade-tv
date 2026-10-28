package eu.amadeos.apps.tv.ui.library;

import eu.amadeos.apps.tv.domain.Film;
import eu.amadeos.commons.i18n.Texts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Library page without the service; needs a display (CI runs the tests under Xvfb). */
class LibraryPageTest {
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final Film TATORT = film("1", "Tatort", "Tod am Deich", "https://example.org/t.m3u8");
    static final Film NO_VIDEO = film("2", "", "Nur Text", "");

    static Film film(String id, String topic, String title, String hls) {
        return new Film(id, "Das Erste", topic, title, "Ein Toter am Deich.", Instant.parse("2026-10-25T19:15:00Z"),
                Duration.ofSeconds(5310), Instant.parse("2026-11-24T22:59:00Z"), "", "", "", "", hls, false, true);
    }

    @BeforeAll
    static void texts() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Texts.use(LibraryPageTest.class, "eu.amadeos.apps.tv.messages");
    }

    static final class Recorder implements LibraryPage.Actions {
        final List<String> calls = new ArrayList<>();

        @Override
        public void search(String text) {
            calls.add("search " + text);
        }

        @Override
        public void moreFilms() {
            calls.add("more");
        }

        @Override
        public void play(Film f) {
            calls.add("play " + f.id());
        }
    }

    @Test
    void enterSearchesAndPlayPlaysTheSelectedFilm() {
        Recorder actions = new Recorder();
        LibraryPage page = new LibraryPage(actions, BERLIN);
        page.find.setText("tatort");
        page.find.postActionEvent();
        page.setLoading();
        page.setFilms(List.of(TATORT, NO_VIDEO), 51, true);
        assertEquals("2 of 51 films", page.status.getText());
        assertTrue(page.more.isVisible());
        assertFalse(page.play.isEnabled());

        page.table.setRowSelectionInterval(0, 0);
        assertTrue(page.play.isEnabled());
        assertEquals("Tatort: Tod am Deich", page.table.getValueAt(0, 0));
        assertEquals("89 min", page.table.getValueAt(0, 3));
        page.play.doClick();
        page.more.doClick();
        assertEquals(List.of("search tatort", "play 1", "more"), actions.calls);

        page.table.setRowSelectionInterval(1, 1);
        assertFalse(page.play.isEnabled(), "a film without a video cannot be played");
    }

    @Test
    void detailsNameChannelLengthAndHowLongItIsThere() {
        LibraryPage page = new LibraryPage(new Recorder(), BERLIN);
        String details = page.details(TATORT);
        assertTrue(details.startsWith("Tatort: Tod am Deich\nDas Erste, "), details);
        assertTrue(details.contains("20:15, 89 min, sign language\nAvailable until "), details);
        assertTrue(details.endsWith("\n\nEin Toter am Deich."), details);
    }

    @Test
    void moreFilmsKeepTheSelection() {
        LibraryPage page = new LibraryPage(new Recorder(), BERLIN);
        page.setLoading();
        page.setFilms(List.of(TATORT), 2, true);
        page.table.setRowSelectionInterval(0, 0);
        page.setFilms(List.of(TATORT, NO_VIDEO), 2, false);
        assertSame(TATORT, page.selected());
        assertFalse(page.more.isVisible());
        page.setFailed("HTTP 502");
        assertEquals("The TV service did not answer: HTTP 502", page.status.getText());
    }
}
