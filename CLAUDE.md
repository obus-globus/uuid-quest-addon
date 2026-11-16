# uuid-quest (lb-addon-uuid-quest)

Collects the premium (v4) player UUIDs a Minecraft client sees and submits them to uuid.quest, our public
UUID submit API in front of mcid (`../../mcid/uuid-quest/` on our machine). Ships as a LiquidBounce add-on
(MC 26.3) and as a standalone Fabric mod for every release from 1.20.1 to 26.3, both on one Java core.
User-facing behaviour and install per version are in [README.md](README.md).

## Layout

- `core/`: plain Java 17 (no Minecraft, no Kotlin), Gson + `java.net.http`. `UuidQuest` is the
  orchestrator the platforms drive (`update()` about once a second, also in menus; `onStopping()`; command
  texts). `Submitter` holds the session's seen set and queue, posts `POST /v1/uuids`, polls
  `GET /v1/submissions/{id}`; `RetryPolicy` decides retry/drop; `SubmissionHistory` persists the last 100
  (a 404 on poll is stored as the non-API state `expired`); `Announcer` holds messages until
  `Platform.inWorld()`. Platforms implement `Platform`, `Settings` and `QuestLog`; `StoredSettings` is the
  JSON-backed `Settings` the Fabric mod uses. `UpdateChecker` asks GitHub's
  `/releases/latest` (pre-releases like `snapshot` are ignored) at most every 6 hours, caching the answer in
  `update-check.json` next to the history, and links the jar whose name ends in the running version's
  `+<range>.jar` (else the release page); platforms turn it on with `UuidQuest.enableUpdateCheck`. Core's classes are copied into each jar (`bundled`
  configuration), not nested.
- `liquidbounce/`: the add-on (Kotlin). Built from [`../lb-addon-workspace`](../lb-addon-workspace)
  (CCBlueX's template); its CLAUDE.md covers the add-on build setup and traps (no `mappings` line, plain
  `implementation`). Not a module on purpose: `UuidQuestTracker` is a plain `EventListener` whose tick
  also runs in menus, and `UuidQuestSettings` is an add-on `config(...)` edited through
  `.value set uuidQuest.settings.<name>`, which cannot parse multi-choice values, so settings must stay
  single-choice/boolean/text/number. Config `LiquidBounce/uuidquest.json`, history
  `LiquidBounce/uuid-quest/submissions.json`.
- `fabric/`: the standalone mod (Java), a [Stonecutter](https://stonecutter.kikugie.dev/) tree.
  `fabric/build.gradle.kts` runs once per node in `fabric/versions/<node>/`; a node is named after the
  Minecraft version it compiles and runs against, and its `gradle.properties` holds the supported range
  (`mod.mc_range`, written into `fabric.mod.json`), Java level, Fabric API and Mod Menu versions. Every
  version-dependent call is in `Compat.java` (plus a 1.20.1-only background in `ConfigScreen`). Config
  `config/uuid-quest.json`, history `config/uuid-quest/submissions.json`.

## Fabric nodes

Mojang names everywhere (`officialMojangMappings()` below 26, no mappings on unobfuscated 26.x), so a
node covers every release whose API is binary-compatible in intermediary (below 26) or in official names
(26.x). A new node is only needed where a call the mod makes breaks:

```
node    range             forced by
1.20.1  1.20.1-1.20.2     SystemToastIds enum (1.20.3 has SystemToastId)
1.20.3  1.20.3-1.21.4     ClickEvent/HoverEvent records in 1.21.5 (getToasts -> getToastManager in
                          1.21.2 is a rename only, same intermediary name)
1.21.5  1.21.5-1.21.8     GameProfile became a record (id()/name()) in 1.21.9 (authlib, not remapped)
1.21.9  1.21.9-1.21.11    26.1 is unobfuscated, needs its own jar
26.1    26.1-26.1.2       26.2 moved setScreen and the toast manager to Minecraft.gui
26.3    26.2-26.3         compiled against 26.3 (the LiquidBounce target) so runClient tests the newest
```

`tools/linkcheck.py` established this table: it checks every class and member a built jar references
against other versions' client jars (intermediary below 26), Fabric API and Mod Menu. When a Minecraft
version comes out, run it on the newest jar first (e.g. `python3 tools/linkcheck.py
build/libs/uuid-quest-fabric-<v>+26.2-26.3.jar 26.3 26.4`); if it passes, a test run plus widening
`mod.mc_range` is enough, otherwise add a node for the break. Compile a node against the oldest version
of its range below 26: a Java 17 jar cannot be compiled against a Java 21 Minecraft.

## Build and run

- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew build collectJars`: core tests, the add-on,
  every Fabric node; all jars land in `build/libs/`. Gradle runs on 25 (Stonecutter 0.10 needs it) and
  finds JDK 17/21 for the older nodes in `/usr/lib/jvm` on its own.
- One node: `./gradlew :fabric:1.21.5:build`, `:fabric:1.21.5:runClient` (Mod Menu is on the dev runtime;
  `-Pmodmenu_runtime=false` drops it). Add-on: `:liquidbounce:runClient`. Dev clients are capped at 2 GB.
- Headless runs on this VM: run under your own Xvfb with `WAYLAND_DISPLAY` unset (see
  `../../docs/minecraft-capture.md`); `onboardAccessibility:false` in the run dir's `options.txt` skips
  the first-launch screen.
- The first build configures every node and sets up each Minecraft version (several minutes); keep
  `org.gradle.parallel=false` on this shared VM.
- The dev client plays with an offline (v3) UUID, so collection only shows results on an online-mode
  server with other players.

## Stonecutter

- Version conditions are comments (`//? if >=1.21.9 {` ... `//?}`) evaluated per node; inactive branches
  stay commented. The shared `fabric/src` is kept in the 26.3 state (`stonecutter active` in
  `fabric/stonecutter.gradle.kts`, `vcsVersion` in `settings.gradle.kts`); switching the active version
  rewrites `fabric/src` in place, so switch back to 26.3 before committing.
- Keep explanatory comments outside a conditional branch: text inside a branch must be either all active
  code or one block comment, or Stonecutter misreads it.
- Loom: 26.x needs `net.fabricmc.fabric-loom`, older versions `net.fabricmc.fabric-loom-remap` (applied
  per node in `fabric/build.gradle.kts`; the distributable jar is `remapJar` there, `jar` on 26.x). Both
  come from the same Loom artifact, declared once in the root `build.gradle.kts` so all projects share it;
  subprojects apply plugins without versions.

## Rules behind the code

- `PremiumUuid.isPremium` is the only filter. Keep it strict: uuid.quest early-exits a submission and
  tightens the submitter's rate budget once most of its UUIDs turn out fake (NPCs with v4 UUIDs are the
  risk), and the budget is per IP.
- `ServerGuard` is the safety net behind that filter: a finished submission that early-exited, or with at
  least 10 checked and half of them fake, blocks the servers its UUIDs came from (stored per submission
  as `servers`) for the session. Keep its thresholds stricter than uuid.quest's
  (`QUEST_ABUSE_MIN_CHECKED` 50, `QUEST_ABUSE_FAKE_RATIO` 0.8 in `mcid/uuid-quest/src/config.rs`).
- Core compiles against Gson 2.10 (what 1.20.1 ships) and Java 17; don't raise either.
- The API contract is `https://uuid.quest/openapi.json` (source: `mcid/uuid-quest/api-spec/`).

## Releases

CI (`.github/workflows/build.yml`) builds everything with JDK 17, 21 and 25 and refreshes the `snapshot`
pre-release with all jars on every push to `main`. A published GitHub release tagged
`v<mod_version>+<mc>` (the add-on's version, e.g. `v1.0.0+26.3`) gets all jars attached; if it is not a
pre-release, the add-on jar alone is uploaded to the LiquidBounce marketplace as item 894 (repo variable
`MARKETPLACE_ITEM_ID`). That step needs the repo secret `API_TOKEN`, our LiquidBounce API token from
`../../docs/credentials.md` on our machine; it is not set yet (owner, 2026-11-16), so such a release would
fail at the upload. The Fabric jars are not published anywhere else.
