package quest.uuid.addon

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories

object ModuleUuidQuest : ClientModule("UuidQuest", ModuleCategories.MISC) {

    private val interval by int("Interval", 60, 30..600, "s")
    private val tabList by boolean("TabList", true)
    private val worldPlayers by boolean("WorldPlayers", true)
    val note by text("Note", "")
    private val announce by multiEnumChoice("Announce", AnnounceTarget.NOTIFICATION)
    private val verbose by boolean("Verbose", false)

    @Suppress("unused")
    private val tickHandler = tickHandler {
        collect()
        if (UuidQuestSubmitter.isDue(interval)) {
            UuidQuestSubmitter.flush(note, ::verboseReport)
        }
        UuidQuestSubmitter.pollUnfinished(::onFinished)
        Announcer.deliver()
        waitTicks(20)
    }

    override fun onDisabled() {
        UuidQuestSubmitter.flush(note, ::verboseReport)
    }

    private fun collect() {
        if (tabList) {
            mc.connection?.listedOnlinePlayers?.forEach { info ->
                val profile = info.profile
                if (isPremium(profile.id, profile.name)) UuidQuestSubmitter.offer(profile.id)
            }
        }
        if (worldPlayers) {
            mc.level?.players()?.forEach { player ->
                if (isPremium(player.uuid, player.gameProfile.name)) UuidQuestSubmitter.offer(player.uuid)
            }
        }
    }

    private fun onFinished(submission: Submission) {
        val found = submission.count("forwarded")
        if (found > 0) {
            Announcer.announce("Found $found new ${if (found == 1) "UUID" else "UUIDs"}", announce)
        }
        verboseReport(UuidQuestSubmitter.summary(submission))
    }

    private fun verboseReport(message: String) {
        if (verbose) Announcer.announce(message, setOf(AnnounceTarget.CHAT))
    }

}
