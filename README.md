# uuid.quest add-on for LiquidBounce

Submits the premium player UUIDs the client sees to [uuid.quest](https://uuid.quest/), which checks
them against Mojang and adds new ones to its dataset.

- Collects UUIDs from the tab list and from players in the world.
- Only sends version 4 UUIDs with a valid Minecraft name. Offline-mode (version 3) and most NPC
  UUIDs are skipped.
- Sends in batches (default every 60 seconds), when `Enabled` is turned off, and when the game
  closes (unless `Enabled` is off). Honours the service's `Retry-After` on rate limits.
- Stops collecting on a server for the rest of the session once uuid.quest finds most of its UUIDs
  fake (e.g. NPCs with random UUIDs), well before uuid.quest would penalise the network, and warns in
  chat and as a notification. It also warns if uuid.quest does throttle the network for abuse.
- Keeps the last 100 submissions with their uuid.quest result in `LiquidBounce/uuid-quest/submissions.json`
  and keeps polling unfinished ones, also after a restart. An optional `Note` is sent with each one.
- Announces UUIDs that turned out new to uuid.quest in chat and/or as a LiquidBounce notification
  (`Announce`: Off, Chat, Notification, Both), held back until you are in a world. `Verbose` adds every
  submission's outcome to chat.

## Use

1. `./gradlew build` (JDK 25), then put `build/libs/uuid-quest-addon-<version>.jar` into `mods/`
   next to LiquidBounce.
2. It runs on its own as soon as LiquidBounce starts; there is no module to enable.
3. `.uuidquest` shows totals, `.uuidquest submissions` the recent submissions and their results,
   `.uuidquest settings` the current settings, and `.uuidquest flush` sends the pending UUIDs now.

## Settings

Stored in `LiquidBounce/uuidquest.json`. Change them with LiquidBounce's `.value` command, e.g.

```
.value set uuidQuest.settings.note "my label"
.value set uuidQuest.settings.announce Both
.value set uuidQuest.settings.enabled off
```

`Enabled`, `Interval` (30 to 600 s), `TabList`, `WorldPlayers`, `SubmitOwnUuid` (off: your own account is skipped),
`Note`, `Announce`, `Verbose`.
