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

package net.ccbluex.agent.skills

import net.ccbluex.agent.navigation.Body
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

/**
 * What abilities need besides walking. A client acts through its game mode and container clicks, a server directly
 * on the bot player. Inventory slot indices are the ones [net.minecraft.world.entity.player.Inventory] uses: 0 to 8
 * hotbar, 9 to 35 the rest, 36 to 39 armor feet to head, 40 offhand.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public interface AgentBody : Body {

    /**
     * Attacks as a player does: swings and hits.
     */
    public fun attack(target: Entity)

    public fun swing(hand: InteractionHand)

    /**
     * Interacts with [target] as the use key does with an entity in reach: a villager opens its trades, an
     * interaction hitbox or an armor stand answers. False for a body that cannot.
     */
    public fun interact(target: Entity, hand: InteractionHand): Boolean = false

    /**
     * Asks to come back from the death screen, as its button does. False for a body that cannot, as a server bot,
     * which comes back by its own settings.
     */
    public fun respawn(): Boolean = false

    /**
     * Stabs with the spear in the main hand at nothing in particular, as the attack key does with nothing in reach.
     */
    public fun stab()

    /**
     * The players this body can see in its level, bots included. Hosts whose bots are not in the level's player list
     * override it.
     */
    public fun players(): List<Player> = level.players()

    /**
     * Starts using the item in [hand]: eating, drinking, raising a shield, drawing a bow, blocking on 1.8.
     */
    public fun startUsingItem(hand: InteractionHand): Boolean

    public fun releaseUsingItem()

    public val isUsingItem: Boolean

    /**
     * 0 to 1; always 1 under 1.8 rules.
     */
    public val attackStrength: Float

    /**
     * Closest hit distance with the held item.
     */
    public val minAttackReach: Double

    /**
     * Farthest hit distance with the held item.
     */
    public val maxAttackReach: Double

    /**
     * 1.8 rules: no attack cooldown, sword blocking.
     */
    public val isCombat1_8: Boolean

    public fun swapWithHotbar(inventorySlot: Int, hotbarSlot: Int)

    public fun swapWithOffhand(inventorySlot: Int)

    public fun drop(inventorySlot: Int, wholeStack: Boolean)

    /**
     * The open container; null while only the own inventory is open.
     */
    public val containerMenu: AbstractContainerMenu?

    /**
     * Moves the stack in [containerSlot], an index into the slots of [containerMenu], into the own inventory.
     */
    public fun takeFromContainer(containerSlot: Int)

    /**
     * A raw click on [containerMenu], as shop menus on clients need.
     */
    public fun clickContainer(containerSlot: Int, button: Int, type: ContainerInput)

    public fun closeContainer()
}
