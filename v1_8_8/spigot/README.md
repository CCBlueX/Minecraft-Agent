# v1_8_8/spigot

Server bots as a Spigot 1.8.8 plugin: the same `MinecraftAgent`, `Bots` and `/agent` as on Paper, with bodies that
move and fight as 1.8 players. No minigame integrations.

## Install

Put the plugin jar (`minecraft-agent-spigot-<version>-plugin.jar`, Kotlin and the library inside) into
`plugins/`, then list it in your plugin.yml:

```yaml
depend: [MinecraftAgent]
```

```kotlin
dependencies { compileOnly("net.ccbluex:minecraft-agent-spigot:1.0.0-SNAPSHOT") }
```

```kotlin
val bot = MinecraftAgent.bots().spawn(location, "Steve")
bot.moveTo(target, 1.0)
bot.fight(enemy)
```

## In a host plugin

A plugin with its own bots shades `minecraft-agent-spigot` with Kotlin and coroutines (1.8 has no `libraries`)
and calls `MinecraftAgent.start(this)` in `onEnable` and `MinecraftAgent.stop()` in `onDisable`.

`bot.getBody()` is a `SpigotBody`. It differs from Paper's body where 1.8 does:

1. Sword blocking: `startUsingItem` with a sword raises it and `isBlocking` halves hits; no off hand or shield.
2. Full strength every hit (`attackStrength` is 1), reach 3, no jabs.
3. `launch` throws pearls, snowballs and eggs, and shoots bows at full draw.
4. `setTabEntry(false)` takes the body out of the tab list two seconds after a player starts seeing it, once the skin
   loaded; a 1.8 client lists every player info entry it has.
5. No chunk tickets: bots keep no chunks loaded; spawn them in loaded chunks.
