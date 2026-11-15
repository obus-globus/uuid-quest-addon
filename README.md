# uuid.quest add-on for LiquidBounce

Submits the premium player UUIDs the client sees to [uuid.quest](https://uuid.quest/), which checks
them against Mojang and adds new ones to its dataset.

- Collects UUIDs from the tab list and from players in the world.
- Only sends version 4 UUIDs with a valid Minecraft name. Offline-mode (version 3) and most NPC
  UUIDs are skipped.
- Sends in batches (default every 60 seconds), when the module is turned off, and when the game
  closes. Honours the service's `Retry-After` on rate limits.
- Keeps the last 100 submissions with their uuid.quest result in `LiquidBounce/uuid-quest/submissions.json`
  and keeps polling unfinished ones, also after a restart. An optional `Note` is sent with each one.
- Announces UUIDs that turned out new to uuid.quest in chat and/or as a LiquidBounce notification
  (`Announce`), held back until you are in a world. `Verbose` adds every submission's outcome to chat.

## Use

1. `./gradlew build` (JDK 25), then put `build/libs/uuid-quest-addon-<version>.jar` into `mods/`
   next to LiquidBounce.
2. Enable the `UuidQuest` module (Misc).
3. `.uuidquest` shows totals, `.uuidquest submissions` the recent submissions and their results,
   and `.uuidquest flush` sends the pending UUIDs now.
