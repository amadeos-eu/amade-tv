package eu.amadeos.apps.tv.service.record;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.domain.Recording;
import eu.amadeos.apps.tv.repository.RecordingStore;
import eu.amadeos.apps.tv.testing.Fixtures;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.FinishedProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecorderTest {
    static final Instant START = Instant.parse("2026-10-26T19:15:00Z");
    static final Channel ZDF = Fixtures.channel("zdf", "ZDF", Channel.GuideSourceId.ZDF);
    static final Broadcast SHOW = new Broadcast("zdf", START, START.plusSeconds(1800), "heute-show", "", "");

    @TempDir
    Path config;
    @TempDir
    Path videos;

    final List<List<String>> started = new ArrayList<>();

    Recorder recorder(CommandRunner runner) {
        return new Recorder(new RecordingStore(config), command -> {
            started.add(command);
            return runner.start(command);
        }, videos, ZoneId.of("Europe/Berlin"));
    }

    static Recording waitUntilOver(Recorder recorder, String id) throws InterruptedException {
        for (int i = 0; i < 500; i++) {
            Recording r = recorder.all().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
            if (r.status().isOver()) return r;
            Thread.sleep(10);
        }
        fail("still recording");
        return null;
    }

    @Test
    void startsWhenDueAndRecordsToTheEnd() throws Exception {
        Recorder recorder = recorder(c -> FinishedProcess.printing("total_size=N/A\nprogress=continue\n"
                + "total_size=123456\nprogress=end\n"));
        Recording r = recorder.plan(SHOW, ZDF, START.minusSeconds(3600));
        recorder.tick(START.minusSeconds(600), Map.of("zdf", ZDF)::get);
        assertTrue(started.isEmpty());
        assertEquals(r, recorder.recordingOf(SHOW).orElseThrow());

        recorder.tick(r.start(), Map.of("zdf", ZDF)::get);
        Recording done = waitUntilOver(recorder, r.id());
        assertEquals(Recording.Status.DONE, done.status());
        assertEquals(123456, done.bytes());
        assertTrue(recorder.recordingOf(SHOW).isEmpty());
        List<String> command = started.getFirst();
        assertEquals("ffmpeg", command.getFirst());
        assertEquals(ZDF.streamUrl(), command.get(command.indexOf("-i") + 1));
        assertEquals(Long.toString(r.remaining(r.start()).toSeconds()), command.get(command.indexOf("-t") + 1));
        assertEquals(r.file().toString(), command.getLast());
    }

    @Test
    void aFailingStreamKeepsFfmpegsMessage() throws Exception {
        Recorder recorder = recorder(c -> FinishedProcess.failing(1, "Server returned 403 Forbidden\n"));
        Recording r = recorder.plan(SHOW, ZDF, START);
        recorder.tick(START, Map.of("zdf", ZDF)::get);
        Recording failed = waitUntilOver(recorder, r.id());
        assertEquals(Recording.Status.FAILED, failed.status());
        assertEquals("Server returned 403 Forbidden", failed.error());
    }

    @Test
    void missedAndChannelsWithoutStreamFail() {
        Recorder recorder = recorder(c -> FinishedProcess.printing(""));
        Recording missed = recorder.plan(SHOW, ZDF, START.minusSeconds(3600));
        Channel silent = new Channel("zdf", "ZDF", "", "", 0, Channel.GuideSourceId.ZDF, List.of());
        Broadcast later = new Broadcast("zdf", START.plusSeconds(7200), START.plusSeconds(9000), "Krimi", "", "");
        Recording noStream = recorder.plan(later, silent, START);
        recorder.tick(START.plusSeconds(3600), Map.of("zdf", ZDF)::get);
        recorder.tick(later.start(), Map.of("zdf", silent)::get);
        assertEquals(Recorder.MISSED, recorder.all().stream().filter(x -> x.id().equals(missed.id())).findFirst().orElseThrow().error());
        assertEquals(Recorder.NO_STREAM, recorder.all().stream().filter(x -> x.id().equals(noStream.id())).findFirst().orElseThrow().error());
        assertTrue(started.isEmpty());
    }

    @Test
    void stopEndsTheRecordingAndCancelRemovesAPlannedOne() throws Exception {
        Recorder recorder = recorder(c -> new ProcessBuilder("sleep", "30").start());
        Recording r = recorder.plan(SHOW, ZDF, START);
        recorder.tick(START, Map.of("zdf", ZDF)::get);
        assertTrue(recorder.isRecording());
        recorder.stop(r.id());
        assertEquals(Recording.Status.STOPPED, waitUntilOver(recorder, r.id()).status());
        assertFalse(recorder.isRecording());

        Broadcast later = new Broadcast("zdf", START.plusSeconds(7200), START.plusSeconds(9000), "Krimi", "", "");
        Recording planned = recorder.plan(later, ZDF, START);
        recorder.stop(planned.id());
        assertTrue(recorder.recordingOf(later).isEmpty());
        assertEquals(1, recorder.all().size());
    }

    @Test
    void anEarlierRecordingIsNeverOverwritten() throws Exception {
        Path file = videos.resolve("heute-show (ZDF, 2026-10-26 20.15).mkv");
        Files.createFile(file);
        Files.createFile(videos.resolve("heute-show (ZDF, 2026-10-26 20.15) (2).mkv"));
        assertEquals(videos.resolve("heute-show (ZDF, 2026-10-26 20.15) (3).mkv"), Recorder.freeName(file));
        assertEquals(videos.resolve("new.mkv"), Recorder.freeName(videos.resolve("new.mkv")));
    }
}
