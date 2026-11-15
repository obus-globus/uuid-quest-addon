package quest.uuid.addon

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.notification
import java.util.concurrent.ConcurrentLinkedQueue

enum class AnnounceTarget(override val tag: String) : Tagged {
    CHAT("Chat"),
    NOTIFICATION("Notification"),
}

/**
 * Queues messages from any thread and shows them once the player is in a world, since chat and
 * notifications are not visible from the menus.
 */
object Announcer {

    private class Announcement(val message: String, val targets: Set<AnnounceTarget>)

    private val queue = ConcurrentLinkedQueue<Announcement>()

    fun announce(message: String, targets: Set<AnnounceTarget>) {
        if (targets.isNotEmpty()) queue.add(Announcement(message, targets.toSet()))
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
                notification("uuid.quest", announcement.message, NotificationEvent.Severity.SUCCESS)
            }
        }
    }

}
