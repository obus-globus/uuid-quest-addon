package quest.uuid.addon

import java.util.UUID

private val USERNAME = Regex("[A-Za-z0-9_]{1,16}")

/**
 * Mojang issues random (version 4, IETF variant) UUIDs to premium accounts. Offline-mode servers
 * derive version 3 UUIDs from the name, and most NPC plugins use other versions or names no real
 * account can have, so both are left out.
 */
fun isPremium(uuid: UUID, name: String?): Boolean =
    uuid.version() == 4 && uuid.variant() == 2 && name != null && USERNAME.matches(name)
