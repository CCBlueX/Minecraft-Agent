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

package net.ccbluex.agent.pathfinder.glide

import net.ccbluex.agent.world.entity.EquipmentSlot
import net.ccbluex.agent.world.entity.LivingEntity
import net.ccbluex.agent.world.item.ItemStack
import net.ccbluex.agent.world.item.Items
import kotlin.math.min

/**
 * How the player can glide, captured on the render thread for one search.
 *
 * From LiquidBounce: [Abilities.kt](https://github.com/CCBlueX/LiquidBounce/blob/fcbd131fe5a86aa31cb999a75abf563b4c35bf7e/src/main/kotlin/net/ccbluex/liquidbounce/utils/navigation/glide/Abilities.kt#L35-L71)
 *
 * @agent claude-opus-5-5
 */
public class GlideAbilities(
    public val rockets: Int,
    /**
     * Ticks the weakest rocket pushes.
     */
    public val rocketTicks: Int,
    /**
     * Ticks the elytra lasts before it would break.
     */
    public val glideTicks: Int,
) {

    public companion object {

        private const val TICKS_PER_DAMAGE = 20
        private const val ROCKET_TICKS_PER_FLIGHT = 10

        /**
         * Gliding with [elytra] worn on the chest and, when [useRockets], the rockets among [stacks] (offhand and
         * hotbar); null when [elytra] cannot glide.
         */
        public fun of(elytra: ItemStack, stacks: Iterable<ItemStack>, useRockets: Boolean): GlideAbilities? {
            if (!elytra.canGlideUsing(EquipmentSlot.CHEST)) {
                return null
            }

            val glideTicks = if (elytra.isDamageableItem) {
                (elytra.maxDamage - elytra.damageValue - 1) * TICKS_PER_DAMAGE
            } else {
                Int.MAX_VALUE
            }

            var rockets = 0
            var flight = Int.MAX_VALUE
            if (useRockets) {
                for (stack in stacks) {
                    if (stack.`is`(Items.FIREWORK_ROCKET)) {
                        rockets += stack.count
                        flight = min(flight, stack.fireworkFlightDuration ?: 1)
                    }
                }
            }

            val rocketTicks = if (rockets > 0) ROCKET_TICKS_PER_FLIGHT * (1 + flight) else 0
            return GlideAbilities(rockets, rocketTicks, glideTicks)
        }
    }

}
