package quest.uuid.core;

import java.util.ArrayList;
import java.util.List;

/** A chat line made of plain text and clickable submission ids; each platform turns it into its own text type. */
public record QuestText(List<Part> parts) {

    public QuestText {
        parts = List.copyOf(parts);
    }

    public sealed interface Part permits Plain, Link {
    }

    public record Plain(String text) implements Part {
    }

    /** Shows {@code label} and opens {@code url} when clicked; notifications and logs show only the label. */
    public record Link(String label, String url, String hover) implements Part {

        /** The first 8 characters of the id, opening its status page. */
        public static Link submission(String submitId) {
            String shortId = submitId.length() > 8 ? submitId.substring(0, 8) : submitId;
            return new Link(shortId, "https://uuid.quest/?id=" + submitId, "Open " + submitId + " on uuid.quest");
        }
    }

    public static QuestText of(String text) {
        return new QuestText(List.of(new Plain(text)));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The text with links reduced to their short id, for notifications and logs. */
    public String plain() {
        var out = new StringBuilder();
        for (Part part : parts) {
            if (part instanceof Plain plain) {
                out.append(plain.text());
            } else if (part instanceof Link link) {
                out.append(link.label());
            }
        }
        return out.toString();
    }

    public static final class Builder {

        private final List<Part> parts = new ArrayList<>();

        public Builder text(String text) {
            parts.add(new Plain(text));
            return this;
        }

        public Builder link(String submitId) {
            parts.add(Link.submission(submitId));
            return this;
        }

        public Builder link(String label, String url, String hover) {
            parts.add(new Link(label, url, hover));
            return this;
        }

        public QuestText build() {
            return new QuestText(parts);
        }
    }

}
