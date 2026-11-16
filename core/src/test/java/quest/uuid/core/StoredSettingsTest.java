package quest.uuid.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredSettingsTest {

    @TempDir
    Path dir;

    @Test
    void defaultsWithoutAFile() {
        var settings = StoredSettings.load(dir.resolve("uuid-quest.json"), new TestSupport.RecordingLog());
        assertTrue(settings.enabled());
        assertEquals(60, settings.interval());
        assertTrue(settings.tabList());
        assertTrue(settings.worldPlayers());
        assertFalse(settings.submitOwnUuid());
        assertEquals("", settings.note());
        assertEquals(AnnounceMode.NOTIFICATION, settings.announce());
        assertFalse(settings.verbose());
    }

    @Test
    void persistsChangesAndClampsThem() {
        Path file = dir.resolve("uuid-quest.json");
        var settings = StoredSettings.load(file, new TestSupport.RecordingLog());
        settings.setInterval(5);
        settings.setNote("  " + "x".repeat(250) + "  ");
        settings.setAnnounce(AnnounceMode.BOTH);
        settings.setEnabled(false);
        assertEquals(30, settings.interval());

        var loaded = StoredSettings.load(file, new TestSupport.RecordingLog());
        assertFalse(loaded.enabled());
        assertEquals(30, loaded.interval());
        assertEquals(200, loaded.note().length());
        assertEquals(AnnounceMode.BOTH, loaded.announce());
        loaded.setInterval(1000);
        assertEquals(600, loaded.interval());
    }

    @Test
    void storesAnnounceByTagAndKeepsDefaultsForMissingFields() throws Exception {
        Path file = dir.resolve("uuid-quest.json");
        Files.writeString(file, "{\"announce\":\"Chat\",\"interval\":90}");
        var settings = StoredSettings.load(file, new TestSupport.RecordingLog());
        assertEquals(AnnounceMode.CHAT, settings.announce());
        assertEquals(90, settings.interval());
        assertTrue(settings.enabled());
        settings.setVerbose(true);
        assertTrue(Files.readString(file).contains("\"announce\": \"Chat\""));
    }

    @Test
    void movesACorruptFileAside() throws Exception {
        Path file = dir.resolve("uuid-quest.json");
        Files.writeString(file, "[broken");
        var settings = StoredSettings.load(file, new TestSupport.RecordingLog());
        assertTrue(settings.enabled());
        assertTrue(Files.exists(dir.resolve("uuid-quest.json.bak")));
    }

    @Test
    void appliesAnotherSettingsObjectInOneGo() {
        Path file = dir.resolve("uuid-quest.json");
        var settings = StoredSettings.load(file, new TestSupport.RecordingLog());
        var other = StoredSettings.load(dir.resolve("other.json"), new TestSupport.RecordingLog());
        other.setVerbose(true);
        other.setInterval(7);
        other.setNote(" label ");
        settings.apply(other);
        var loaded = StoredSettings.load(file, new TestSupport.RecordingLog());
        assertTrue(loaded.verbose());
        assertEquals(30, loaded.interval());
        assertEquals("label", loaded.note());
    }

    @Test
    void parsesAnnounceTagsLoosely() {
        assertEquals(AnnounceMode.BOTH, AnnounceMode.byTag("both"));
        assertEquals(AnnounceMode.OFF, AnnounceMode.byTag("Off"));
        assertEquals(null, AnnounceMode.byTag("loud"));
    }

}
