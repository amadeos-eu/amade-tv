package eu.amadeos.apps.tv.service.net;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KodinerdsListTest {
    @Test
    void addressesByTvgId() {
        String m3u = """
                #EXTM3U
                #EXTINF:-1 tvg-name="ORF eins HD" tvg-id="ORF1.at" group-title="IPTV-AT" tvg-logo="https://x/orf1hd.png",ORF eins HD
                https://orf1.mdn.ors.at/out/u/orf1/q6a/manifest_6.m3u8
                #EXTINF:-1 tvg-name="No id" group-title="IPTV-DE",No id
                https://example.org/noid.m3u8
                #EXTINF:-1 tvg-id="Radio.de",Radio
                rtmp://example.org/live
                #EXTINF:-1 tvg-id="WELT.de",WELT
                #EXTVLCOPT:http-user-agent=Mozilla
                https://welt.personalstream.tv/v1/master.m3u8
                """;
        assertEquals(Map.of("ORF1.at", "https://orf1.mdn.ors.at/out/u/orf1/q6a/manifest_6.m3u8",
                "WELT.de", "https://welt.personalstream.tv/v1/master.m3u8"), KodinerdsList.parse(m3u));
    }
}
