package quest.uuid.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Compares the running version with the latest GitHub release. The answer is cached in a file for
 * {@link #CACHE_FOR}, so a client that crashes and restarts in a loop asks GitHub at most once in that time.
 */
public final class UpdateChecker {

    static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/obus-globus/uuid-quest-addon/releases/latest");
    static final Duration CACHE_FOR = Duration.ofHours(6);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Gson GSON = new Gson();

    /** {@code url} is the jar for the running Minecraft range if the release has one, else the release page. */
    public record Update(String version, String url) {
    }

    /** The last answer; the release fields are null when the repository had no release. */
    static final class Cache {
        long checkedAt;
        String tag;
        String releaseUrl;
        List<Asset> assets = new ArrayList<>();
    }

    static final class Asset {
        String name;
        String url;
    }

    private final Transport transport;
    private final Path cacheFile;
    private final QuestLog log;
    private final LongSupplier clock;
    private final String userAgent;
    private final String currentVersion;
    private final String assetPrefix;
    private final AtomicBoolean checked = new AtomicBoolean(false);

    /**
     * @param currentVersion the running version, {@code <mod version>+<minecraft range>}
     * @param assetPrefix    start of this variant's jar names, e.g. {@code uuid-quest-fabric-}
     */
    UpdateChecker(Transport transport, Path cacheFile, QuestLog log, LongSupplier clock, String userAgent,
                  String currentVersion, String assetPrefix) {
        this.transport = transport;
        this.cacheFile = cacheFile;
        this.log = log;
        this.clock = clock;
        this.userAgent = userAgent;
        this.currentVersion = currentVersion;
        this.assetPrefix = assetPrefix;
    }

    /** Checks once per session; {@code onUpdate} runs, possibly on another thread, if a newer release exists. */
    public void checkOnce(Consumer<Update> onUpdate) {
        if (!checked.compareAndSet(false, true)) return;

        Cache cache = readCache();
        long age = cache == null ? -1 : clock.getAsLong() - cache.checkedAt;
        if (age >= 0 && age < CACHE_FOR.toMillis()) {
            evaluate(cache).ifPresent(onUpdate);
            return;
        }

        transport.sendAsync(new Transport.Request(LATEST_RELEASE, null, TIMEOUT, userAgent)).whenComplete((response, error) -> {
            try {
                // A failed check keeps the last known release and still counts, so failures are not retried at once.
                Cache next = cache == null ? new Cache() : cache;
                next.checkedAt = clock.getAsLong();
                if (response != null && response.status() == 200) {
                    readRelease(JsonParser.parseString(response.body()).getAsJsonObject(), next);
                } else if (response != null && response.status() == 404) {
                    next.tag = null;
                    next.releaseUrl = null;
                    next.assets = new ArrayList<>();
                } else {
                    log.warn("Update check failed (" + (response == null ? "no response" : response.status()) + ")", error);
                }
                writeCache(next);
                evaluate(next).ifPresent(onUpdate);
            } catch (RuntimeException e) {
                log.warn("Update check failed", e);
            }
        });
    }

    private static void readRelease(JsonObject release, Cache into) {
        into.tag = release.get("tag_name").getAsString();
        into.releaseUrl = release.get("html_url").getAsString();
        into.assets = new ArrayList<>();
        JsonElement assets = release.get("assets");
        if (assets != null && assets.isJsonArray()) {
            for (JsonElement element : assets.getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                var asset = new Asset();
                asset.name = object.get("name").getAsString();
                asset.url = object.get("browser_download_url").getAsString();
                into.assets.add(asset);
            }
        }
    }

    Optional<Update> evaluate(Cache cache) {
        if (cache.tag == null || cache.releaseUrl == null) return Optional.empty();
        String latest = modVersion(cache.tag);
        int[] latestParts = numbers(latest);
        int[] currentParts = numbers(modVersion(currentVersion));
        if (latestParts == null || currentParts == null || compare(latestParts, currentParts) <= 0) return Optional.empty();

        String url = cache.releaseUrl;
        int plus = currentVersion.indexOf('+');
        if (plus >= 0 && cache.assets != null) {
            String suffix = "+" + currentVersion.substring(plus + 1) + ".jar";
            for (Asset asset : cache.assets) {
                if (asset.name.startsWith(assetPrefix) && asset.name.endsWith(suffix)) {
                    url = asset.url;
                    break;
                }
            }
        }
        return Optional.of(new Update(latest, url));
    }

    /** {@code v1.2.0+26.3} and {@code 1.2.0+1.20.1-1.20.2} both give {@code 1.2.0}. */
    static String modVersion(String version) {
        String v = version.startsWith("v") ? version.substring(1) : version;
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    /** The dot-separated numbers, null if any part is not a number (e.g. a pre-release or "unknown"). */
    static int[] numbers(String version) {
        String[] parts = version.split("\\.");
        int[] numbers = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) numbers[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return numbers;
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private Cache readCache() {
        if (!Files.isRegularFile(cacheFile)) return null;
        try {
            return GSON.fromJson(Files.readString(cacheFile, StandardCharsets.UTF_8), Cache.class);
        } catch (IOException | JsonParseException e) {
            log.warn("Could not read " + cacheFile + ", checking again", e);
            return null;
        }
    }

    private void writeCache(Cache cache) {
        try {
            Files.createDirectories(cacheFile.toAbsolutePath().getParent());
            Path temp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(cache), StandardCharsets.UTF_8);
            Files.move(temp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Could not write " + cacheFile, e);
        }
    }

}
