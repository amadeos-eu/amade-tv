package eu.amadeos.apps.tv.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelTest {
    private final Channel kika = new Channel("kika", "KiKA", "https://k/master.m3u8", "", 0, Channel.GuideSourceId.ZDF,
            List.of("KI.KA"));

    @Test
    void guideNamesAreComparedWithoutCaseSpacesAndDots() {
        assertTrue(kika.isCalled("KI.KA"));
        assertTrue(kika.isCalled("ki ka"));
        assertTrue(kika.isCalled("KIKA"));
        assertTrue(kika.isCalled("kika"));
        assertFalse(kika.isCalled("ZDF"));
        assertFalse(kika.isCalled(""));
        assertFalse(kika.isCalled(null));
    }

    @Test
    void softHyphensInServiceNamesDoNotMatter() {
        Channel parliament = new Channel("parlamentsfernsehen_1", "Parlamentsfernsehen 1", "", "", 0, null, List.of());
        assertTrue(parliament.isCalled("Parlaments­fern­sehen 1"));
    }

    @Test
    void defaults() {
        Channel c = new Channel("x", " ", null, null, -90, null, null);
        assertEquals("x", c.name());
        assertEquals("", c.streamUrl());
        assertEquals(Channel.GuideSourceId.NONE, c.guide());
        assertEquals(270, c.logoTurn());
        assertEquals(List.of(), c.guideNames());
    }

    @Test
    void aNewStreamAddressKeepsTheRest() {
        Channel moved = kika.withStreamUrl("https://new/master.m3u8");
        assertEquals("https://new/master.m3u8", moved.streamUrl());
        assertEquals(kika.guideNames(), moved.guideNames());
        assertEquals(kika.name(), moved.name());
    }
}
