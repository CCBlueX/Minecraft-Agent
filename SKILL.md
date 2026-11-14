---
name: minecraft-agent
description: Add Minecraft Agent (pathfinding, navigation, bot skills and game modes for Minecraft 26.3) to a Fabric mod or a Paper plugin, and drive bots with it.
---

# Minecraft Agent

Kotlin library for Minecraft 26.3 (Mojang names, Java 25). Group `net.ccbluex`.

| Artifact | Use it for |
|---|---|
| `minecraft-agent-common` | Predicting player movement (`PlayerMotion`, `SimulatedPlayer`); route searches without moving anything; `Navigator`: follows routes with a `Body`; `Agent`: fighting, eating, looting, gear, shop, void rescue, game modes; what server bots share on every server platform |
| `minecraft-agent-fabric` | The local player as a body (Fabric mod) |
| `minecraft-agent-paper` | Server bots (Paper plugin `MinecraftAgent`) |

Each one brings what it builds on: `navigation` brings `pathfinder` and `simulation`, `skills` brings `navigation`,
`fabric` brings `common`, `paper` brings `common`.
Everything public is callable from Java.

## Fabric mod

```kotlin
dependencies {
    implementation("net.ccbluex:minecraft-agent-fabric:1.0.0")
    include("net.ccbluex:minecraft-agent-fabric:1.0.0")
}
```

The mod needs Fabric API and Fabric Language Kotlin. Walk the local player somewhere:

```kotlin
val navigation = ClientNavigation()
navigation.moveTo(Vec3(100.5, 64.0, -20.5))   // stops on arrival
navigation.follow { target?.position() }    // a goal that moves; null pauses
navigation.stop()
```

`navigation.Options` (digging, bridging, parkour, sprinting, look-ahead) go in the constructor or `navigation.options`.
`ClientBody(maxTurn)` limits how fast the camera turns.

## Paper plugin

Install the `MinecraftAgent` plugin jar (`minecraft-agent-paper-<version>-plugin.jar`) on the server, then:

```kotlin
dependencies { compileOnly("net.ccbluex:minecraft-agent-paper:1.0.0") }
```

```yaml
# plugin.yml
depend: [MinecraftAgent]
```

```kotlin
val bot = MinecraftAgent.bots().spawn(location, "Steve")
bot.moveTo(target, 1.0)
bot.follow(player)
bot.fight(enemy)                 // with whatever it holds
bot.play(gameMode)               // a skills.games mode, see below
bot.remove()
```

## Bots on your own host

Implement `Body` (navigation) or `AgentBody` (skills) for whatever moves: keys, turning, digging, item use, inventory.
Conventions: inventory indices as in `Inventory` (0-8 hotbar, 36-39 armor feet to head, 40 offhand); the last `press`
in a tick wins; `players()` returns every player the body can see, bots included, if your bots are not in the
level's player list. Then:

```kotlin
val navigator = Navigator(body, Options(), listener)
navigator.navigate(goal, radius)   // once per tick, before the player moves
navigator.halt()
navigator.options = navigator.options.copy(canPlace = false)   // takes effect on the next tick
```

A `navigation.Listener` gets routes (`routeFound`), steps, stuck spots, arrivals and failures, and what a renderer
draws (`routeSearching`, `routeFollowing`, `blocksAhead`, `routeDone`, `routeInvalid`, `routeCleared`). Hosts feed world
changes: `navigator.blockChanged`, `levelChanged`, `positionCorrected`, and `SectionCache.blockChanged`,
`columnChanged`, `forget` for block and chunk updates. A client host calls `navigator.turnView()` after the movement
packet, so the camera turns where the walk looks ahead.

## Skills and game modes

```kotlin
val skills = Skills.builtIn(body, SkillLevel.NORMAL, Personality.BALANCED)
val agent = Agent(body, navigator, skills)
agent.game = SkyWars(senses)       // null: only the abilities run
agent.tick()                       // once per tick, instead of navigator.navigate
```

Game modes take senses the host supplies (round state, teams, chests, beds): `SkyWars(skywars.Senses)`,
`Blitz(blitz.Senses)`, `BedWars(bedwars.Senses)`, `Duels(duels.Senses)`. They are `@ExperimentalAgentApi`: game modes
change while the bots get better; hosts opt in. A null ability in `Skills` leaves it to the host. Kits are never
built in; bots use what they hold.

A client host does not implement them: `bedwars.LevelSenses(level, facts)`, `skywars.LevelSenses(level, facts, chests)`
and `blitz.LevelSenses(level, facts, chests)` read beds, generators, chests, players and the border from the level; the
host supplies only what the server says in text (`RoundFacts`) and calls `tick()` every client tick.

API docs: https://ccbluex.github.io/Minecraft-Agent/api/
