package quest.uuid.addon

import net.ccbluex.liquidbounce.utils.client.variable
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import quest.uuid.core.QuestText
import java.net.URI

/** Plain parts in gray, submission ids as LiquidBounce variables that open their status page when clicked. */
fun QuestText.toComponent(): MutableComponent {
    val component = Component.empty()
    for (part in parts()) {
        when (part) {
            is QuestText.Plain -> component.append(gray(part.text()))
            is QuestText.SubmissionLink -> component.append(submissionLink(part))
        }
    }
    return component
}

private fun submissionLink(link: QuestText.SubmissionLink): MutableComponent =
    variable(link.shortId()).withStyle { style ->
        style.withUnderlined(true)
            .withClickEvent(ClickEvent.OpenUrl(URI(link.url())))
            .withHoverEvent(HoverEvent.ShowText(Component.literal(link.hover())))
    }

private fun gray(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.GRAY)
