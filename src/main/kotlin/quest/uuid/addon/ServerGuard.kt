package quest.uuid.addon

import java.util.concurrent.ConcurrentHashMap

/**
 * Stops collecting on servers whose UUIDs uuid.quest found to be mostly fake (NPCs with random v4
 * UUIDs), for the rest of the game session.
 *
 * uuid.quest early-exits a submission and penalises the IP once at least 50 of its unknown UUIDs were
 * checked and 80% of them were fake. This trips earlier, so one NPC-heavy lobby cannot get the
 * network penalised.
 */
object ServerGuard {

    private const val MIN_CHECKED = 10
    private const val MAX_FAKE_RATIO = 0.5

    private val blocked = ConcurrentHashMap.newKeySet<String>()

    fun isBlocked(server: String) = server in blocked

    fun isSuspicious(submission: Submission): Boolean {
        val checked = submission.count("checked")
        return submission.earlyExited ||
            (checked >= MIN_CHECKED && submission.count("fake") >= checked * MAX_FAKE_RATIO)
    }

    /** Blocks [servers] and returns the ones that were not blocked before. */
    fun block(servers: Collection<String>): List<String> = servers.filter { blocked.add(it) }

}
