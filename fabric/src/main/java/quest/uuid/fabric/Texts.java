package quest.uuid.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import quest.uuid.core.QuestText;

/** Turns core's text into chat components: plain parts in gray, submission ids as links. */
final class Texts {

    private Texts() {
    }

    static MutableComponent toComponent(QuestText text) {
        MutableComponent component = Component.empty();
        for (QuestText.Part part : text.parts()) {
            if (part instanceof QuestText.Plain plain) {
                component.append(gray(plain.text()));
            } else if (part instanceof QuestText.SubmissionLink link) {
                component.append(Component.literal(link.shortId()).withStyle(style -> Compat.link(
                    style.withColor(ChatFormatting.AQUA).withUnderlined(true), link.url(), Component.literal(link.hover()))));
            }
        }
        return component;
    }

    static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

}
