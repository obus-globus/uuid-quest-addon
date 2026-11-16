package quest.uuid.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static quest.uuid.core.Submitter.FlushResult.IN_FLIGHT;
import static quest.uuid.core.Submitter.FlushResult.NOTHING_PENDING;
import static quest.uuid.core.Submitter.FlushResult.SENT;
import static quest.uuid.core.Submitter.FlushResult.WAITING;

class SubmitterTest {

    @TempDir
    Path dir;

    private final TestSupport.Clock clock = new TestSupport.Clock();
    private final FakeTransport transport = new FakeTransport();
    private final ServerGuard guard = new ServerGuard();
    private final List<QuestText> reports = new ArrayList<>();
    private SubmissionHistory history;
    private Submitter submitter;

    @BeforeEach
    void setUp() {
        history = new SubmissionHistory(dir.resolve("submissions.json"), new TestSupport.RecordingLog());
        submitter = new Submitter(transport, history, guard, new TestSupport.RecordingLog(), clock, "uuid-quest/test");
    }

    private JsonObject body(int request) {
        return JsonParser.parseString(transport.requests.get(request).body()).getAsJsonObject();
    }

    @Test
    void deduplicatesForTheSession() {
        UUID uuid = TestSupport.v4();
        submitter.offer(uuid, "a.example");
        submitter.offer(uuid, "b.example");
        assertEquals(1, submitter.pendingCount());
        assertEquals(1, submitter.rememberedCount());

        transport.accepted("s1", 1);
        assertEquals(SENT, submitter.flush("", reports::add));
        submitter.offer(uuid, "a.example");
        assertEquals(0, submitter.pendingCount());
        assertEquals(NOTHING_PENDING, submitter.flush("", reports::add));
    }

    @Test
    void isDueOnceTheOldestUuidHasWaitedTheInterval() {
        assertFalse(submitter.isDue(60));
        submitter.offer(TestSupport.v4(), "a.example");
        clock.advanceSeconds(59);
        submitter.offer(TestSupport.v4(), "a.example");
        assertFalse(submitter.isDue(60));
        clock.advanceSeconds(1);
        assertTrue(submitter.isDue(60));

        transport.accepted("s1", 2);
        submitter.flush("", reports::add);
        assertFalse(submitter.isDue(60));
        submitter.offer(TestSupport.v4(), "a.example");
        clock.advanceSeconds(59);
        assertFalse(submitter.isDue(60));
    }

    @Test
    void postsTheBatchWithNoteAndUserAgent() {
        UUID uuid = TestSupport.v4();
        submitter.offer(uuid, "a.example");
        transport.accepted("0123456789abcdef", 1);
        submitter.flush("  my label  ", reports::add);

        var request = transport.requests.get(0);
        assertEquals("https://uuid.quest/v1/uuids", request.uri().toString());
        assertEquals("uuid-quest/test", request.userAgent());
        assertEquals(uuid.toString(), body(0).getAsJsonArray("uuids").get(0).getAsString());
        assertEquals("my label", body(0).get("note").getAsString());

        Submission submission = history.recent(1).get(0);
        assertEquals("0123456789abcdef", submission.submitId());
        assertEquals(List.of("a.example"), submission.servers());
        assertEquals("my label", submission.note());
        assertEquals("uuid.quest 01234567: queued 1 of 1 UUIDs", reports.get(0).plain());
    }

    @Test
    void leavesOutAnEmptyNote() {
        submitter.offer(TestSupport.v4(), "a.example");
        transport.accepted("s1", 1);
        submitter.flush("   ", reports::add);
        assertFalse(body(0).has("note"));
    }

    @Test
    void sendsAtMostTenThousandPerBatch() {
        for (int i = 0; i < Submitter.MAX_BATCH + 5; i++) submitter.offer(TestSupport.v4(), "a.example");
        transport.accepted("s1", Submitter.MAX_BATCH);
        submitter.flush("", reports::add);
        assertEquals(Submitter.MAX_BATCH, body(0).getAsJsonArray("uuids").size());
        assertEquals(5, submitter.pendingCount());
        assertTrue(submitter.isDue(0));
    }

    @Test
    void retriesAfterTheServersWait() {
        submitter.offer(TestSupport.v4(), "a.example");
        submitter.offer(TestSupport.v4(), "a.example");
        transport.respond(503, null, "120", "");
        assertEquals(SENT, submitter.flush("", reports::add));

        assertEquals(2, submitter.pendingCount());
        assertEquals(120, submitter.waitSeconds());
        assertEquals(WAITING, submitter.flush("", reports::add));
        clock.advanceSeconds(120);
        transport.accepted("s1", 2);
        assertEquals(SENT, submitter.flush("", reports::add));
        assertEquals(0, submitter.pendingCount());
        assertEquals(2, transport.requests.size());
    }

    @Test
    void reportsRateLimitingButWarnsAboutAbuseThrottling() {
        var penalties = new ArrayList<Long>();
        submitter.setOnAbuseThrottled(penalties::add);
        submitter.offer(TestSupport.v4(), "a.example");

        transport.respond(429, "application/problem+json", "30", "{\"type\":\"https://uuid.quest/problems/rate-limited\"}");
        submitter.flush("", reports::add);
        assertEquals("uuid.quest is rate limiting, retrying in 30s", reports.get(0).plain());
        assertEquals(List.of(), penalties);

        clock.advanceSeconds(30);
        transport.respond(429, "application/problem+json", "3600",
            "{\"type\":\"https://uuid.quest/problems/abuse-throttled\"}");
        submitter.flush("", reports::add);
        assertEquals(List.of(3600L), penalties);
        assertEquals(1, reports.size());
        assertEquals(1, submitter.pendingCount());
    }

    @Test
    void retriesNetworkErrorsAfterTheDefaultWait() {
        submitter.offer(TestSupport.v4(), "a.example");
        submitter.flush("", reports::add);
        assertEquals(1, submitter.pendingCount());
        assertEquals(RetryPolicy.DEFAULT_RETRY_SECONDS, submitter.waitSeconds());
    }

    @Test
    void dropsARejectedBatch() {
        submitter.offer(TestSupport.v4(), "a.example");
        transport.respond(400, "application/problem+json", null, "{\"type\":\"https://uuid.quest/problems/validation\"}");
        submitter.flush("", reports::add);
        assertEquals(0, submitter.pendingCount());
        assertEquals(0, submitter.waitSeconds());
        assertEquals("uuid.quest rejected the submission (400)", reports.get(0).plain());
    }

    @Test
    void dropsUuidsFromServersBlockedSinceTheyWereSeen() {
        submitter.offer(TestSupport.v4(), "npc.example");
        UUID kept = TestSupport.v4();
        submitter.offer(kept, "a.example");
        guard.block(List.of("npc.example"));

        transport.accepted("s1", 1);
        submitter.flush("", reports::add);
        assertEquals(1, body(0).getAsJsonArray("uuids").size());
        assertEquals(kept.toString(), body(0).getAsJsonArray("uuids").get(0).getAsString());
        assertEquals(List.of("a.example"), history.recent(1).get(0).servers());
    }

    @Test
    void doesNotStartASecondRequestWhileOneIsRunning() {
        var hanging = new Transport() {
            @Override
            public java.util.concurrent.CompletableFuture<Response> sendAsync(Request request) {
                return new java.util.concurrent.CompletableFuture<>();
            }

            @Override
            public Response send(Request request) {
                throw new UnsupportedOperationException();
            }
        };
        var busy = new Submitter(hanging, history, guard, new TestSupport.RecordingLog(), clock, "ua");
        busy.offer(TestSupport.v4(), "a.example");
        assertEquals(SENT, busy.flush("", reports::add));
        busy.offer(TestSupport.v4(), "a.example");
        assertEquals(IN_FLIGHT, busy.flush("", reports::add));
    }

    @Test
    void shutdownFlushSendsEverythingButRespectsTheWait() {
        for (int i = 0; i < 3; i++) submitter.offer(TestSupport.v4(), "a.example");
        transport.accepted("s1", 3);
        submitter.flushBlocking("", Duration.ofSeconds(5));
        assertEquals(0, submitter.pendingCount());
        assertEquals(1, history.size());

        submitter.offer(TestSupport.v4(), "a.example");
        transport.respond(503, null, "60", "");
        submitter.flushBlocking("", Duration.ofSeconds(5));
        assertEquals(1, submitter.pendingCount());
        submitter.flushBlocking("", Duration.ofSeconds(5));
        assertEquals(2, transport.requests.size());
    }

    @Test
    void pollsUnfinishedSubmissionsUntilDone() {
        submitter.offer(TestSupport.v4(), "a.example");
        transport.accepted("s1", 1);
        submitter.flush("", reports::add);

        var finished = new ArrayList<Submission>();
        transport.respond(200, "application/json", null,
            "{\"state\":\"processing\",\"counts\":{\"received\":1,\"checked\":0}}");
        submitter.pollUnfinished(finished::add);
        assertEquals("https://uuid.quest/v1/submissions/s1", transport.requests.get(1).uri().toString());
        assertTrue(finished.isEmpty());

        clock.advanceSeconds(29);
        submitter.pollUnfinished(finished::add);
        assertEquals(2, transport.requests.size());

        clock.advanceSeconds(1);
        transport.respond(200, "application/json", null,
            "{\"state\":\"done\",\"counts\":{\"received\":1,\"forwarded\":1}}");
        submitter.pollUnfinished(finished::add);
        assertEquals(3, transport.requests.size());
        assertEquals(1, finished.size());
        assertEquals(1, history.total("forwarded"));
        assertTrue(history.unfinished().isEmpty());
    }

    @Test
    void marksPrunedSubmissionsExpiredAndBacksOffOnRateLimits() {
        submitter.offer(TestSupport.v4(), "a.example");
        transport.accepted("s1", 1);
        submitter.flush("", reports::add);
        clock.advanceSeconds(30);

        transport.respond(429, "application/problem+json", "300", "{}");
        submitter.pollUnfinished(s -> {
        });
        clock.advanceSeconds(60);
        var before = new AtomicLong(transport.requests.size());
        submitter.pollUnfinished(s -> {
        });
        assertEquals(before.get(), transport.requests.size());

        clock.advanceSeconds(300);
        transport.respond(404, "application/problem+json", null, "{}");
        submitter.pollUnfinished(s -> {
        });
        assertEquals(Submission.STATE_EXPIRED, history.recent(1).get(0).state());
        assertTrue(history.unfinished().isEmpty());
    }

    @Test
    void apiKeyIsSentWithSubmissionsOnly() {
        submitter.setApiKey(() -> "  uqk_secret  ");
        submitter.offer(TestSupport.v4(), "a.example");
        transport.accepted("s1", 1);
        submitter.flush("", reports::add);
        assertEquals("uqk_secret", transport.requests.get(0).bearer());

        transport.respond(200, "application/json", null,
            "{\"submitId\":\"s1\",\"state\":\"queued\",\"counts\":{\"received\":1,\"queued\":1}}");
        submitter.pollUnfinished(submission -> { });
        assertEquals(2, transport.requests.size());
        assertEquals(null, transport.requests.get(1).bearer());
    }

    @Test
    void blankApiKeySubmitsAnonymously() {
        submitter.setApiKey(() -> " ");
        submitter.offer(TestSupport.v4(), "a.example");
        transport.accepted("s1", 1);
        submitter.flush("", reports::add);
        assertEquals(null, transport.requests.get(0).bearer());
    }

    @Test
    void apiKeyIsMaskedInListings() {
        assertEquals("not set", Settings.maskApiKey(""));
        assertEquals("set (...cdef)", Settings.maskApiKey("uqk_0123456789abcdef"));
        assertEquals("set", Settings.maskApiKey("uqk_ab"));
    }

}
