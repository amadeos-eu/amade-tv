package eu.amadeos.apps.tv.i18n;

import eu.amadeos.apps.tv.bootstrap.AmadeTvApplication;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.Texts;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(AmadeTvApplication.class, Texts.bundle()));
    }

    @Test
    void mnemonicsAreUniqueWithinEachMenu() {
        List<List<String>> menus = List.of(
                List.of("menu.file", "menu.view", "menu.playback", "menu.channels", "menu.help"),
                List.of("menu.live", "menu.guide", "menu.recordings", "menu.library", "menu.find", "menu.full-screen"),
                List.of("menu.play-pause", "menu.back", "menu.to-live", "menu.stop", "menu.mute", "menu.louder",
                        "menu.quieter", "menu.subtitles", "menu.audio"),
                List.of("menu.previous-channel", "menu.next-channel", "menu.group.regional",
                        "menu.group.atch", "menu.group.international", "menu.group.more", "menu.refresh"),
                // The sidebar buttons are in the window together with each page's buttons.
                List.of("page.live", "page.guide", "page.recordings", "page.library", "guide.now", "guide.prime-time", "guide.find",
                        "guide.watch", "guide.remind", "record"),
                List.of("page.live", "page.guide", "page.recordings", "page.library", "guide.now", "guide.prime-time", "guide.find",
                        "guide.watch", "guide.remind.cancel", "record.cancel"),
                List.of("page.live", "page.guide", "page.recordings", "page.library", "guide.now", "guide.prime-time", "guide.find",
                        "guide.watch", "guide.remind.cancel", "record.stop"),
                List.of("page.live", "page.guide", "page.recordings", "page.library", "live.mute", "live.subtitles",
                        "live.full-screen", "guide.remind", "record"),
                List.of("page.live", "page.guide", "page.recordings", "page.library", "live.mute", "live.subtitles",
                        "live.full-screen", "guide.remind.cancel", "record.stop"),
                List.of("page.live", "page.guide", "page.recordings", "page.library", "recordings.play", "recordings.folder",
                        "record.stop", "recordings.remove"),
                List.of("page.live", "page.guide", "page.recordings", "page.library", "recordings.play", "recordings.folder",
                        "record.cancel", "recordings.remove"),
                List.of("page.live", "page.guide", "page.recordings", "page.library", "library.find", "library.play",
                        "library.more"),
                List.of("reminder.watch", "reminder.dismiss"));
        for (String language : List.of("en", "de", "fr", "es", "it")) {
            Messages m = Messages.load(AmadeTvApplication.class, Texts.bundle(), Locale.of(language));
            for (List<String> menu : menus) {
                Set<Character> letters = new HashSet<>();
                for (String key : menu) {
                    Mnemonics.Text t = Mnemonics.parse(m.get(key));
                    assertTrue(t.index() >= 0, language + " " + key + " has no mnemonic");
                    char c = Character.toLowerCase(t.text().charAt(t.index()));
                    assertTrue(letters.add(c), language + " " + key + ": '" + c + "' used twice");
                }
            }
        }
    }
}
