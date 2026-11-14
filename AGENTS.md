# Minecraft Agent

Minecraft 26.3 (Mojang names, unobfuscated), Java 25, Kotlin. Root package `net.ccbluex.agent`.

| Module | Artifact (`net.ccbluex`) | Builds with | Needs |
|---|---|---|---|
| `common/` | `minecraft-agent-common` | `agent.shared` | - |
| `v26_3/fabric/` | `minecraft-agent-fabric` | `agent.fabric` (Loom, client) | common |
| `v26_3/paper/` | `minecraft-agent-paper`, plugin jar (classifier `plugin`) | `agent.paper` (paperweight) | common |
| `gradle/build-logic/` | - | convention plugins and checks | - |

- Shared modules compile against the vanilla server jar only (`serverOnlyMinecraftJar()`), so their classes link
  on the Fabric client and on Paper. `checkClientClasses` fails on `net.minecraft.client` or `com.mojang.blaze3d`
  in their sources or classes.
- The Paper plugin jar bundles the library modules; Kotlin and coroutines come from `libraries` in `plugin.yml`
  (write `libraries: ${libraries}` and `version: "${version}"`, the build fills them in).
- Dependency repositories are in `agent.kotlin`, not in settings: Loom and paperweight add project repositories,
  which make Gradle ignore the settings ones.

## Commands

The container is shared and capped at 20G. Always serialize Gradle and cap memory:

```sh
flock /tmp/minecraft-agent-gradle.lock ./gradlew -Dorg.gradle.jvmargs=-Xmx2g build
flock /tmp/minecraft-agent-gradle.lock ./gradlew -Dorg.gradle.jvmargs=-Xmx2g updateKotlinAbi   # after an intended API change
timeout 900 flock /tmp/minecraft-agent-gradle.lock xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew -Dorg.gradle.jvmargs=-Xmx2g :v26_3-fabric:runClientGameTest
timeout 600 flock /tmp/minecraft-agent-gradle.lock ./gradlew -Dorg.gradle.jvmargs=-Xmx2g :v26_3-paper:runServer
```

- `build` runs the unit tests, the ABI check (`api/*.api`), `checkLicenseHeaders` and `checkClientClasses`.
- Game clients and servers run with `-Xmx2g` (set by the build) and a hard timeout; `runClientGameTest` has a
  10 minute Gradle timeout (`-PgameTestTimeoutMinutes` for a whole local run). Kill leftovers afterwards (`ps`,
  check `/proc/<pid>/cwd`).
- Never start a client while another `runClientGameTest`/`fabric.client.gametest` process from another directory
  runs; wait for it.
- Game tests: register each in `v26_3/fabric/src/gametest/resources/fabric.mod.json`, with
  `net.ccbluex.gametest.harness.ResultsGameTest` last. `-PgameTestOnly=WalkSneak,WalkSwim` runs only those. Server
  tests run with `GAMETEST_SERVER_ADDRESS` and `GAMETEST_SERVER_ANTICHEAT`. Count `Test passed` lines; the exit
  status alone proves nothing.

## Code rules

Kotlin:
1. Sealed interfaces with exhaustive `when` for moves, steps, phases and events.
2. Value classes for units and ids. Data classes with defaults for options.
3. Extension functions live in the file that owns the type. No `!!`, no `lateinit` in the API.
4. Game modes (`skills.games`) read top to bottom as suspend code on a `net.ccbluex.agent.tick.TickScope` (waits:
   `waitTicks`, `tickConditional`, `tickUntil`; guards: `requires`, `unchanged`).

Readable:
1. Names say what a thing is: `walk.Moves`, not `WalkMoves`. Where the plain name is a Minecraft class, the
   qualifier goes first, as in Mojang's `WalkNodeEvaluator`: `WalkAbilities`, not `walk.Abilities`.
2. Names follow Mojang's mappings: their words and grammar (`level`, `xRot`/`yRot`, `tick()`, `isDone()`,
   `canUse()`), never an existing Minecraft class name for something else.
3. One concept per file; a file over about 400 lines is split.
4. Comments only for what the code cannot say.
5. KDoc on three lines with the text on the middle one, never `/** text */`.
6. The README lists every library module.

Stable API:
1. Explicit API mode. Only entry types are public; the rest is `internal`.
2. ABI dumps (`api/*.api`) are checked on every build. Changing a dump is a version decision.
3. SemVer. Breaking changes need a major version; removals are deprecated for one minor first.
4. The game modes in `skills.games` (not `GameMode` itself) are `@ExperimentalAgentApi`: they change while the bots
   get better. `@InternalAgentApi` is for library modules only (they opt in in `agent.publish`); ABI dumps skip it.
5. Callable from Java: every feature has a plain call or callback form (`Navigator.moveTo(goal, radius, Consumer)`,
   `TickScope.tick()`); suspend functions (`Navigator.walkTo`) are a Kotlin layer over them. Kotlin function types
   only there and in `@InternalAgentApi`.
6. Bots report routes, steps, stuck spots, phases and decisions through a listener API, so the library knows
   nothing about the tools that record them.

## AI provenance

Every class, object, interface, enum or top-level function written by an AI model carries
`@agent <model>` as the last KDoc tag. The agent writing or changing the code adds it, naming
its own model.

- One model wrote it: the tag goes on the declaration.
- Another model later changes a function's logic: that function gets its own `@agent`.
  Renames, imports, formatting and moves don't count.
- Several models changed it: one tag per model, oldest first.
- No other KDoc: `/** @agent claude-opus-5-5 */` on its own.
- Model id as the provider names it, without the provider prefix: `claude-opus-5-5`, `gpt-5.6-sol`.
- Code people wrote, with AI only as assistance, gets no tag. Tags move with the code.
- Code taken from elsewhere keeps the tags of who wrote it there: people's code none, an agent's code that
  agent's model. Rewriting its logic adds your own model.

[agent-usage](https://github.com/1zun4/agent-usage) (v1.0.0, run in CI) checks the tags and `.agent-usage/`. Every
commit made with an AI model records its usage, and the README totals follow:

```sh
curl -fsSLo /tmp/agent-usage https://raw.githubusercontent.com/1zun4/agent-usage/v1.0.0/agent-usage
python3 /tmp/agent-usage record <session log or folder> --from <first call of the change> --change "<commit subject>"
python3 /tmp/agent-usage summary --update README.md
python3 /tmp/agent-usage check
```

`check` fails on an `@agent` tag that is not the last KDoc tag or not in `name-version` form, on a tagged model
without a usage file, and on an edited or deleted usage file.

## Also

- Commit bodies (README "Commits"): only when the subject leaves the next reader guessing, and only the scenario: no
  servers, times, runs or accounts.
- Game and network specific code (minigame plugins, servers) stays in the host; this repo is the base library.
- No anticheat bypasses in this repo.
- Every change by an AI model adds its own file to `.agent-usage/`. Never edit an existing one.
- Every source file starts with the Minecraft Agent license header (copy it from any file; `checkLicenseHeaders`
  fails without it). Code taken from LiquidBounce links its original in its KDoc with a GitHub permalink:
  `From LiquidBounce: [File.kt](https://github.com/CCBlueX/LiquidBounce/blob/<sha>/<path>#L1-L9)`, before the tags.
