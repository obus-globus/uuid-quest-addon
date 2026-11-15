package quest.uuid.addon

import com.mojang.brigadier.CommandDispatcher
import net.ccbluex.liquidbounce.features.command.CommandRegistrar
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource
import net.ccbluex.liquidbounce.features.command.brigadier.register
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `.uuidquest` shows totals, `.uuidquest submissions` the recent submissions and their results,
 * `.uuidquest flush` sends the pending UUIDs now.
 */
object CommandUuidQuest : CommandRegistrar {

    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register("uuidquest") {
            exec {
                chat(
                    "Seen ${UuidQuestSubmitter.rememberedCount} this session, " +
                        "pending ${UuidQuestSubmitter.pendingCount}. " +
                        "Submitted ${SubmissionHistory.total("received")}, " +
                        "new to uuid.quest ${SubmissionHistory.total("forwarded")}, " +
                        "already known ${SubmissionHistory.total("alreadyKnown")}"
                )
                1
            }
            literal("submissions") {
                exec {
                    val recent = SubmissionHistory.recent(10)
                    if (recent.isEmpty()) chat("No submissions yet")
                    recent.forEach { submission ->
                        val line = buildString {
                            append(time.format(Instant.ofEpochMilli(submission.createdAt)))
                            append(" ${submission.sent} sent, ${submission.state}")
                            if (submission.finished) append(" | ${UuidQuestSubmitter.summary(submission)}")
                            submission.note?.let { append(" | $it") }
                        }
                        chat(line)
                    }
                    1
                }
            }
            literal("flush") {
                exec {
                    UuidQuestSubmitter.flush(ModuleUuidQuest.note) { message -> mc.execute { chat(message) } }
                    1
                }
            }
        }
    }

}
