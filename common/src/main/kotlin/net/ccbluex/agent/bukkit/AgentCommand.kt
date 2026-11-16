/*
 * This file is part of Minecraft Agent (https://github.com/CCBlueX/Minecraft-Agent)
 *
 * Copyright (c) 2026 CCBlueX
 *
 * Minecraft Agent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Minecraft Agent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Minecraft Agent. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.agent.bukkit

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import java.util.Locale

/**
 * `/agent`: spawns, lists, moves, arms and removes bots, and sets them on each other.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
internal class AgentCommand(
    private val bots: Bots,
    private val skins: Skins,
) : TabExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val action = args.firstOrNull()?.lowercase()
        if (action == "list") {
            list(sender)
            return true
        }
        if (action !in ACTIONS || args.size < 2) {
            sender.sendMessage(USAGE.joinToString("\n") { "/$label $it" })
            return true
        }
        val name = args[1]
        try {
            when (action) {
                "spawn" -> spawn(sender, name, args.getOrNull(2))
                "remove" -> bot(sender, name)?.let {
                    it.remove()
                    sender.sendMessage("Removed ${it.name}")
                }
                "goto" -> goTo(sender, name, args.drop(2))
                "follow" -> follow(sender, name, args.getOrNull(2))
                "fight" -> fight(sender, name, args.getOrNull(2))
                "give" -> give(sender, name, args.getOrNull(2), args.getOrNull(3))
                "stop" -> bot(sender, name)?.stop()
            }
        } catch (e: IllegalArgumentException) {
            sender.sendMessage(e.message ?: e.toString())
        } catch (e: IllegalStateException) {
            sender.sendMessage(e.message ?: e.toString())
        }
        return true
    }

    private fun spawn(sender: CommandSender, name: String, skinName: String?) {
        // The console spawns at the spawn of the first world
        val location = (sender as? Entity)?.location ?: Bukkit.getWorlds().first().spawnLocation
        val bot = bots.spawn(location, name, skinName?.let(skins::cached))
        sender.sendMessage("Spawned ${bot.name}")
        if (skinName == null || bot.skin != null) return
        skins.lookUp(skinName) { skin ->
            if (bots.get(bot.name) !== bot) return@lookUp
            bot.skin = skin
            if (bot.isSpawned) bot.respawn()
        }
    }

    private fun goTo(sender: CommandSender, name: String, coordinates: List<String>) {
        val bot = bot(sender, name) ?: return
        val origin = (sender as? Entity)?.location ?: bot.player?.location ?: bot.home
        if (coordinates.size < 3) {
            sender.sendMessage("Coordinates: <x> <y> <z>")
            return
        }
        val x = coordinate(coordinates[0], origin.x)
        val y = coordinate(coordinates[1], origin.y)
        val z = coordinate(coordinates[2], origin.z)
        if (x == null || y == null || z == null) {
            sender.sendMessage("Coordinates are numbers, or ~ for where you are")
            return
        }
        val world = bot.player?.world ?: bot.home.world
        bot.moveTo(Location(world, x, y, z), GOTO_RADIUS)
        sender.sendMessage("${bot.name} walks to " + String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z))
    }

    private fun follow(sender: CommandSender, name: String, playerName: String?) {
        val bot = bot(sender, name) ?: return
        val player = playerName?.let(Bukkit::getPlayerExact)
        if (player == null) {
            sender.sendMessage("No player named ${playerName ?: "?"} online")
            return
        }
        bot.follow(player)
        sender.sendMessage("${bot.name} follows ${player.name}")
    }

    private fun list(sender: CommandSender) {
        val all = bots.all
        if (all.isEmpty()) {
            sender.sendMessage("No bots")
            return
        }
        for (bot in all) {
            val player = bot.player
            val at = player?.location
            val where = when {
                at != null -> String.format(Locale.ROOT, "%s %.1f %.1f %.1f, %.1f hp", at.world.name, at.x, at.y, at.z, player.health)
                bot.isDead -> "dead"
                else -> "waiting to spawn"
            }
            val agent = bot.agent
            val doing = when {
                agent?.game != null -> ", plays ${agent.game?.name} (${agent.game?.phase})"
                agent?.skills?.fight?.target != null -> agent.skills.fight?.let { fight ->
                    ", fights ${fight.target?.scoreboardName}: ${fight.stats.hits} hits, " +
                        String.format(Locale.ROOT, "%.1f damage", fight.stats.damageDealt)
                }
                else -> null
            }
            sender.sendMessage("${bot.name}: $where${doing.orEmpty()}")
        }
    }

    private fun fight(sender: CommandSender, name: String, targetName: String?) {
        val bot = bot(sender, name) ?: return
        val target = targetName?.let { bots.get(it)?.player ?: Bukkit.getPlayerExact(it) }
        if (target == null || !target.isValid) {
            sender.sendMessage("No bot or player named ${targetName ?: "?"} here")
            return
        }
        bot.fight(target)
        sender.sendMessage("${bot.name} fights ${target.name}")
    }

    private fun give(sender: CommandSender, name: String, itemName: String?, count: String?) {
        val bot = bot(sender, name) ?: return
        val player = bot.player
        val material = itemName?.let(Material::matchMaterial)
        if (player == null || material == null || material == Material.AIR) {
            sender.sendMessage(if (player == null) "${bot.name} has no body" else "No item named ${itemName ?: "?"}")
            return
        }
        val amount = count?.toIntOrNull()?.coerceIn(1, material.maxStackSize) ?: 1
        player.inventory.addItem(ItemStack(material, amount))
        sender.sendMessage("Gave ${bot.name} $amount ${material.name.lowercase()}")
    }

    private fun bot(sender: CommandSender, name: String): Bot? {
        val bot = bots.get(name)
        if (bot == null) sender.sendMessage("No bot named $name")
        return bot
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>,
    ): List<String> {
        val options = when (args.size) {
            1 -> ACTIONS + "list"
            2 -> if (args[0].lowercase() in ACTIONS - "spawn") bots.all.map { it.name } else emptyList()
            3 -> when (args[0].lowercase()) {
                "follow" -> Bukkit.getOnlinePlayers().map { it.name }
                "fight" -> bots.all.map { it.name } + Bukkit.getOnlinePlayers().map { it.name }
                "give" -> listOf("diamond_sword", "iron_sword", "shield", "bow", "golden_apple")
                "goto" -> listOf("~")
                else -> emptyList()
            }
            4, 5 -> if (args[0].lowercase() == "goto") listOf("~") else emptyList()
            else -> emptyList()
        }
        val typed = args.last().lowercase()
        return options.filter { it.lowercase().startsWith(typed) }
    }

    private companion object {
        val ACTIONS = listOf("spawn", "remove", "goto", "follow", "fight", "give", "stop")
        val USAGE = listOf(
            "spawn <name> [skin]",
            "remove <name>",
            "list",
            "goto <name> <x> <y> <z>",
            "follow <name> <player>",
            "fight <name> <bot or player>",
            "give <name> <item> [count]",
            "stop <name>",
        )
        const val GOTO_RADIUS = 1.0

        fun coordinate(text: String, origin: Double): Double? = when {
            text == "~" -> origin
            text.startsWith("~") -> text.substring(1).toDoubleOrNull()?.plus(origin)
            else -> text.toDoubleOrNull()
        }
    }
}
