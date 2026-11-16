package quest.uuid.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @TempDir
    Path dir;

    private final FakeTransport transport = new FakeTransport();
    private final TestSupport.Clock clock = new TestSupport.Clock();
    private final TestSupport.RecordingLog log = new TestSupport.RecordingLog();
    private final List<UpdateChecker.Update> updates = new ArrayList<>();

    private static final String RELEASE = """
        {"tag_name":"v1.1.0+26.3","html_url":"https://github.com/obus-globus/uuid-quest-addon/releases/tag/v1.1.0+26.3",
         "assets":[
          {"name":"uuid-quest-addon-1.1.0+26.3.jar","browser_download_url":"https://example.test/addon.jar"},
          {"name":"uuid-quest-fabric-1.1.0+1.20.1-1.20.2.jar","browser_download_url":"https://example.test/fabric-1.20.1.jar"},
          {"name":"uuid-quest-fabric-1.1.0+26.2-26.3.jar","browser_download_url":"https://example.test/fabric-26.2.jar"}]}
        """;

    private UpdateChecker checker(String version, String prefix) {
        return new UpdateChecker(transport, dir.resolve("update-check.json"), log, clock, "uuid-quest/test", version, prefix);
    }

    @Test
    void newerReleaseLinksTheJarForTheRunningRange() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.0.0+1.20.1-1.20.2", "uuid-quest-fabric-").checkOnce(updates::add);
        assertEquals(List.of(new UpdateChecker.Update("1.1.0", "https://example.test/fabric-1.20.1.jar")), updates);
    }

    @Test
    void addonPicksItsOwnJar() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        assertEquals("https://example.test/addon.jar", updates.get(0).url());
    }

    @Test
    void missingJarFallsBackToTheReleasePage() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.0.0+1.21.9-1.21.11", "uuid-quest-fabric-").checkOnce(updates::add);
        assertTrue(updates.get(0).url().endsWith("/releases/tag/v1.1.0+26.3"));
    }

    @Test
    void sameOrOlderReleaseIsNoUpdate() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.1.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.2.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        assertTrue(updates.isEmpty());
    }

    @Test
    void restartsWithinTheCacheTimeDoNotAskGitHubAgain() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        clock.advanceSeconds(60);
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        assertEquals(1, transport.requests.size());
        assertEquals(2, updates.size());
    }

    @Test
    void staleCacheAsksAgain() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        clock.advanceSeconds(UpdateChecker.CACHE_FOR.toSeconds() + 1);
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        assertEquals(2, transport.requests.size());
    }

    @Test
    void failedCheckIsCachedButKeepsTheLastKnownRelease() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        clock.advanceSeconds(UpdateChecker.CACHE_FOR.toSeconds() + 1);
        transport.respond(503, "text/html", null, "<html>");
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        clock.advanceSeconds(60);
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        assertEquals(2, transport.requests.size());
        assertEquals(3, updates.size());
    }

    @Test
    void noReleaseYetIsNoUpdate() {
        transport.respond(404, "application/json", null, "{\"message\":\"Not Found\"}");
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        assertTrue(updates.isEmpty());
    }

    @Test
    void checksOnlyOncePerSession() {
        transport.respond(200, "application/json", null, RELEASE);
        UpdateChecker checker = checker("1.0.0+26.3", "uuid-quest-addon-");
        checker.checkOnce(updates::add);
        checker.checkOnce(updates::add);
        assertEquals(1, updates.size());
    }

    @Test
    void unknownCurrentVersionNeverReportsAnUpdate() {
        transport.respond(200, "application/json", null, RELEASE);
        checker("unknown", "uuid-quest-fabric-").checkOnce(updates::add);
        assertTrue(updates.isEmpty());
    }

    @Test
    void versionsCompareNumerically() {
        assertTrue(UpdateChecker.compare(UpdateChecker.numbers("1.10.0"), UpdateChecker.numbers("1.9.3")) > 0);
        assertEquals(0, UpdateChecker.compare(UpdateChecker.numbers("1.0"), UpdateChecker.numbers("1.0.0")));
        assertEquals("1.2.0", UpdateChecker.modVersion("v1.2.0+1.20.1-1.20.2"));
    }

    @Test
    void userAgentNamesVariantAndPlayedVersion() {
        assertEquals("uuid-quest/1.0.0 (fabric; minecraft 1.21.4)", UuidQuest.userAgent("1.0.0+1.20.3-1.21.4", "fabric", "1.21.4"));
        assertEquals("uuid-quest/1.0.0 (liquidbounce; minecraft 26.3)", UuidQuest.userAgent("1.0.0+26.3", "liquidbounce", "26.3"));
    }

    @Test
    void damagedCachedAssetsAreSkipped() throws Exception {
        java.nio.file.Files.writeString(dir.resolve("update-check.json"),
            "{\"checkedAt\":" + clock.now + ",\"tag\":\"v1.1.0+26.3\",\"releaseUrl\":\"https://example.test/r\","
                + "\"assets\":[{\"url\":\"https://example.test/x.jar\"},null]}");
        checker("1.0.0+26.3", "uuid-quest-addon-").checkOnce(updates::add);
        assertEquals(List.of(new UpdateChecker.Update("1.1.0", "https://example.test/r")), updates);
    }

}
