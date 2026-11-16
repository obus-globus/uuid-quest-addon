package quest.uuid.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionHistoryTest {

    @TempDir
    Path dir;

    private static Submission submission(String id) {
        return new Submission(id, 1L, 3, "note", "queued", Map.of("received", 3, "queued", 3), List.of("a.example"));
    }

    @Test
    void savesAndLoadsAgain() throws Exception {
        Path file = dir.resolve("uuid-quest").resolve("submissions.json");
        var history = new SubmissionHistory(file, new TestSupport.RecordingLog());
        Submission first = submission("one");
        history.add(first);
        history.add(submission("two"));
        first.update(JsonParser.parseString(
            "{\"state\":\"done\",\"earlyExited\":true,\"counts\":{\"received\":3,\"forwarded\":2}}").getAsJsonObject());
        history.save();

        assertFalse(Files.exists(file.resolveSibling("submissions.json.tmp")));
        var loaded = new SubmissionHistory(file, new TestSupport.RecordingLog());
        assertEquals(2, loaded.size());
        assertEquals("two", loaded.recent(1).get(0).submitId());
        assertEquals(List.of("two"), loaded.unfinished().stream().map(Submission::submitId).toList());
        assertEquals(2, loaded.total("forwarded"));
        assertEquals(6, loaded.total("received"));
        Submission reloaded = loaded.recent(2).get(1);
        assertTrue(reloaded.earlyExited());
        assertEquals("note", reloaded.note());
        assertEquals(List.of("a.example"), reloaded.servers());
    }

    @Test
    void readsTheLiquidBounceAddonsFormat() throws Exception {
        Path file = dir.resolve("submissions.json");
        Files.writeString(file, """
            [
              {
                "submitId": "0b9b8f7e-1",
                "createdAt": 1763251200000,
                "sent": 42,
                "state": "done",
                "counts": {"received": 42, "forwarded": 3, "alreadyKnown": 39},
                "earlyExited": false,
                "lastPolled": 1763251260000
              },
              {"submitId": "missing-state", "counts": {}},
              null
            ]
            """);
        var history = new SubmissionHistory(file, new TestSupport.RecordingLog());
        assertEquals(1, history.size());
        Submission submission = history.recent(1).get(0);
        assertEquals(List.of(), submission.servers());
        assertEquals("3 new, 39 known, 0 fake", submission.summaryCounts());
    }

    @Test
    void movesACorruptFileAside() throws Exception {
        Path file = dir.resolve("submissions.json");
        Files.writeString(file, "{not json");
        var log = new TestSupport.RecordingLog();
        var history = new SubmissionHistory(file, log);
        assertEquals(0, history.size());
        assertFalse(Files.exists(file));
        assertEquals("{not json", Files.readString(dir.resolve("submissions.json.bak")));
        assertTrue(log.lines.stream().anyMatch(line -> line.startsWith("WARN Could not read")));

        history.add(submission("fresh"));
        assertEquals(1, new SubmissionHistory(file, log).size());
    }

    @Test
    void keepsTheLastHundred() {
        var history = new SubmissionHistory(dir.resolve("submissions.json"), new TestSupport.RecordingLog());
        for (int i = 0; i < SubmissionHistory.MAX_KEPT + 5; i++) history.add(submission("id" + i));
        assertEquals(SubmissionHistory.MAX_KEPT, history.size());
        assertEquals("id104", history.recent(1).get(0).submitId());
        assertEquals("id5", history.recent(SubmissionHistory.MAX_KEPT).get(SubmissionHistory.MAX_KEPT - 1).submitId());
    }

    @Test
    void twoInstancesSharingTheFolderKeepEachOthersSubmissions() {
        var log = new TestSupport.RecordingLog();
        Path file = dir.resolve("submissions.json");
        var first = new SubmissionHistory(file, log);
        var second = new SubmissionHistory(file, log);
        first.add(new Submission("aaa", 1, 1, null, "queued", java.util.Map.of(), List.of()));
        second.add(new Submission("bbb", 2, 1, null, "queued", java.util.Map.of(), List.of()));

        var reloaded = new SubmissionHistory(file, log);
        assertEquals(List.of("bbb", "aaa"), reloaded.recent(10).stream().map(Submission::submitId).toList());
    }

    @Test
    void entriesWithAnUnusableIdAreDropped() throws Exception {
        Path file = dir.resolve("submissions.json");
        java.nio.file.Files.writeString(file, "[{\"submitId\":\"../../x y\",\"state\":\"queued\",\"counts\":{}},"
            + "{\"submitId\":\"ok-1\",\"state\":\"queued\",\"counts\":{}}]");
        var history = new SubmissionHistory(file, new TestSupport.RecordingLog());
        assertEquals(List.of("ok-1"), history.recent(10).stream().map(Submission::submitId).toList());
    }

}
