package quest.uuid.addon

import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon
import net.fabricmc.loader.api.FabricLoader
import quest.uuid.core.UuidQuest

class UuidQuestAddon : LiquidBounceAddon() {

    override fun onInitialize() {
        val dataDir = FabricLoader.getInstance().gameDir.resolve("LiquidBounce").resolve("uuid-quest")
        val minecraft = FabricLoader.getInstance().getModContainer("minecraft")
            .map { it.metadata.version.friendlyString }.orElse("unknown")
        val userAgent = UuidQuest.userAgent(version, "liquidbounce", minecraft)
        val quest = UuidQuest(LiquidBouncePlatform, UuidQuestSettings.View, AddonLog, dataDir.resolve("submissions.json"), userAgent)
        quest.enableUpdateCheck(dataDir.resolve("update-check.json"), version, "uuid-quest-addon-")
        Companion.quest = quest
        config("UuidQuest", mutableListOf(UuidQuestSettings))
        UuidQuestTracker.addon = this
        registerListeners(UuidQuestTracker)
        registerCommand(CommandUuidQuest(quest))
    }

    override fun onStarted() {
        started = true
    }

    override fun onStopping() {
        quest?.onStopping()
    }

    companion object {
        /** Null until the add-on is initialized; settings callbacks can fire before that. */
        @Volatile
        var quest: UuidQuest? = null
            private set

        /** True once LiquidBounce has loaded the configs. */
        @Volatile
        var started = false
            private set
    }

}
