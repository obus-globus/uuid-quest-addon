package quest.uuid.addon

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.ccbluex.liquidbounce.features.command.CommandRegistrar
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import quest.uuid.core.Settings
import quest.uuid.core.UuidQuest

/**
 * `.uuidquest` shows totals, `.uuidquest submissions` the recent submissions and their results,
 * `.uuidquest settings` the current settings, `.uuidquest flush` sends the pending UUIDs now.
 *
 * Plain Brigadier builders, as LiquidBounce's command DSL is not part of its add-on API.
 */
class CommandUuidQuest(private val quest: UuidQuest) : CommandRegistrar {

    private fun literal(name: String) = LiteralArgumentBuilder.literal<ClientCommandSource>(name)

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register(
            literal("uuidquest")
                .executes { chat(quest.totals()); 1 }
                .then(literal("submissions").executes { showSubmissions(); 1 })
                .then(literal("settings").executes { showSettings(); 1 })
                .then(literal("flush").executes { flush(); 1 })
        )
    }

    private fun showSubmissions() {
        val recent = quest.recentSubmissions()
        if (recent.isEmpty()) chat("No submissions yet")
        recent.forEach { line -> chat(line.toComponent()) }
    }

    private fun showSettings() {
        UuidQuestSettings.collectValuesRecursively().forEach { value ->
            val shown = if (value.name == "ApiKey") Settings.maskApiKey(value.get() as String) else value.get()
            chat("${value.name}: $shown")
        }
        chat("Change one with .value set uuidQuest.settings.<name> <value>")
    }

    private fun flush() {
        chat(quest.flushNow { message -> mc.execute { chat(message.toComponent()) } })
    }

}
