package quest.uuid.addon

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.utils.client.mc

/**
 * Runs for as long as the add-on is loaded: collects UUIDs while in a world, submits and polls in
 * the background, and shows announcements once the player can see them.
 */
object UuidQuestTracker : EventListener {

    internal var addon: EventListener? = null

    override fun parent() = addon

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val settings = UuidQuestSettings
        if (settings.enabled) {
            collect()
            if (UuidQuestSubmitter.isDue(settings.interval)) {
                UuidQuestSubmitter.flush(settings.note, ::verboseReport)
            }
        }
        UuidQuestSubmitter.pollUnfinished(::onFinished)
        Announcer.deliver()
        waitTicks(20)
    }

    private fun collect() {
        if (UuidQuestSettings.tabList) {
            mc.connection?.listedOnlinePlayers?.forEach { info ->
                val profile = info.profile
                if (isPremium(profile.id, profile.name)) UuidQuestSubmitter.offer(profile.id)
            }
        }
        if (UuidQuestSettings.worldPlayers) {
            mc.level?.players()?.forEach { player ->
                if (isPremium(player.uuid, player.gameProfile.name)) UuidQuestSubmitter.offer(player.uuid)
            }
        }
    }

    private fun onFinished(submission: Submission) {
        val found = submission.count("forwarded")
        if (found > 0) {
            val message = "Found $found new ${if (found == 1) "UUID" else "UUIDs"}"
            Announcer.announce(message, UuidQuestSettings.announce.targets)
        }
        verboseReport(UuidQuestSubmitter.summary(submission))
    }

    fun verboseReport(message: String) {
        if (UuidQuestSettings.verbose) Announcer.announce(message, setOf(AnnounceTarget.CHAT))
    }

}
