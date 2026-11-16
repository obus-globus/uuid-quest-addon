package quest.uuid.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** Settings kept in a JSON file, for platforms without their own config system. */
public final class StoredSettings implements Settings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The persisted fields; Gson keeps the defaults for anything missing from the file. */
    static final class Values {
        boolean enabled = true;
        int interval = DEFAULT_INTERVAL;
        boolean tabList = true;
        boolean worldPlayers = true;
        boolean submitOwnUuid = false;
        String note = "";
        AnnounceMode announce = AnnounceMode.OFF;
        boolean verbose = false;
        boolean updateCheck = true;
        String apiKey = "";

        Values copy() {
            return GSON.fromJson(GSON.toJson(this), Values.class);
        }

        void normalize() {
            interval = clampInterval(interval);
            note = note == null ? "" : cleanNote(note);
            if (announce == null) announce = AnnounceMode.OFF;
            apiKey = apiKey == null ? "" : apiKey.trim();
        }
    }

    private final Path file;
    private final QuestLog log;
    private volatile Values values = new Values();

    private StoredSettings(Path file, QuestLog log) {
        this.file = file;
        this.log = log;
    }

    /** Reads {@code file}; a file that cannot be parsed is moved to {@code <name>.bak} and the defaults are used. */
    public static StoredSettings load(Path file, QuestLog log) {
        var settings = new StoredSettings(file, log);
        if (Files.isRegularFile(file)) {
            try {
                Values loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Values.class);
                if (loaded != null) {
                    loaded.normalize();
                    settings.values = loaded;
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                try {
                    Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveError) {
                    e.addSuppressed(moveError);
                }
                log.warn("Could not read " + file + ", moved it to " + backup, e);
            }
        }
        return settings;
    }

    /** Applies {@code change} to a copy and stores it, so readers never see a half-applied edit. */
    private synchronized void update(Consumer<Values> change) {
        Values next = values.copy();
        change.accept(next);
        next.normalize();
        values = next;
        save();
    }

    /** Takes every value from {@code from}, saving once. */
    public void apply(Settings from) {
        update(v -> {
            v.enabled = from.enabled();
            v.interval = from.interval();
            v.tabList = from.tabList();
            v.worldPlayers = from.worldPlayers();
            v.submitOwnUuid = from.submitOwnUuid();
            v.note = from.note();
            v.announce = from.announce();
            v.verbose = from.verbose();
            v.updateCheck = from.updateCheck();
            v.apiKey = from.apiKey();
        });
    }

    public void setEnabled(boolean value) {
        update(v -> v.enabled = value);
    }

    public void setInterval(int value) {
        update(v -> v.interval = value);
    }

    public void setTabList(boolean value) {
        update(v -> v.tabList = value);
    }

    public void setWorldPlayers(boolean value) {
        update(v -> v.worldPlayers = value);
    }

    public void setSubmitOwnUuid(boolean value) {
        update(v -> v.submitOwnUuid = value);
    }

    public void setNote(String value) {
        update(v -> v.note = value);
    }

    public void setAnnounce(AnnounceMode value) {
        update(v -> v.announce = value);
    }

    public void setVerbose(boolean value) {
        update(v -> v.verbose = value);
    }

    public void setUpdateCheck(boolean value) {
        update(v -> v.updateCheck = value);
    }

    public void setApiKey(String value) {
        update(v -> v.apiKey = value);
    }

    /** Writes a temporary file and moves it over the old one, so a crash mid-write keeps the previous file. */
    public synchronized void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(values), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Could not write " + file, e);
        }
    }

    public static int clampInterval(int seconds) {
        return Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, seconds));
    }

    public static String cleanNote(String note) {
        String trimmed = note.trim();
        return trimmed.length() > MAX_NOTE_LENGTH ? trimmed.substring(0, MAX_NOTE_LENGTH) : trimmed;
    }

    @Override
    public boolean enabled() {
        return values.enabled;
    }

    @Override
    public int interval() {
        return values.interval;
    }

    @Override
    public boolean tabList() {
        return values.tabList;
    }

    @Override
    public boolean worldPlayers() {
        return values.worldPlayers;
    }

    @Override
    public boolean submitOwnUuid() {
        return values.submitOwnUuid;
    }

    @Override
    public String note() {
        return values.note;
    }

    @Override
    public AnnounceMode announce() {
        return values.announce;
    }

    @Override
    public boolean verbose() {
        return values.verbose;
    }

    @Override
    public boolean updateCheck() {
        return values.updateCheck;
    }

    @Override
    public String apiKey() {
        return values.apiKey;
    }

}
