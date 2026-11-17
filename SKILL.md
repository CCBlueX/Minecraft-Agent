---
name: minecraft-agent
description: Add Minecraft Agent (pathfinding, navigation, bot skills and game modes for Minecraft 26.3, server bots also on Spigot 1.8.8) to a Fabric mod or a Bukkit plugin, and drive bots with it.
---

# Minecraft Agent

Kotlin library for Minecraft 26.3 (Mojang names, Java 25). Group `net.ccbluex`, repository
`https://maven.ccbluex.net/releases` (snapshots: `/snapshots`).

| Artifact | Use it for |
|---|---|
| `minecraft-agent-common` | The game's blocks, entities and items as the libraries see them, on every game version; predicting player movement (`PlayerMotion`, `SimulatedPlayer`); route searches without moving anything; `Navigator`: follows routes with a `Body`; `Agent`: fighting, eating, looting, gear, shop, void rescue, mining, crafting, farming, hunting, sleeping, building, game modes; server bots as Bukkit sees them: `MinecraftAgent`, `Bots`, `Bot`, `BotBody` |
| `minecraft-agent-adapter` | The libraries on Minecraft 26.3: `toAgent()`/`toMinecraft()`, `defineGameTypes()` |
| `minecraft-agent-fabric` | The local player as a body (Fabric mod) |
| `minecraft-agent-paper` | Server bots on Paper 26.3 (plugin `MinecraftAgent`) |
| `minecraft-agent-spigot` | Server bots on Spigot 1.8.8 (plugin `MinecraftAgent`) |

Each one brings what it builds on: in `common`, `simulation` brings `world`, `navigation` brings `pathfinder` and
`simulation`, `skills` brings `navigation`; `adapter` brings `common`; `fabric` and `paper` bring `common` and
`adapter`; `spigot` brings `common`.
Everything public is callable from Java.

## Fabric mod

```kotlin
repositories { maven("https://maven.ccbluex.net/releases") }
dependencies {
    implementation("net.ccbluex:minecraft-agent-fabric:1.0.0")
    // Jar-in-jar is not transitive: every module goes into the mod jar
    for (module in listOf("common", "adapter", "fabric")) {
        include("net.ccbluex:minecraft-agent-$module:1.0.0")
    }
}
```

Call `defineGameTypes()` (`net.ccbluex.agent.v26_3`) once the game's registries are loaded (on client start), before
any other library call. Library calls take the library's types; `toAgent()` and `toMinecraft()` convert, and
`Terrain.live(level)` also takes the game's level.

The mod needs Fabric API and Fabric Language Kotlin. Walk the local player somewhere:

```kotlin
val navigation = ClientNavigation()                      // keep it: it hears the world while kept
navigation.navigator.moveTo(Vec3(100.5, 64.0, -20.5)) { ending -> println(ending) }   // ARRIVED, UNREACHABLE, STOPPED
navigation.navigator.follow { target?.position() }       // a goal that moves; null pauses
navigation.navigator.stop()                             // stops
```

In suspend code, `TickScope` (package `net.ccbluex.agent.tick`) runs coroutines on the tick of whoever calls
`scope.tick()`; inside, `navigator.walkTo(goal)` returns the `Ending`, `waitTicks(n)` waits, and `requires { … }` /
`unchanged { body.level }` end the run quietly once they fail. A host with its own tick loop puts its own `Ticker`
into the context instead (LiquidBounce: `object : Ticker { override suspend fun tickUntil(stopAt: IntPredicate) =
net.ccbluex.liquidbounce.event.tickUntil(stopAt) }`), so its guards apply to every library wait.

`navigation.Options` (digging, bridging, parkour, sprinting, look-ahead, `minPressTicks`/`minReleaseTicks` for
human key timing on plain walks) go in the constructor or `navigation.options`. `ClientBody(turning)` sets how
movement and aim rotations are turned to (`Turning.linear(degrees)`, `skillLevel.turning()` from `skills`, or
`Turning.instant()` when a rotation system of the host paces the turns).
The navigator presses the keys once per tick (`Body.press(input, exact)`); exact keys (jumps, climbs, block
actions, flight) must reach the player unchanged, plain walks may be changed by host features.

`ClientBody` is an `AgentBody` too: with `navigation.agent = Agent(navigation.body, navigation.navigator, Skills())`
the agent ticks instead of the walk, and suspend skills run for the local player on a `TickScope` ticked at the start
of the client tick. `agent.build(blocks, canBreak)` builds a `Map<BlockPos, BlockState>` from the inventory and returns
what it left and which items ran short. With Litematica installed, `Placements.blocks()` (package
`net.ccbluex.agent.v26_3.fabric.litematica`) gives the blocks of its enabled placements.

Survival skills run on the same `Agent` (`@ExperimentalAgentApi`): `gather(Resource.IRON, count)` mines down and
tunnels for a resource, `huntFor(Prey.FOOD)`, `craft(recipe)` and `smelt(input, count, fuels)` with `nextStep` planning the
way to an item from the inventory alone, `farm(Farm(area))`, `fillLava()`, `shelter()` and `sleepThroughNight` for the night, `Dangers`
as reflexes and `give(player, item, count)` to hand items to a teammate. `Options.canBoat`, `Options.canEat` and
`Options.breakableBlocks` (`BreakableBlocks.NATURAL` leaves what players built) steer the walk; `AgentListener.step`
reports what a skill is doing. A body implements `AgentBody.clickInventory` and `inventoryMenu` for the 2 by 2 grid.

Placed bridges are crossed as players do: `Options.bridging` is `Bridging.AUTO` (Ninja, one-stack up rising bridges,
Swift Sneak in Swift Sneak III leggings) or one fixed method (`NINJA`, `GODBRIDGE`, `MOONWALK`, `BREEZILY`,
`ONE_STACK`, `SWIFT_SNEAK`, `TELLY`). `riskyBridges` lets `AUTO` pick the methods that fall on one slip and steps
onto blocks before the server keeps them. Clicks reach no block through another: `Navigator.canClick` and `useItemOn`
refuse a click whose face the rotation the server has does not hit first, unless `Options.canClickThroughBlocks`.

## Server plugin

Install the `MinecraftAgent` plugin jar on the server (`minecraft-agent-paper-<version>-plugin.jar` on Paper
26.3, `minecraft-agent-spigot-<version>-plugin.jar` on Spigot 1.8.8), then:

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

A plugin can bundle the library instead of depending on the plugin: shade `minecraft-agent-paper` or
`minecraft-agent-spigot` (their jars have no plugin.yml), call `MinecraftAgent.start(this)` in `onEnable` and
`MinecraftAgent.stop()` in `onDisable`, and list the Kotlin libraries of the Paper plugin jar's plugin.yml in its own
(1.8.8 has no `libraries`: shade Kotlin and coroutines too). `start(this)` runs the bots, and `bots()` gives them.
`MinecraftAgent`, `Bots`, `Bot`, `BotSettings` and `BotBody` are in `net.ccbluex.agent.bukkit` (`common`), the same on
every server platform.

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
host supplies only what the server says in text (`RoundFacts`) and calls `tick()` every client tick. The Bed Wars
one also takes the blocks the server sets, `blockUpdated(pos, state)`: they tell which blocks players placed. On 26.3 the
adapter's `senses.received(packet)` (`net.ccbluex.agent.v26_3`) feeds it the block and section update packets.

Maps: `skills.maps.MapPlay` plays a world's map alone: it works out the steps from people, buttons, openings, hatches and
pressure plates, keeps those that led on in a `KnownMaps` folder, and glides caves. A client host calls `agent(body)`,
hands in server chat and block changes, and implements `AgentBody.interact` and `respawn`.

API docs: https://ccbluex.github.io/Minecraft-Agent/api/
