# uuid.quest for Minecraft

Submits the premium player UUIDs the client sees to [uuid.quest](https://uuid.quest/), which checks
them against Mojang and adds new ones to its dataset. It comes in two variants with the same behaviour:

- a **LiquidBounce add-on** (Minecraft 26.3, alongside LiquidBounce 0.42.0), and
- a **standalone Fabric mod** for every Minecraft release from 1.20.1 to 26.3.

Install only one of them; both use the mod id `uuid-quest`.

## What it does

- Collects UUIDs from the tab list and from players in the world.
- Only sends version 4 UUIDs with a valid Minecraft name. Offline-mode (version 3) and most NPC
  UUIDs are skipped, and so is your own account unless `SubmitOwnUuid` is on.
- Sends in batches (default every 60 seconds), when `Enabled` is turned off, and when the game
  closes (unless `Enabled` is off). Honours the service's `Retry-After` on rate limits.
- Stops collecting on a server for the rest of the session once uuid.quest finds most of its UUIDs
  fake (e.g. NPCs with random UUIDs), well before uuid.quest would penalise the network, and warns in
  chat and as a notification. It also warns if uuid.quest does throttle the network for abuse.
- Keeps the last 100 submissions with their uuid.quest result and keeps polling unfinished ones, also
  after a restart. An optional `Note` is sent with each one.
- Announces UUIDs that turned out new to uuid.quest in chat (the submission id links to its status
  page) and/or as a notification (`Announce`: Off, Chat, Notification, Both), held back until you are
  in a world. `Verbose` adds every submission's outcome to chat.

## Settings

`Enabled`, `Interval` (30 to 600 s), `TabList`, `WorldPlayers`, `SubmitOwnUuid` (off: your own account
is skipped), `Note`, `Announce`, `Verbose`.

## LiquidBounce add-on

Put `uuid-quest-addon-<version>+26.3.jar` into `mods/` next to LiquidBounce 0.42.0 for Minecraft
26.3. It runs on its own as soon as LiquidBounce starts; there is no module to enable.

- `.uuidquest` shows totals, `.uuidquest submissions` the recent submissions and their results,
  `.uuidquest settings` the current settings, and `.uuidquest flush` sends the pending UUIDs now.
- Settings are stored in `LiquidBounce/uuidquest.json`. Change them with LiquidBounce's `.value`
  command, e.g.

  ```
  .value set uuidQuest.settings.note "my label"
  .value set uuidQuest.settings.announce Both
  .value set uuidQuest.settings.enabled off
  ```
- Submissions are kept in `LiquidBounce/uuid-quest/submissions.json`. Notifications are
  LiquidBounce notifications.

## Fabric mod

Needs [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
[Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds a config button. Pick the jar for
your Minecraft version and put it into `mods/`:

```
Minecraft        jar
1.20.1 - 1.20.2  uuid-quest-fabric-<version>+1.20.1-1.20.2.jar
1.20.3 - 1.21.4  uuid-quest-fabric-<version>+1.20.3-1.21.4.jar
1.21.5 - 1.21.8  uuid-quest-fabric-<version>+1.21.5-1.21.8.jar
1.21.9 - 1.21.11 uuid-quest-fabric-<version>+1.21.9-1.21.11.jar
26.1 - 26.1.2    uuid-quest-fabric-<version>+26.1-26.1.2.jar
26.2 - 26.3      uuid-quest-fabric-<version>+26.2-26.3.jar
```

- `/uuidquest` shows totals, `/uuidquest submissions` the recent submissions, `/uuidquest settings`
  the current settings and `/uuidquest flush` sends the pending UUIDs now.
- `/uuidquest config` (or Mod Menu's config button) opens the settings screen. `/uuidquest set <name>
  <value>` changes one setting from chat, e.g. `/uuidquest set note my label`,
  `/uuidquest set announce Both`, `/uuidquest set interval 120`, `/uuidquest set enabled false`.
- Settings are stored in `config/uuid-quest.json`, submissions in `config/uuid-quest/submissions.json`.
  Notifications are vanilla toasts.

## Building

`./gradlew build collectJars` with JDK 25 (Gradle finds JDK 17 and 21 for the older Fabric versions in
the usual install locations) puts every jar into `build/libs/`.
