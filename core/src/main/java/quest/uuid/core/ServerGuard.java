package quest.uuid.core;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Stops collecting on servers whose UUIDs uuid.quest found to be mostly fake (NPCs with random v4
 * UUIDs), for a short cooldown.
 *
 * <p>uuid.quest early-exits a submission once at least 50 of its unknown UUIDs were checked and 80% of
 * them were fake, and blocks the IP from the third such submission on. This trips earlier, so one
 * NPC-heavy lobby cannot get the network blocked.
 */
public final class ServerGuard {

    static final int MIN_CHECKED = 10;
    static final double MAX_FAKE_RATIO = 0.5;
    /** How long a server stays paused after a mostly fake submission. */
    public static final Duration COOLDOWN = Duration.ofMinutes(10);

    private final LongSupplier clock;
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    public ServerGuard(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String server) {
        Long until = blockedUntil.get(server);
        if (until == null) return false;
        if (clock.getAsLong() < until) return true;
        blockedUntil.remove(server, until);
        return false;
    }

    public static boolean isSuspicious(Submission submission) {
        int checked = submission.count("checked");
        return submission.earlyExited()
            || (checked >= MIN_CHECKED && submission.count("fake") >= checked * MAX_FAKE_RATIO);
    }

    /** Pauses {@code servers} for {@link #COOLDOWN} and returns the ones that were not paused already. */
    public List<String> block(Collection<String> servers) {
        long until = clock.getAsLong() + COOLDOWN.toMillis();
        return servers.stream().filter(server -> {
            if (isBlocked(server)) return false;
            blockedUntil.put(server, until);
            return true;
        }).toList();
    }

}
