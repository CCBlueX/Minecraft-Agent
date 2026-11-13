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

package net.ccbluex.agent.v26_3.paper.body

import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

// The speed of a thrown snowball, pearl or wind charge, and of an arrow from a fully drawn bow
private const val THROW_POWER = 1.5f
private const val BOW_POWER = 3.0f

/**
 * Throws or fires [stack] the way the bot looks: a wind charge, ender pearl, snowball, or an arrow as from a fully
 * drawn bow. Null for other items or when the projectile did not spawn.
 *
 * @agent claude-opus-5-5
 */
internal fun BotPlayer.launch(stack: ItemStack): Projectile? {
    val level = level()
    var pitch = 0.4f / (level.random.nextFloat() * 0.4f + 0.8f)
    var volume = 0.5f
    val item = stack.item
    val sound: SoundEvent
    val projectile: Projectile = when {
        stack.`is`(Items.WIND_CHARGE) -> {
            sound = SoundEvents.WIND_CHARGE_THROW
            Projectile.spawnProjectileFromRotation(
                { spawnLevel, shooter, _ ->
                    WindCharge(EntityTypes.WIND_CHARGE, spawnLevel).apply {
                        setOwner(shooter)
                        setPos(shooter.x, shooter.eyeY, shooter.z)
                    }
                },
                level, stack, this, 0f, THROW_POWER, 1f,
            )
        }
        stack.`is`(Items.ENDER_PEARL) -> {
            sound = SoundEvents.ENDER_PEARL_THROW
            Projectile.spawnProjectileFromRotation(
                { spawnLevel, shooter, thrown -> ThrownEnderpearl(spawnLevel, shooter, thrown) },
                level, stack, this, 0f, THROW_POWER, 1f,
            )
        }
        stack.`is`(Items.SNOWBALL) -> {
            sound = SoundEvents.SNOWBALL_THROW
            Projectile.spawnProjectileFromRotation(
                { spawnLevel, shooter, thrown -> Snowball(spawnLevel, shooter, thrown) },
                level, stack, this, 0f, THROW_POWER, 1f,
            )
        }
        item is ArrowItem || item is BowItem -> {
            // BowItem.releaseUsing at full draw
            val bow = if (item is BowItem) stack else ItemStack(Items.BOW)
            val ammo = if (item is ArrowItem) stack.copyWithCount(1) else ItemStack(Items.ARROW)
            val arrowItem = ammo.item as? ArrowItem ?: return null
            val arrow = arrowItem.createArrow(level, ammo, this, bow)
            arrow.setCritArrow(true)
            sound = SoundEvents.ARROW_SHOOT
            volume = 1f
            pitch = 1f / (level.random.nextFloat() * 0.4f + 1.2f) + 0.5f
            Projectile.spawnProjectile(arrow, level, ammo) {
                it.shootFromRotation(this, xRot, yRot, 0f, BOW_POWER, 1f)
            }
        }
        else -> return null
    }
    if (projectile.isRemoved) return null
    level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch)
    swingHand(InteractionHand.MAIN_HAND)
    return projectile
}
