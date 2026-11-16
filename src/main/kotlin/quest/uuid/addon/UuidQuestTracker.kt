package quest.uuid.addon

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.NotificationEvent
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
        UuidQuestSubmitter.onAbuseThrottled = { seconds ->
            warn("uuid.quest is blocking this network for ${seconds / 60} min after too many fake UUIDs")
        }
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
        val server = mc.currentServer?.ip ?: "singleplayer"
        if (ServerGuard.isBlocked(server)) return
        val own = if (UuidQuestSettings.submitOwnUuid) null else mc.user.profileId
        fun offer(uuid: UUID, name: String?) {
            if (uuid != own && isPremium(uuid, name)) UuidQuestSubmitter.offer(uuid, server)
        }

        if (UuidQuestSettings.tabList) {
            mc.connection?.listedOnlinePlayers?.forEach { info -> offer(info.profile.id, info.profile.name) }
        }
        if (UuidQuestSettings.worldPlayers) {
            mc.level?.players()?.forEach { player -> offer(player.uuid, player.gameProfile.name) }
        }
    }

    private fun onFinished(submission: Submission) {
        if (ServerGuard.isSuspicious(submission)) {
            ServerGuard.block(submission.servers.orEmpty()).forEach { server ->
                warn("Most UUIDs from $server were fake (NPCs?), not collecting there until restart")
            }
        }

        val found = submission.count("forwarded")
        if (found > 0) {
            val message = gray("Found $found new ${if (found == 1) "UUID" else "UUIDs"} (")
                .append(submissionLink(submission.submitId))
                .append(gray(")"))
            Announcer.announce(message, UuidQuestSettings.announce.targets)
        }
        verboseReport(summaryText(submission))
    }

    /** Always shown in chat and as a notification, whatever `Announce` is set to. */
    private fun warn(message: String) {
        log.warn(message)
        Announcer.announce(
            gray(message),
            setOf(AnnounceTarget.CHAT, AnnounceTarget.NOTIFICATION),
            NotificationEvent.Severity.ERROR,
        )
    }

    fun verboseReport(message: Component) {
        if (UuidQuestSettings.verbose) Announcer.announce(message, setOf(AnnounceTarget.CHAT))
    }

}
