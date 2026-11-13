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

package net.ccbluex.agent.skills.gear

import net.ccbluex.agent.skills.CARRIED_SLOTS
import net.ccbluex.agent.skills.itemId
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments

/**
 * How well someone is equipped, as far as it can be seen from outside: armor points and weapon damage by tier and
 * enchantment level, scaled down with missing health to compare two players.
 *
 * From LiquidBounce: [Gear.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/Gear.kt#L43-L86)
 *
 * @property armor armor points of the worn pieces with their protection
 * @property weapon about the damage of a hit with the weapon, sharpness included
 * @property health 0 to 1; 1 where it is unknown
 * @agent claude-opus-5-5
 */
internal class GearScore(val armor: Double, val weapon: Double, val health: Double) {

    val equipment = armor + weapon * WEAPON_WEIGHT

    /**
     * [equipment] scaled down with missing health.
     */
    val score = equipment * (HEALTH_FLOOR + (1.0 - HEALTH_FLOOR) * health)

    /**
     * 1 with nothing, down to 0 once [equipment] reaches [GOOD].
     */
    val weakness = (1.0 - equipment / GOOD).coerceIn(0.0, 1.0)

    override fun toString() = "%.1f[a%.1f w%.1f hp%.0f%%]".format(java.util.Locale.ROOT, score, armor, weapon, health * 100)

    companion object {

        /**
         * Full iron armor and an iron sword.
         */
        const val GOOD = 24.0

        /**
         * What an empty or non-weapon hand of someone else counts as: they keep their sword elsewhere while building.
         */
        const val UNSEEN_WEAPON = 5.0

        private const val WEAPON_WEIGHT = 1.5
        private const val HEALTH_FLOOR = 0.5

        val ARMOR_SLOTS = arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

        /**
         * The player with the best weapon anywhere in its inventory.
         */
        fun own(player: Player): GearScore =
            GearScore(wornArmor(player), bestWeapon(player.inventory), healthOf(player))

        /**
         * [entity] by its worn armor and the weapon in its hand, or the best one [memory] saw it hold.
         */
        fun of(entity: LivingEntity, memory: WeaponMemory? = null): GearScore {
            val held = weaponScore(entity.mainHandItem).takeIf { it > 0.0 } ?: UNSEEN_WEAPON
            val weapon = maxOf(held, memory?.best(entity.uuid) ?: 0.0)
            return GearScore(wornArmor(entity), weapon, healthOf(entity))
        }

        fun wornArmor(entity: LivingEntity): Double = ARMOR_SLOTS.sumOf { armorScore(entity.getItemBySlot(it)) }

        fun bestWeapon(inventory: Inventory): Double = CARRIED_SLOTS.maxOf { weaponScore(inventory.getItem(it)) }

        /**
         * About the damage of a hit with the item, sharpness included; 0 for anything but a sword or axe.
         */
        fun weaponScore(stack: ItemStack): Double {
            if (stack.isEmpty) return 0.0
            val name = name(stack)
            val base = when {
                name.endsWith("_sword") -> tier(name, 4.0, 4.0, 5.0, 6.0, 7.0, 8.0)
                // Slower than a sword, so worth a little less than its damage says
                name.endsWith("_axe") -> tier(name, 3.5, 3.5, 4.5, 5.5, 6.5, 7.5)
                else -> return 0.0
            }
            val sharpness = level(stack, Enchantments.SHARPNESS)
            return base + if (sharpness > 0) 0.5 + 0.5 * sharpness else 0.0
        }

        /**
         * Armor points of a piece with its protection; 0 for anything else.
         */
        fun armorScore(stack: ItemStack): Double {
            val slot = armorSlot(stack) ?: return 0.0
            val name = name(stack)
            var points = when (slot) {
                EquipmentSlot.HEAD -> tier(name, 1.0, 2.0, 2.0, 2.0, 3.0, 3.0)
                EquipmentSlot.CHEST -> tier(name, 3.0, 5.0, 5.0, 6.0, 8.0, 8.0)
                EquipmentSlot.LEGS -> tier(name, 2.0, 3.0, 4.0, 5.0, 6.0, 6.0)
                else -> tier(name, 1.0, 1.0, 1.0, 2.0, 3.0, 3.0)
            }
            if (name.startsWith("leather")) {
                points = when (slot) {
                    EquipmentSlot.CHEST -> 3.0
                    EquipmentSlot.LEGS -> 2.0
                    else -> 1.0
                }
            }
            // Netherite's toughness on top of diamond's points
            if (name.startsWith("netherite")) points += 0.5
            return points + level(stack, Enchantments.PROTECTION) * 0.5
        }

        /**
         * The armor slot the piece goes on; null for anything that is not head, chest, legs or feet armor.
         */
        fun armorSlot(stack: ItemStack): EquipmentSlot? {
            if (stack.isEmpty) return null
            val slot = stack.get(DataComponents.EQUIPPABLE)?.slot() ?: return null
            return slot.takeIf { it in ARMOR_SLOTS && name(stack).let(::isArmorName) }
        }

        /**
         * Whether [stack] beats what [inventory] holds: armor better than the worn and carried pieces of its slot,
         * or a weapon better than its best one.
         */
        fun isUpgrade(inventory: Inventory, stack: ItemStack): Boolean {
            val slot = armorSlot(stack)
            if (slot != null) {
                val worn = armorScore(inventory.getItem(wornIndex(slot)))
                val carried = CARRIED_SLOTS.maxOf { carriedSlot ->
                    val owned = inventory.getItem(carriedSlot)
                    if (armorSlot(owned) == slot) armorScore(owned) else 0.0
                }
                return armorScore(stack) > maxOf(worn, carried)
            }
            val weapon = weaponScore(stack)
            return weapon > 0.0 && weapon > bestWeapon(inventory)
        }

        /**
         * The inventory index [slot] is worn at.
         */
        fun wornIndex(slot: EquipmentSlot): Int = slot.getIndex(Inventory.INVENTORY_SIZE)

        private fun isArmorName(name: String) =
            name.endsWith("_helmet") || name.endsWith("_chestplate") || name.endsWith("_leggings") || name.endsWith("_boots")

        private fun healthOf(entity: LivingEntity): Double {
            val health = entity.health
            return if (health > 0f) (health / entity.maxHealth).toDouble().coerceIn(0.0, 1.0) else 1.0
        }

        private fun name(stack: ItemStack) = stack.itemId

        private fun tier(
            name: String,
            wood: Double,
            gold: Double,
            stone: Double,
            iron: Double,
            diamond: Double,
            netherite: Double,
        ) = when {
            name.startsWith("netherite") -> netherite
            name.startsWith("diamond") -> diamond
            name.startsWith("iron") || name.startsWith("chainmail") -> iron
            name.startsWith("stone") || name.startsWith("copper") -> stone
            name.startsWith("gold") -> gold
            else -> wood
        }

        private fun level(stack: ItemStack, enchantment: ResourceKey<Enchantment>): Int =
            stack.enchantments.entrySet().firstOrNull { it.key.`is`(enchantment) }?.intValue ?: 0
    }
}
