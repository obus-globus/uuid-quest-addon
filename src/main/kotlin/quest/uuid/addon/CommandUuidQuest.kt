package quest.uuid.addon

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.ccbluex.liquidbounce.features.command.CommandRegistrar
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `.uuidquest` shows totals, `.uuidquest submissions` the recent submissions and their results,
 * `.uuidquest settings` the current settings, `.uuidquest flush` sends the pending UUIDs now.
 *
 * Plain Brigadier builders, as LiquidBounce's command DSL is not part of its add-on API.
 */
object CommandUuidQuest : CommandRegistrar {

    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private fun literal(name: String) = LiteralArgumentBuilder.literal<ClientCommandSource>(name)

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register(
            literal("uuidquest")
                .executes { showTotals(); 1 }
                .then(literal("submissions").executes { showSubmissions(); 1 })
                .then(literal("settings").executes { showSettings(); 1 })
                .then(literal("flush").executes { flush(); 1 })
        )
    }

    private fun showTotals() {
        chat(
            "Seen ${UuidQuestSubmitter.rememberedCount} this session, " +
                "pending ${UuidQuestSubmitter.pendingCount}. " +
                "Submitted ${SubmissionHistory.total("received")}, " +
                "new to uuid.quest ${SubmissionHistory.total("forwarded")}, " +
                "already known ${SubmissionHistory.total("alreadyKnown")}"
        )
    }

    private fun showSubmissions() {
        val recent = SubmissionHistory.recent(10)
        if (recent.isEmpty()) chat("No submissions yet")
        recent.forEach { submission ->
            val details = buildString {
                append(" ${submission.sent} sent, ${submission.state}")
                if (submission.finished) append(", ${summaryCounts(submission)}")
                submission.note?.let { append(" | $it") }
            }
            chat(
                gray(time.format(Instant.ofEpochMilli(submission.createdAt)) + " ")
                    .append(submissionLink(submission.submitId))
                    .append(gray(details))
            )
        }
    }

    private fun showSettings() {
        UuidQuestSettings.collectValuesRecursively().forEach { value -> chat("${value.name}: ${value.get()}") }
        chat("Change one with .value set uuidQuest.settings.<name> <value>")
    }

    private fun flush() {
        val result = UuidQuestSubmitter.flush(UuidQuestSettings.note) { message ->
            mc.execute { chat(message) }
        }
        when (result) {
            UuidQuestSubmitter.FlushResult.SENT -> chat("${result.reason} (${UuidQuestSubmitter.pendingCount} left)")
            UuidQuestSubmitter.FlushResult.WAITING -> chat("${result.reason}, ${UuidQuestSubmitter.waitSeconds()}s left")
            else -> chat(result.reason)
        }
    }

}
