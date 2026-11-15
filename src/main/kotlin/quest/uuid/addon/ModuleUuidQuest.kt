package quest.uuid.addon

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.chat

object ModuleUuidQuest : ClientModule("UuidQuest", ModuleCategories.MISC) {

    private val interval by int("Interval", 60, 30..600, "s")
    private val tabList by boolean("TabList", true)
    private val worldPlayers by boolean("WorldPlayers", true)
    private val notify by boolean("Notify", false)

    @Suppress("unused")
    private val tickHandler = tickHandler {
        collect()
        if (UuidQuestSubmitter.isDue(interval)) {
            UuidQuestSubmitter.flush(::report)
        }
        waitTicks(20)
    }

    override fun onDisabled() {
        UuidQuestSubmitter.flush(::report)
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

    private fun report(message: String) {
        if (notify) mc.execute { chat(message) }
    }

}
