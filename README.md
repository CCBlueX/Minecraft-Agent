# Minecraft Agent

A Kotlin library for Minecraft 26.3 bots: pathfinding, navigation and game skills. Every public API is callable from
Java.

<p align="center"><a href="https://ccbluex.github.io/Minecraft-Agent/clips/elytra-travel.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/elytra-travel.gif" width="200" alt="Cruises the End with elytra and rockets"></a> <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-parkour.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-parkour.gif" width="200" alt="Parkour jumps"></a> <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-bridge.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-bridge.gif" width="200" alt="Bridges gaps"></a> <a href="https://ccbluex.github.io/Minecraft-Agent/clips/ride-horse.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/ride-horse.gif" width="200" alt="Rides a horse up ledges"></a></p>

## Libraries

`net.ccbluex:minecraft-agent-<library>` from `https://maven.ccbluex.net/snapshots`. Each library brings the ones it
builds on.

- `common`: everything that runs on every game version
  - `world`: the game's blocks, entities and items, the same on every game version
  - `simulation`: player movement, tick by tick
  - `pathfinder`: route searches
  - `navigation`: follows routes
  - `skills`: fighting, looting, game modes
- `vanilla`: the libraries on Minecraft 26.3

## Platforms

### Client

The local player as a body, for Fabric mods.

```kotlin
dependencies {
    implementation("net.ccbluex:minecraft-agent-fabric:1.0.0-SNAPSHOT")
}
```

```kotlin
val navigation = ClientNavigation()
navigation.navigator.moveTo(Vec3(100.5, 64.0, -20.5)) { ending -> println(ending) }
```

### Server

Bots as players on the server, the same on every server platform: `v26_3/paper` as a Paper plugin, `v1_8_8/spigot` as
a [Spigot 1.8.8 plugin](v1_8_8/spigot/README.md). Install the `MinecraftAgent` plugin jar on the server, or shade
`minecraft-agent-paper` into a plugin with bots of its own and call `MinecraftAgent.start(this)` in its `onEnable`.

```kotlin
dependencies {
    compileOnly("net.ccbluex:minecraft-agent-paper:1.0.0-SNAPSHOT")
}
```

```kotlin
val bot = MinecraftAgent.bots().spawn(location, "Steve")
bot.moveTo(target, 1.0)
```

[SKILL.md](SKILL.md) covers the rest: game modes, options and the Kotlin coroutine API.

## Navigation

Yes: passes its game test or worked in a captured run. Untested: should work, never run there. No: not supported yet.

### Walking

| | Client | Server | |
|---|:-:|:-:|---|
| Walks around walls | Yes | Yes | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-detour.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-detour.gif" width="160" alt="Walks around walls"></a> |
| Tunnels through walls | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-break.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-break.gif" width="160" alt="Tunnels through walls"></a> |
| Bridges gaps | Yes | Yes | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-bridge.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-bridge.gif" width="160" alt="Bridges gaps"></a> |
| Pillars up | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-pillar.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-pillar.gif" width="160" alt="Pillars up"></a> |
| Opens doors and fence gates | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-doors.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-doors.gif" width="160" alt="Opens doors and fence gates"></a> |
| Keeps off soul sand, crosses ice | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-soul-sand-ice.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-soul-sand-ice.gif" width="160" alt="Keeps off soul sand, crosses ice"></a> |
| Takes another way when its way gets closed | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-blocked.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-blocked.gif" width="160" alt="Takes another way when its way gets closed"></a> |
| Climbs ladders and vines | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-climb.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-climb.gif" width="160" alt="Climbs ladders and vines"></a> |
| Crouches through low gaps, safe walk on narrow bridges | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-sneak.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-sneak.gif" width="160" alt="Crouches through low gaps, safe walk on narrow bridges"></a> |
| Swims through flooded tunnels | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-swim.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-swim.gif" width="160" alt="Swims through flooded tunnels"></a> |

### Jumping

| | Client | Server | |
|---|:-:|:-:|---|
| Parkour jumps | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/walk-parkour.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/walk-parkour.gif" width="160" alt="Parkour jumps"></a> |
| Assists jumps the player starts | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/parkour-assist.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/parkour-assist.gif" width="160" alt="Assists jumps the player starts"></a> |
| Jumps from the very edge | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/parkour-edge.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/parkour-edge.gif" width="160" alt="Jumps from the very edge"></a> |

### Following

| | Client | Server | |
|---|:-:|:-:|---|
| Walks where the camera faces, to a goal, and after a player | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/auto-walk.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/auto-walk.gif" width="160" alt="Walks where the camera faces, to a goal, and after a player"></a> |
| Walks to far goals in stages | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/auto-walk-stages.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/auto-walk-stages.gif" width="160" alt="Walks to far goals in stages"></a> |

### Flying and riding

| | Client | Server | |
|---|:-:|:-:|---|
| Flies with an elytra | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/flight-elytra.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/flight-elytra.gif" width="160" alt="Flies with an elytra"></a> |
| Cruises the End with elytra and rockets | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/elytra-travel.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/elytra-travel.gif" width="160" alt="Cruises the End with elytra and rockets"></a> |
| Flies through a canyon in creative | Yes | No | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/flight-canyon.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/flight-canyon.gif" width="160" alt="Flies through a canyon in creative"></a> |
| Flies over a wall in creative | Yes | No | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/flight-wall.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/flight-wall.gif" width="160" alt="Flies over a wall in creative"></a> |
| Rides a boat | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/ride-boat.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/ride-boat.gif" width="160" alt="Rides a boat"></a> |
| Rides a horse up ledges | Yes | Untested | <a href="https://ccbluex.github.io/Minecraft-Agent/clips/ride-horse.mp4"><img src="https://ccbluex.github.io/Minecraft-Agent/clips/ride-horse.gif" width="160" alt="Rides a horse up ledges"></a> |
| Takes teleport routes | No | Untested |  |

## Agent usage

Most of this code was written by AI models. Each declaration they wrote names its model in an `@agent` KDoc tag.
These are all models used so far and what they cost, summed up by [agent-usage](https://github.com/1zun4/agent-usage):

<a href="https://ccbluex.github.io/Minecraft-Agent/ai-usage/">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://ccbluex.github.io/Minecraft-Agent/ai-usage-dark.svg">
    <img alt="AI models used in Minecraft Agent and their cost" src="https://ccbluex.github.io/Minecraft-Agent/ai-usage-light.svg">
  </picture>
</a>

## Commits

`type(scope): subject`, lowercase, no trailing period.

`feat` `fix` `refactor` `chore` `docs`

## License

This project is subject to the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html) (GPLv3). This only applies to source code located directly in this clean repository. During the development and compilation process, additional source code may be used to which we have obtained no rights. Such code is not covered by the GPL license.

For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice nor legally binding.

*Actions that you are allowed to do:*

* Use
* Share
* Modify

*If you do decide to use ANY code from the source:*

* **You must disclose the source code of your modified work and the source code you took from this project. This means you are not allowed to use code from this project (even partially) in a closed-source (or even obfuscated) application.**
* **Your modified application must also be licensed under the GPL.**
