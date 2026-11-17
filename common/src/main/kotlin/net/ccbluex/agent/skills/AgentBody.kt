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
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.ContainerMenu
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.entity.Player

/**
 * What abilities need besides walking. A client acts through its game mode and container clicks, a server directly
 * on the bot player. Inventory slot indices are the ones the game's player inventory uses: 0 to 8 hotbar, 9 to 35 the
 * rest, 36 to 39 armor feet to head, 40 offhand.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public interface AgentBody : Body {

    public fun swing(hand: InteractionHand)

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

    public fun swapWithOffhand(inventorySlot: Int)

    public fun drop(inventorySlot: Int, wholeStack: Boolean)

    /**
     * The open container; null while only the own inventory is open.
     */
    public val containerMenu: ContainerMenu?

    /**
     * Moves the stack in [containerSlot], an index into the slots of [containerMenu], into the own inventory.
     */
    public fun takeFromContainer(containerSlot: Int)

    /**
     * A raw click on [containerMenu], as shop menus on clients need.
     */
    public fun clickContainer(containerSlot: Int, button: Int, type: ContainerInput)

    /**
     * A raw click on the own inventory's menu while no container is open, as its 2 by 2 crafting grid needs; [menuSlot]
     * is an index into the slots of the game's inventory menu. The inventory stays open from the first
     * click until [closeContainer], as a player keeps it open while crafting; closing it gives back what the grid holds.
     */
    public fun clickInventory(menuSlot: Int, button: Int, type: ContainerInput)

    /**
     * The own inventory's menu with its 2 by 2 crafting grid, which [clickInventory] clicks; null for a body that has
     * none.
     */
    public val inventoryMenu: ContainerMenu? get() = null

    public fun closeContainer()
}
