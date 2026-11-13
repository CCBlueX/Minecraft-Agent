---
name: minecraft-agent
description: Add Minecraft Agent (pathfinding, navigation, bot skills and game modes for Minecraft 26.3) to a Fabric mod or a Paper plugin, and drive bots with it.
---

# Minecraft Agent

Kotlin library for Minecraft 26.3 (Mojang names, Java 25). Group `net.ccbluex`.

| Artifact | Use it for |
|---|---|
| `minecraft-agent-common` | Predicting player movement (`PlayerMotion`, `SimulatedPlayer`); route searches without moving anything; `Navigator`: follows routes with a `Body` |
| `minecraft-agent-fabric` | The local player as a body (Fabric mod) |

Each one brings what it builds on: `navigation` brings `pathfinder` and `simulation`, `fabric` brings `common`.
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

## Bots on your own host

Implement `Body` (navigation) for whatever moves: keys, turning, digging, item use, inventory.
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
