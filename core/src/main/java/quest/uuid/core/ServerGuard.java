package quest.uuid.core;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops collecting on servers whose UUIDs uuid.quest found to be mostly fake (NPCs with random v4
 * UUIDs), for the rest of the game session.
 *
 * <p>uuid.quest early-exits a submission and penalises the IP once at least 50 of its unknown UUIDs were
 * checked and 80% of them were fake. This trips earlier, so one NPC-heavy lobby cannot get the
 * network penalised.
 */
public final class ServerGuard {

    static final int MIN_CHECKED = 10;
    static final double MAX_FAKE_RATIO = 0.5;

    private final Set<String> blocked = ConcurrentHashMap.newKeySet();

    public boolean isBlocked(String server) {
        return blocked.contains(server);
    }

    public static boolean isSuspicious(Submission submission) {
        int checked = submission.count("checked");
        return submission.earlyExited()
            || (checked >= MIN_CHECKED && submission.count("fake") >= checked * MAX_FAKE_RATIO);
    }

    /** Blocks {@code servers} and returns the ones that were not blocked before. */
    public List<String> block(Collection<String> servers) {
        return servers.stream().filter(blocked::add).toList();
    }

}
