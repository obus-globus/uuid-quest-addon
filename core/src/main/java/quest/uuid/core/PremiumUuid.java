package quest.uuid.core;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mojang issues random (version 4, IETF variant) UUIDs to premium accounts. Offline-mode servers
 * derive version 3 UUIDs from the name, and most NPC plugins use other versions or names no real
 * account can have, so both are left out.
 */
public final class PremiumUuid {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private PremiumUuid() {
    }

    public static boolean isPremium(UUID uuid, String name) {
        return uuid != null && uuid.version() == 4 && uuid.variant() == 2
            && name != null && USERNAME.matcher(name).matches();
    }

}
