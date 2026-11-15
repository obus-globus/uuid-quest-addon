package quest.uuid.addon

import com.mojang.brigadier.CommandDispatcher
import net.ccbluex.liquidbounce.features.command.CommandRegistrar
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource
import net.ccbluex.liquidbounce.features.command.brigadier.register
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc

/**
 * `.uuidquest` shows what has been collected and sent; `.uuidquest flush` sends the pending UUIDs now.
 */
object CommandUuidQuest : CommandRegistrar {

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register("uuidquest") {
            exec {
                chat(
                    "Seen ${UuidQuestSubmitter.rememberedCount}, pending ${UuidQuestSubmitter.pendingCount}, " +
                        "accepted ${UuidQuestSubmitter.acceptedTotal.get()}" +
                        (UuidQuestSubmitter.lastSubmitId?.let { ", last submission $it" } ?: "")
                )
                1
            }
            literal("flush") {
                exec {
                    UuidQuestSubmitter.flush { message -> mc.execute { chat(message) } }
                    1
                }
            }
        }
    }

}
