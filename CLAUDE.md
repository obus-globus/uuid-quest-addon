# lb-addon-uuid-quest

LiquidBounce add-on that collects the premium (v4) player UUIDs the client sees and submits them to
uuid.quest, our public UUID submit API in front of mcid (`/home/clawd/obus/mcid/uuid-quest/`). Built
from [`../lb-addon-workspace`](../lb-addon-workspace) (CCBlueX's add-on template); its CLAUDE.md
covers the add-on build setup and traps. User-facing behaviour is in [README.md](README.md).

## Code

- `UuidQuestSubmitter` holds the session's seen set and the pending queue and talks to
  `POST https://uuid.quest/v1/uuids`; `flushBlocking` runs from `UuidQuestAddon.onStopping`.
- `PremiumUuid.isPremium` is the only filter. Keep it strict: uuid.quest early-exits a submission
  and tightens the submitter's rate budget once most of its UUIDs turn out fake (NPCs with v4 UUIDs
  are the risk), and the budget is per IP.
- The API contract is `https://uuid.quest/openapi.json` (source: `mcid/uuid-quest/api-spec/`).

## Build and test

- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew build`; `runClient` under `xvfb-run -a`
  loads it in a dev client.
- The dev client plays with an offline (v3) UUID, so collection only shows results on an
  online-mode server with other players.
