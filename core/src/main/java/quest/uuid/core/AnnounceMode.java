package quest.uuid.core;

import com.google.gson.annotations.SerializedName;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** A single choice rather than a set, since LiquidBounce's `.value set` cannot parse multi-choice values. */
public enum AnnounceMode {
    @SerializedName("Off") OFF("Off", EnumSet.noneOf(AnnounceTarget.class)),
    @SerializedName("Chat") CHAT("Chat", EnumSet.of(AnnounceTarget.CHAT)),
    @SerializedName("Notification") NOTIFICATION("Notification", EnumSet.of(AnnounceTarget.NOTIFICATION)),
    @SerializedName("Both") BOTH("Both", EnumSet.of(AnnounceTarget.CHAT, AnnounceTarget.NOTIFICATION));

    private final String tag;
    private final EnumSet<AnnounceTarget> targets;

    AnnounceMode(String tag, EnumSet<AnnounceTarget> targets) {
        this.tag = tag;
        this.targets = targets;
    }

    public String tag() {
        return tag;
    }

    public Set<AnnounceTarget> targets() {
        return EnumSet.copyOf(targets);
    }

    public AnnounceMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Matches the tag case-insensitively, null if none does. */
    public static AnnounceMode byTag(String tag) {
        for (AnnounceMode mode : values()) {
            if (mode.tag.toLowerCase(Locale.ROOT).equals(tag.toLowerCase(Locale.ROOT))) return mode;
        }
        return null;
    }
}
