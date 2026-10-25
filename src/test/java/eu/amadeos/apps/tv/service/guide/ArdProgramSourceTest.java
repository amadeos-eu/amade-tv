package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static eu.amadeos.apps.tv.testing.Fixtures.channel;
import static org.junit.jupiter.api.Assertions.*;

class ArdProgramSourceTest {
    static final List<Channel> CHANNELS = List.of(
            channel("das_erste", "Das Erste", Channel.GuideSourceId.ARD, "daserste"),
            channel("ndr_hh", "NDR Hamburg", Channel.GuideSourceId.ARD, "NDR", "NDR Fernsehen"),
            channel("zdf", "ZDF", Channel.GuideSourceId.ZDF, "ZDF"));

    @Test
    void readsTheDayOfTheKnownChannels() throws IOException {
        Fixtures.FakeWeb web = new Fixtures.FakeWeb().answer(ArdProgramSource.URL, Fixtures.read("ard-program.json"));
        List<Broadcast> items = new ArdProgramSource(web).day(LocalDate.of(2026, 10, 25), CHANNELS);
        assertEquals(List.of("GET https://programm-api.ard.de/program/api/program?day=2026-10-25"), web.requests);
        assertEquals(4, items.size());

        Broadcast news = items.get(0);
        assertEquals("das_erste", news.channelId());
        assertEquals("Tagesschau", news.title());
        assertEquals(Instant.parse("2026-10-25T19:00:00Z"), news.start());
        assertEquals(Instant.parse("2026-10-25T19:15:00Z"), news.end());
        assertEquals("Die Nachrichten des Tages.\nMit dem Wetter.", news.description());

        Broadcast tatort = items.get(1);
        assertEquals("Nebel über dem Hafen", tatort.subtitle());
        assertEquals("Ein Frachter läuft ohne Besatzung ein & niemand weiß, warum.", tatort.description());

        // Without a title the core title is used; items without times are left out
        assertEquals("Caren Miosga", items.get(2).title());
        assertEquals("Talk", items.get(2).subtitle());

        // NDR by its service name, with an offset time; unknown channels are left out
        assertEquals("ndr_hh", items.get(3).channelId());
        assertEquals(Instant.parse("2026-10-25T19:00:00Z"), items.get(3).start());
    }

    @Test
    void onlyArdChannelsAreMatched() throws IOException {
        String json = "{\"channels\": [{\"id\": \"zdf\", \"publicationService\": {\"name\": \"ZDF\"}, \"timeSlots\": [[{\"title\": \"x\","
                + " \"broadcastedOn\": \"2026-10-25T19:00:00Z\", \"broadcastEnd\": \"2026-10-25T19:15:00Z\"}]]}]}";
        assertEquals(List.of(), ArdProgramSource.parse(json, CHANNELS));
    }

    @Test
    void notJsonIsAnError() {
        assertThrows(IOException.class, () -> ArdProgramSource.parse("<html>Wartung</html>", CHANNELS));
        assertEquals(List.of(), assertDoesNotThrow(() -> ArdProgramSource.parse("{}", CHANNELS)));
    }
}
