# Minecraft Agent

A Kotlin library for Minecraft 26.3 bots: pathfinding, navigation and game skills. Every public API is callable from
Java.

## Libraries

`net.ccbluex:minecraft-agent-<library>` from `https://maven.ccbluex.net/snapshots`. Each library brings the ones it
builds on.

- `common`: everything that runs on every game version
  - `world`: the game's blocks, entities and items, the same on every game version
  - `simulation`: player movement, tick by tick
  - `pathfinder`: route searches
  - `navigation`: follows routes
  - `skills`: fighting, looting, mining, crafting, farming, hunting, sleeping, building, game modes
- `adapter`: the libraries on Minecraft 26.3

## Platforms

### Client

The local player as a body, for Fabric mods. With [Litematica](https://modrinth.com/mod/litematica) installed,
`fabric` reads its placements as blocks to build.

```kotlin
dependencies {
    implementation("net.ccbluex:minecraft-agent-fabric:1.0.0-SNAPSHOT")
    // Jar-in-jar is not transitive: every module goes into the mod jar
    for (module in listOf("common", "adapter", "fabric")) {
        include("net.ccbluex:minecraft-agent-$module:1.0.0-SNAPSHOT")
    }
}
```

```kotlin
defineGameTypes()   // once the game's registries are loaded, before any other library call
val navigation = ClientNavigation()
navigation.navigator.moveTo(Vec3(100.5, 64.0, -20.5)) { ending -> println(ending) }
```

### Server

Bots as players on the server, the same on every server platform: Paper 26.3 as a plugin, and a
[Spigot 1.8.8 plugin](v1_8_8/spigot/README.md). Install the `MinecraftAgent` plugin jar on the server, or shade
`minecraft-agent-paper` or `minecraft-agent-spigot` into a plugin with bots of its own and call
`MinecraftAgent.start(this)` in its `onEnable`.

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
A linked feature has a video.

### Walking

| | Client | Server |
|---|:-:|:-:|
| [Walks around walls](https://ccbluex.github.io/Minecraft-Agent/clips/walk-detour.mp4) | Yes | Yes |
| [Tunnels through walls](https://ccbluex.github.io/Minecraft-Agent/clips/walk-break.mp4) | Yes | Untested |
| [Bridges gaps](https://ccbluex.github.io/Minecraft-Agent/clips/walk-bridge.mp4) | Yes | Yes |
| [Pillars up](https://ccbluex.github.io/Minecraft-Agent/clips/walk-pillar.mp4) | Yes | Untested |
| [Opens doors and fence gates](https://ccbluex.github.io/Minecraft-Agent/clips/walk-doors.mp4) | Yes | Untested |
| [Keeps off soul sand, crosses ice](https://ccbluex.github.io/Minecraft-Agent/clips/walk-soul-sand-ice.mp4) | Yes | Untested |
| [Takes another way when its way gets closed](https://ccbluex.github.io/Minecraft-Agent/clips/walk-blocked.mp4) | Yes | Untested |
| [Climbs ladders and vines](https://ccbluex.github.io/Minecraft-Agent/clips/walk-climb.mp4) | Yes | Untested |
| [Crouches through low gaps, safe walk on narrow bridges](https://ccbluex.github.io/Minecraft-Agent/clips/walk-sneak.mp4) | Yes | Untested |
| [Swims through flooded tunnels](https://ccbluex.github.io/Minecraft-Agent/clips/walk-swim.mp4) | Yes | Untested |

### Jumping

| | Client | Server |
|---|:-:|:-:|
| [Parkour jumps](https://ccbluex.github.io/Minecraft-Agent/clips/walk-parkour.mp4) | Yes | Untested |
| [Assists jumps the player starts](https://ccbluex.github.io/Minecraft-Agent/clips/parkour-assist.mp4) | Yes | Untested |
| [Jumps from the very edge](https://ccbluex.github.io/Minecraft-Agent/clips/parkour-edge.mp4) | Yes | Untested |

### Following

| | Client | Server |
|---|:-:|:-:|
| [Walks where the camera faces, to a goal, and after a player](https://ccbluex.github.io/Minecraft-Agent/clips/auto-walk.mp4) | Yes | Untested |
| [Walks to far goals in stages](https://ccbluex.github.io/Minecraft-Agent/clips/auto-walk-stages.mp4) | Yes | Untested |

### Flying and riding

| | Client | Server |
|---|:-:|:-:|
| [Flies with an elytra](https://ccbluex.github.io/Minecraft-Agent/clips/flight-elytra.mp4) | Yes | Untested |
| [Cruises the End with elytra and rockets](https://ccbluex.github.io/Minecraft-Agent/clips/elytra-travel.mp4) | Yes | Untested |
| [Flies through a canyon in creative](https://ccbluex.github.io/Minecraft-Agent/clips/flight-canyon.mp4) | Yes | No |
| [Flies over a wall in creative](https://ccbluex.github.io/Minecraft-Agent/clips/flight-wall.mp4) | Yes | No |
| [Rides a boat](https://ccbluex.github.io/Minecraft-Agent/clips/ride-boat.mp4) | Yes | Untested |
| [Rides a horse up ledges](https://ccbluex.github.io/Minecraft-Agent/clips/ride-horse.mp4) | Yes | Untested |
| Takes teleport routes | No | Untested |

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
