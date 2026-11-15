# uuid.quest add-on for LiquidBounce

Submits the premium player UUIDs the client sees to [uuid.quest](https://uuid.quest/), which checks
them against Mojang and adds new ones to its dataset.

- Collects UUIDs from the tab list and from players in the world.
- Only sends version 4 UUIDs with a valid Minecraft name. Offline-mode (version 3) and most NPC
  UUIDs are skipped.
- Sends in batches (default every 60 seconds), when the module is turned off, and when the game
  closes. Honours the service's `Retry-After` on rate limits.

## Use

1. `./gradlew build` (JDK 25), then put `build/libs/uuid-quest-addon-<version>.jar` into `mods/`
   next to LiquidBounce.
2. Enable the `UuidQuest` module (Misc).
3. `.uuidquest` shows how many UUIDs were seen, are pending and were accepted; `.uuidquest flush`
   sends the pending ones now.
