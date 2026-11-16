package quest.uuid.addon

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.notification
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentLinkedQueue

enum class AnnounceTarget(override val tag: String) : Tagged {
    CHAT("Chat"),
    NOTIFICATION("Notification"),
}

/** A single choice rather than a set, since `.value set` cannot parse multi-choice values. */
enum class AnnounceMode(override val tag: String, val targets: Set<AnnounceTarget>) : Tagged {
    OFF("Off", emptySet()),
    CHAT("Chat", setOf(AnnounceTarget.CHAT)),
    NOTIFICATION("Notification", setOf(AnnounceTarget.NOTIFICATION)),
    BOTH("Both", setOf(AnnounceTarget.CHAT, AnnounceTarget.NOTIFICATION)),
}

/**
 * Queues messages from any thread and shows them once the player is in a world, since chat and
 * notifications are not visible from the menus.
 */
object Announcer {

    private class Announcement(
        val message: Component,
        val targets: Set<AnnounceTarget>,
        val severity: NotificationEvent.Severity,
    )

    private val queue = ConcurrentLinkedQueue<Announcement>()

    /** Notifications get the plain text of [message]; links only work in chat. */
    fun announce(
        message: Component,
        targets: Set<AnnounceTarget>,
        severity: NotificationEvent.Severity = NotificationEvent.Severity.SUCCESS,
    ) {
        if (targets.isNotEmpty()) queue.add(Announcement(message, targets.toSet(), severity))
    }

    /** Render thread only. */
    fun deliver() {
        if (mc.player == null) return
        while (true) {
            val announcement = queue.poll() ?: break
            if (AnnounceTarget.CHAT in announcement.targets) {
                chat(announcement.message)
            }
            if (AnnounceTarget.NOTIFICATION in announcement.targets) {
                notification("uuid.quest", announcement.message.string, announcement.severity)
            }
        }
    }

}
