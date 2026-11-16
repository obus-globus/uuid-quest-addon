package quest.uuid.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidQuestTest {

    @TempDir
    Path dir;

    private final TestSupport.Clock clock = new TestSupport.Clock();
    private final FakeTransport transport = new FakeTransport();
    private final FakePlatform platform = new FakePlatform();

    private static final class FakePlatform implements Platform {
        boolean inWorld = true;
        String server = "a.example";
        UUID own = TestSupport.v4();
        final Map<UUID, String> listed = new LinkedHashMap<>();
        final Map<UUID, String> world = new LinkedHashMap<>();
        final List<String> chat = new ArrayList<>();
        final List<String> notifications = new ArrayList<>();

        @Override
        public boolean inWorld() {
            return inWorld;
        }

        @Override
        public void showChat(QuestText text) {
            chat.add(text.plain());
        }

        @Override
        public void showNotification(QuestText text, Severity severity) {
            notifications.add(severity + " " + text.plain());
        }

        @Override
        public String serverAddress() {
            return server;
        }

        @Override
        public UUID ownUuid() {
            return own;
        }

        @Override
        public void forEachListedPlayer(BiConsumer<UUID, String> sink) {
            listed.forEach(sink);
        }

        @Override
        public void forEachWorldPlayer(BiConsumer<UUID, String> sink) {
            world.forEach(sink);
        }
    }

    private StoredSettings settings() {
        return StoredSettings.load(dir.resolve("uuid-quest.json"), new TestSupport.RecordingLog());
    }

    private UuidQuest quest(StoredSettings settings) {
        var log = new TestSupport.RecordingLog();
        return new UuidQuest(platform, settings, log, new SubmissionHistory(dir.resolve("submissions.json"), log),
            transport, clock, "uuid-quest/test");
    }

    @Test
    void collectsPremiumPlayersButNotTheOwnAccount() {
        var quest = quest(settings());
        platform.listed.put(TestSupport.v4(), "Alice");
        platform.listed.put(TestSupport.v3("Bob"), "Bob");
        platform.listed.put(platform.own, "Me");
        platform.world.put(TestSupport.v4(), "Carol");
        quest.update();
        assertEquals(2, quest.submitter().pendingCount());
    }

    @Test
    void followsTheCollectionSettings() {
        var settings = settings();
        settings.setTabList(false);
        settings.setSubmitOwnUuid(true);
        var quest = quest(settings);
        platform.listed.put(TestSupport.v4(), "Alice");
        platform.world.put(platform.own, "Me");
        quest.update();
        assertEquals(1, quest.submitter().pendingCount());

        settings.setEnabled(false);
        platform.world.put(TestSupport.v4(), "Carol");
        quest.update();
        assertEquals(1, quest.submitter().pendingCount());
    }

    @Test
    void submitsOnceTheIntervalHasPassed() {
        var quest = quest(settings());
        platform.listed.put(TestSupport.v4(), "Alice");
        quest.update();
        clock.advanceSeconds(59);
        quest.update();
        assertEquals(0, transport.requests.size());
        clock.advanceSeconds(1);
        transport.accepted("s1", 1);
        quest.update();
        // The POST, then the first status poll of the new submission.
        assertEquals(2, transport.requests.size());
        assertEquals("https://uuid.quest/v1/uuids", transport.requests.get(0).uri().toString());
    }

    @Test
    void announcesNewUuidsOnceInAWorld() {
        var settings = settings();
        settings.setAnnounce(AnnounceMode.BOTH);
        var quest = quest(settings);
        platform.listed.put(TestSupport.v4(), "Alice");
        quest.update();
        transport.accepted("abcdef0123456789", 1);
        quest.onDisabled();

        platform.inWorld = false;
        clock.advanceSeconds(30);
        transport.respond(200, "application/json", null,
            "{\"state\":\"done\",\"counts\":{\"received\":1,\"checked\":1,\"real\":1,\"forwarded\":1}}");
        quest.update();
        assertTrue(platform.chat.isEmpty());

        platform.inWorld = true;
        quest.update();
        assertEquals(List.of("Found 1 new UUID (abcdef01)"), platform.chat);
        assertEquals(List.of("SUCCESS Found 1 new UUID (abcdef01)"), platform.notifications);
    }

    @Test
    void stopsCollectingOnAServerWithMostlyFakeUuids() {
        var settings = settings();
        settings.setAnnounce(AnnounceMode.OFF);
        var quest = quest(settings);
        for (int i = 0; i < 20; i++) platform.listed.put(TestSupport.v4(), "Npc" + i);
        quest.update();
        transport.accepted("s1", 20);
        quest.onDisabled();

        clock.advanceSeconds(30);
        transport.respond(200, "application/json", null,
            "{\"state\":\"done\",\"counts\":{\"received\":20,\"checked\":20,\"fake\":12}}");
        quest.update();
        assertEquals(List.of("Most UUIDs from a.example were fake (NPCs?), not collecting there until restart"),
            platform.chat);
        assertEquals(1, platform.notifications.size());
        assertTrue(platform.notifications.get(0).startsWith("ERROR "));

        platform.listed.put(TestSupport.v4(), "Alice");
        quest.update();
        assertEquals(0, quest.submitter().pendingCount());

        platform.server = "b.example";
        quest.update();
        assertEquals(1, quest.submitter().pendingCount());
    }

    @Test
    void verboseShowsEveryOutcome() {
        var settings = settings();
        settings.setVerbose(true);
        var quest = quest(settings);
        platform.listed.put(TestSupport.v4(), "Alice");
        quest.update();
        transport.respond(400, "application/problem+json", null, "{}");
        quest.onDisabled();
        quest.update();
        assertEquals(List.of("uuid.quest rejected the submission (400)"), platform.chat);
    }

    @Test
    void shutdownSendsOnlyWhileEnabled() {
        var settings = settings();
        var quest = quest(settings);
        platform.listed.put(TestSupport.v4(), "Alice");
        quest.update();
        settings.setEnabled(false);
        quest.onStopping();
        assertEquals(0, transport.requests.size());

        settings.setEnabled(true);
        transport.accepted("s1", 1);
        quest.onStopping();
        assertEquals(1, transport.requests.size());
    }

    @Test
    void commandTexts() {
        var quest = quest(settings());
        assertEquals("Nothing to send", quest.flushNow(text -> {
        }));
        platform.listed.put(TestSupport.v4(), "Alice");
        quest.update();
        transport.accepted("s1", 1);
        assertEquals("Sending the pending UUIDs (0 left)", quest.flushNow(text -> {
        }));
        assertEquals("Seen 1 this session, pending 0. Submitted 1, new to uuid.quest 0, already known 0", quest.totals());
        assertEquals(1, quest.recentSubmissions().size());
        assertTrue(quest.recentSubmissions().get(0).plain().endsWith(" s1 1 sent, queued"));
    }

}
