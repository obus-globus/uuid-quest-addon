package quest.uuid.addon

import net.ccbluex.liquidbounce.utils.client.variable
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import java.net.URI

/** The short submission id, opening `https://uuid.quest/?id=<submitId>` when clicked. */
fun submissionLink(submitId: String): MutableComponent = variable(submitId.take(8)).withStyle { style ->
    style.withUnderlined(true)
        .withClickEvent(ClickEvent.OpenUrl(URI("https://uuid.quest/?id=$submitId")))
        .withHoverEvent(HoverEvent.ShowText(Component.literal("Open $submitId on uuid.quest")))
}

fun summaryCounts(submission: Submission) = buildString {
    append("${submission.count("forwarded")} new, ${submission.count("alreadyKnown")} known, ")
    append("${submission.count("fake")} fake")
    if (submission.earlyExited) append(", stopped early")
}

/** For the log: `uuid.quest <submitId>: 3 new, 39 known, 0 fake`. */
fun summaryLine(submission: Submission) = "uuid.quest ${submission.submitId}: ${summaryCounts(submission)}"

/** For chat, with a clickable id. */
fun summaryText(submission: Submission): Component = Component.empty()
    .append(gray("uuid.quest "))
    .append(submissionLink(submission.submitId))
    .append(gray(": ${summaryCounts(submission)}"))

fun gray(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.GRAY)
