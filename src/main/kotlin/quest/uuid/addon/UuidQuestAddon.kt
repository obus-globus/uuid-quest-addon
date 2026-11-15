package quest.uuid.addon

import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon
import java.time.Duration

class UuidQuestAddon : LiquidBounceAddon() {

    override fun onInitialize() {
        UuidQuestSubmitter.userAgent = "$id/$version"
        config("UuidQuest", mutableListOf(UuidQuestSettings))
        UuidQuestTracker.addon = this
        registerListeners(UuidQuestTracker)
        registerCommand(CommandUuidQuest)
    }

    override fun onStopping() {
        UuidQuestSubmitter.flushBlocking(UuidQuestSettings.note, Duration.ofSeconds(5))
    }

}
