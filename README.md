<img src="fabric/src/main/resources/assets/uuid-quest/icon.png" alt="" width="96" align="right">

# uuid.quest for Minecraft

Sends the premium player UUIDs your client sees to [uuid.quest](https://uuid.quest/), which checks
them against Mojang and adds the new ones to its dataset. There are two variants that behave the same:
a LiquidBounce add-on for Minecraft 26.3 (with LiquidBounce 0.42.0), and a standalone Fabric mod for
every Minecraft release from 1.20.1 to 26.3. Install only one of them, since both use the mod id
`uuid-quest`.

## What it does

- Collects UUIDs from the tab list and from players in the world.
- Sends only version 4 UUIDs that belong to a valid Minecraft name. That skips offline-mode players
  (version 3 UUIDs) and most NPCs. Your own account is skipped too unless `SubmitOwnUuid` is on.
- Sends in batches, by default every 60 seconds, plus once when you turn `Enabled` off and once when
  the game closes (unless `Enabled` is off). On a rate limit it waits as long as the service's
  `Retry-After` says.
- If uuid.quest finds most UUIDs from a server fake (usually NPCs with random UUIDs), it stops
  collecting on that server until you restart the game and warns you in chat and with a notification.
  It backs off well before uuid.quest would penalise your network, and it also warns you if uuid.quest
  does throttle the network for abuse.
- Keeps the last 100 submissions with their result and keeps checking unfinished ones, including after
  a restart. You can attach a `Note` to each submission.
- Tells you when a submission contained UUIDs uuid.quest didn't have yet, in chat, as a notification,
  or both (`Announce`). In chat the submission id links to its status page. Messages wait until you
  are in a world. With `Verbose` on, chat also shows the outcome of every submission.
- Tells you in chat and with a notification when a newer release is on GitHub, with a link to the
  jar for your Minecraft version. It asks GitHub at most once every 6 hours, even if the game keeps
  restarting, and you can turn it off with `UpdateCheck`.

## Settings

- `Enabled`: collect and send UUIDs.
- `Interval`: seconds between batches, 30 to 600.
- `TabList`, `WorldPlayers`: where to collect UUIDs from.
- `SubmitOwnUuid`: also send your own account (off by default).
- `Note`: a label sent with each submission.
- `Announce`: Off, Chat, Notification or Both.
- `Verbose`: show every submission's outcome in chat.
- `UpdateCheck`: look for a newer release on GitHub (on by default).
- `ApiKey`: optional uuid.quest API key (`uqk_...`, created on your uuid.quest account page). With a key,
  your submissions count for your account on the leaderboard and use your account's rate limits instead
  of the per-IP ones. After the first submission with a key you get a chat line naming the account it
  is credited to, or a warning if uuid.quest did not accept the key (the submission then goes in
  anonymously). Settings listings only show the last four characters.

## LiquidBounce add-on

Put `uuid-quest-addon-<version>+26.3.jar` into `mods/` next to LiquidBounce 0.42.0 for Minecraft 26.3.
It starts with LiquidBounce; there is no module to enable.

`.uuidquest` shows totals, `.uuidquest submissions` lists recent submissions with their results,
`.uuidquest settings` prints the current settings, and `.uuidquest flush` sends the pending UUIDs now.

Settings live in `LiquidBounce/uuidquest.json`. Change them with LiquidBounce's `.value` command:

```
.value set uuidQuest.settings.note "my label"
.value set uuidQuest.settings.announce Both
.value set uuidQuest.settings.enabled off
.value set uuidQuest.settings.apiKey uqk_...
```

Submissions are kept in `LiquidBounce/uuid-quest/submissions.json`, and notifications show up as
LiquidBounce notifications.

## Fabric mod

You need [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
[Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds a config button. Put the jar for your
Minecraft version into `mods/`:

```
Minecraft        jar
1.20.1 - 1.20.2  uuid-quest-fabric-<version>+1.20.1-1.20.2.jar
1.20.3 - 1.21.4  uuid-quest-fabric-<version>+1.20.3-1.21.4.jar
1.21.5 - 1.21.8  uuid-quest-fabric-<version>+1.21.5-1.21.8.jar
1.21.9 - 1.21.11 uuid-quest-fabric-<version>+1.21.9-1.21.11.jar
26.1 - 26.1.2    uuid-quest-fabric-<version>+26.1-26.1.2.jar
26.2 - 26.3      uuid-quest-fabric-<version>+26.2-26.3.jar
```

`/uuidquest` shows totals, `/uuidquest submissions` lists recent submissions, `/uuidquest settings`
prints the current settings and `/uuidquest flush` sends the pending UUIDs now.

`/uuidquest config` or Mod Menu's config button opens the settings screen. To change one setting from
chat, use `/uuidquest set <name> <value>`, for example `/uuidquest set note my label`,
`/uuidquest set announce Both`, `/uuidquest set interval 120` or `/uuidquest set enabled false`.
Set the API key with `/uuidquest set apikey uqk_...`, and clear it with `/uuidquest set apikey`.

Settings live in `config/uuid-quest.json` and submissions in `config/uuid-quest/submissions.json`.
Notifications show up as vanilla toasts.

## Building

Run `./gradlew build collectJars` with JDK 25. Gradle finds JDK 17 and 21 for the older Fabric versions
in the usual install locations, and every jar ends up in `build/libs/`.
