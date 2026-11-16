package quest.uuid.addon

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * Runs for as long as the add-on is loaded: collects UUIDs while in a world, submits and polls in
 * the background, and shows announcements once the player can see them.
 */
object UuidQuestTracker : EventListener {

    internal var addon: EventListener? = null

    override fun parent() = addon

    init {
        every(20, ::tick)
    }

    private fun tick() {
        val settings = UuidQuestSettings
        if (settings.enabled) {
            collect()
            if (UuidQuestSubmitter.isDue(settings.interval)) {
                UuidQuestSubmitter.flush(settings.note, ::verboseReport)
            }
        }
        UuidQuestSubmitter.pollUnfinished(::onFinished)
        Announcer.deliver()
    }

    private fun collect() {
        // uuid.quest keeps hashed IP prefixes per submission, so the own account is opt-in.
        val own = if (UuidQuestSettings.submitOwnUuid) null else mc.user.profileId
        fun offer(uuid: UUID, name: String?) {
            if (uuid != own && isPremium(uuid, name)) UuidQuestSubmitter.offer(uuid)
        }

        if (UuidQuestSettings.tabList) {
            mc.connection?.listedOnlinePlayers?.forEach { info -> offer(info.profile.id, info.profile.name) }
        }
        if (UuidQuestSettings.worldPlayers) {
            mc.level?.players()?.forEach { player -> offer(player.uuid, player.gameProfile.name) }
        }
    }

    private fun onFinished(submission: Submission) {
        val found = submission.count("forwarded")
        if (found > 0) {
            val message = gray("Found $found new ${if (found == 1) "UUID" else "UUIDs"} (")
                .append(submissionLink(submission.submitId))
                .append(gray(")"))
            Announcer.announce(message, UuidQuestSettings.announce.targets)
        }
        verboseReport(summaryText(submission))
    }

    fun verboseReport(message: Component) {
        if (UuidQuestSettings.verbose) Announcer.announce(message, setOf(AnnounceTarget.CHAT))
    }

}
