# lb-addon-uuid-quest

LiquidBounce add-on that collects the premium (v4) player UUIDs the client sees and submits them to
uuid.quest, our public UUID submit API in front of mcid (`../../mcid/uuid-quest/` on our machine). Built
from [`../lb-addon-workspace`](../lb-addon-workspace) (CCBlueX's add-on template); its CLAUDE.md
covers the add-on build setup and traps. User-facing behaviour is in [README.md](README.md).

## Code

- `UuidQuestSubmitter` holds the session's seen set and the pending queue, posts to
  `POST https://uuid.quest/v1/uuids` and polls `GET /v1/submissions/{id}`; `flushBlocking` runs from
  `UuidQuestAddon.onStopping`. `SubmissionHistory` persists submissions to
  `LiquidBounce/uuid-quest/submissions.json` (a 404 on poll is stored as the non-API state `expired`).
- Not a module on purpose: `UuidQuestTracker` is a plain `EventListener` (parent: the add-on) whose tick
  handler also runs in menus, and `UuidQuestSettings` is an add-on `config(...)` edited through
  `.value set uuidQuest.settings.<name>`. That command cannot parse multi-choice values, so settings
  must stay single-choice/boolean/text/number.
- `Announcer` queues chat/notification messages from any thread and only shows them while a player
  exists, because chat outside a world only reaches the log.
- `PremiumUuid.isPremium` is the only filter. Keep it strict: uuid.quest early-exits a submission
  and tightens the submitter's rate budget once most of its UUIDs turn out fake (NPCs with v4 UUIDs
  are the risk), and the budget is per IP.
- `ServerGuard` is the safety net behind that filter: a finished submission that early-exited, or with
  at least 10 checked and half of them fake, blocks the servers its UUIDs came from (stored per
  submission as `servers`) for the session. Keep its thresholds stricter than uuid.quest's
  (`QUEST_ABUSE_MIN_CHECKED` 50, `QUEST_ABUSE_FAKE_RATIO` 0.8 in `mcid/uuid-quest/src/config.rs`).
- The API contract is `https://uuid.quest/openapi.json` (source: `mcid/uuid-quest/api-spec/`).

## Build and test

- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew build`; `runClient` under `xvfb-run -a`
  loads it in a dev client.
- The dev client plays with an offline (v3) UUID, so collection only shows results on an
  online-mode server with other players.

## Releases

CI (`.github/workflows/build.yml`) refreshes the `snapshot` pre-release on every push to `main`. A
published, non-prerelease GitHub release tagged `v<mod_version>+<mc>` also uploads the jar to the
LiquidBounce marketplace as item 894 (repo variable `MARKETPLACE_ITEM_ID`). That step needs the repo secret
`API_TOKEN`, our LiquidBounce API token from `../../docs/credentials.md` on our machine; it is not set yet
(owner, 2026-11-16), so such a release would fail at the upload.
