package quest.uuid.core;

import java.util.ArrayList;
import java.util.List;

/** A chat line made of plain text and clickable submission ids; each platform turns it into its own text type. */
public record QuestText(List<Part> parts) {

    public QuestText {
        parts = List.copyOf(parts);
    }

    public sealed interface Part permits Plain, SubmissionLink {
    }

    public record Plain(String text) implements Part {
    }

    /** Shows the first 8 characters of the id and opens its status page when clicked. */
    public record SubmissionLink(String submitId) implements Part {

        public String shortId() {
            return submitId.length() > 8 ? submitId.substring(0, 8) : submitId;
        }

        public String url() {
            return "https://uuid.quest/?id=" + submitId;
        }

        public String hover() {
            return "Open " + submitId + " on uuid.quest";
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
            } else if (part instanceof SubmissionLink link) {
                out.append(link.shortId());
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
            parts.add(new SubmissionLink(submitId));
            return this;
        }

        public QuestText build() {
            return new QuestText(parts);
        }
    }

}
