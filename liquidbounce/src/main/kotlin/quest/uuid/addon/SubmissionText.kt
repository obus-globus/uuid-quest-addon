package quest.uuid.addon

import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.variable
import net.ccbluex.liquidbounce.utils.text.RunnableClickEvent
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
            is QuestText.Link -> component.append(link(part))
            is QuestText.Command -> component.append(command(part))
        }
    }
    return component
}

private fun link(link: QuestText.Link): MutableComponent =
    variable(link.label()).withStyle { style ->
        style.withUnderlined(true)
            .withClickEvent(ClickEvent.OpenUrl(URI(link.url())))
            .withHoverEvent(HoverEvent.ShowText(Component.literal(link.hover())))
    }

private fun gray(text: String): MutableComponent = Component.literal(text).withStyle(ChatFormatting.GRAY)

/** Runs `.uuidquest <args>` when clicked, through LiquidBounce's runnable click event. */
private fun command(command: QuestText.Command): MutableComponent =
    variable(command.label()).withStyle { style ->
        style.withUnderlined(true)
            .withClickEvent(RunnableClickEvent {
                mc.execute {
                    runCatching { CommandManager.execute("uuidquest " + command.args()) }
                        .onFailure { AddonLog.warn("Running .uuidquest ${command.args()} failed", it) }
                }
            })
            .withHoverEvent(HoverEvent.ShowText(Component.literal(command.hover())))
    }
