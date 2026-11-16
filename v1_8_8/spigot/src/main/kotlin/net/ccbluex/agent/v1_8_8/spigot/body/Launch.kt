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

package net.ccbluex.agent.v1_8_8.spigot.body

import net.minecraft.server.v1_8_R3.Entity
import net.minecraft.server.v1_8_R3.EntityArrow
import net.minecraft.server.v1_8_R3.EntityEgg
import net.minecraft.server.v1_8_R3.EntityEnderPearl
import net.minecraft.server.v1_8_R3.EntitySnowball
import net.minecraft.server.v1_8_R3.ItemStack
import net.minecraft.server.v1_8_R3.Items
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason

/**
 * Throws or fires [stack] the way the bot looks, as the 1.8 items do: an ender pearl, snowball or egg, or an arrow as
 * from a fully drawn bow. Null for other items or when the projectile did not spawn.
 *
 * @agent claude-opus-5-5
 */
internal fun BotPlayer.launch(stack: ItemStack): Entity? {
    val throwPitch = 0.4f / (world.random.nextFloat() * 0.4f + 0.8f)
    val (projectile, sound) = when (stack.item) {
        Items.ENDER_PEARL -> EntityEnderPearl(world, this) to ("random.bow" to throwPitch)
        Items.SNOWBALL -> EntitySnowball(world, this) to ("random.bow" to throwPitch)
        Items.EGG -> EntityEgg(world, this) to ("random.bow" to throwPitch)
        // ItemBow.onPlayerStoppedUsing at full draw: power 1, a critical arrow
        Items.BOW, Items.ARROW -> EntityArrow(world, this, 2f).apply { setCritical(true) } to
            ("random.bow" to 1f / (world.random.nextFloat() * 0.4f + 1.2f) + 0.5f)
        else -> return null
    }
    if (!world.addEntity(projectile, SpawnReason.CUSTOM) || projectile.dead) return null
    world.makeSound(this, sound.first, if (projectile is EntityArrow) 1f else 0.5f, sound.second)
    bw()
    return projectile
}
