package eu.amadeos.apps.tv.repository;

import eu.amadeos.apps.tv.domain.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RecordingStoreTest {
    static final Instant T = Instant.parse("2026-10-26T19:13:00Z");

    @TempDir
    Path config;

    static Recording recording(String id, Instant start, Recording.Status status) {
        return new Recording(id, "zdf", "ZDF", "heute-show", start, start, start.plusSeconds(3600),
                Path.of("/v/" + id + ".mkv"), status, 0, "");
    }

    @Test
    void keepsRecordingsByStartTime() {
        RecordingStore store = new RecordingStore(config);
        store.add(recording("b", T.plusSeconds(60), Recording.Status.PLANNED));
        store.add(recording("a", T, Recording.Status.PLANNED));
        store.update("a", r -> r.withStatus(Recording.Status.DONE, "").withBytes(5_000_000_000L));
        RecordingStore again = new RecordingStore(config);
        assertEquals(2, again.all().size());
        Recording a = again.get("a").orElseThrow();
        assertEquals("a", again.all().getFirst().id());
        assertEquals(Recording.Status.DONE, a.status());
        assertEquals(5_000_000_000L, a.bytes());
        assertEquals("heute-show", a.title());
        assertEquals(Path.of("/v/a.mkv"), a.file());
        again.remove("b");
        assertEquals(1, new RecordingStore(config).all().size());
    }

    @Test
    void aRecordingThatRanWhenTvEndedIsStopped() {
        new RecordingStore(config).add(recording("x", T, Recording.Status.RECORDING));
        assertEquals(Recording.Status.STOPPED, new RecordingStore(config).get("x").orElseThrow().status());
    }

    @Test
    void anUnreadableEntryIsKept() throws Exception {
        Files.writeString(config.resolve("recordings.json"), "[{\"id\": \"bad\", \"start\": \"yesterday\"}]");
        RecordingStore store = new RecordingStore(config);
        assertTrue(store.all().isEmpty());
        store.add(recording("a", T, Recording.Status.PLANNED));
        assertTrue(Files.readString(config.resolve("recordings.json")).contains("yesterday"));
        assertEquals(1, new RecordingStore(config).all().size());
    }

    @Test
    void anUnreadableFileIsIgnored() throws Exception {
        Files.writeString(config.resolve("recordings.json"), "[{\"id\":");
        assertTrue(new RecordingStore(config).all().isEmpty());
    }
}
