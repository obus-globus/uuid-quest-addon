package quest.uuid.addon

import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon
import java.time.Duration

class UuidQuestAddon : LiquidBounceAddon() {

    override fun onInitialize() {
        registerModules(ModuleUuidQuest)
        registerCommand(CommandUuidQuest)
    }

    override fun onStopping() {
        UuidQuestSubmitter.flushBlocking(ModuleUuidQuest.note, Duration.ofSeconds(5))
    }

}
