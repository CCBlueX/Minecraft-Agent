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

package net.ccbluex.agent.v1_8_8.spigot

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.Direction
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.BodyTraits
import net.ccbluex.agent.navigation.FlightControl
import net.ccbluex.agent.navigation.Teleporter
import net.ccbluex.agent.bukkit.BotBody
import net.ccbluex.agent.v1_8_8.spigot.body.BotPlayer
import net.ccbluex.agent.v1_8_8.spigot.body.Digging
import net.ccbluex.agent.v1_8_8.spigot.body.launch
import net.ccbluex.agent.simulation.PlayerSimulation
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.world.BlockHitResult
import net.ccbluex.agent.world.ContainerInput
import net.ccbluex.agent.world.ContainerMenu
import net.ccbluex.agent.world.InteractionHand
import net.ccbluex.agent.world.Input
import net.ccbluex.agent.world.Slot
import net.ccbluex.agent.world.entity.Player
import net.minecraft.server.v1_8_R3.BlockPosition
import net.minecraft.server.v1_8_R3.EnumDirection
import net.minecraft.server.v1_8_R3.PlayerInventory
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack
import org.bukkit.entity.Projectile
import net.minecraft.server.v1_8_R3.ItemStack as McItemStack

/**
 * The body of a server bot on Spigot 1.8.8: it moves and fights as a 1.8 player, with no off hand or shield, a sword
 * that blocks while used, and hits at full strength every click.
 *
 * @agent claude-opus-5-5
 */
public class SpigotBody internal constructor(internal val handle: BotPlayer) : BotBody() {

    override val entity: org.bukkit.entity.Player
        get() = handle.bukkitEntity

    override val player: Player
        get() = handle.toAgent()

    /**
     * The world's players and the other living bot bodies in it, which the world keeps out of its player list.
     */
    override fun players(): List<Player> {
        val world = handle.world
        val bodies = BotPlayer.spawned.filter { it !== handle && it.world === world && it.isAlive }
        return (world.players + bodies).map { it.toAgent() }
    }

    private val digging = Digging(handle)
    private var releasedSlot = -1

    override fun press(input: Input, exact: Boolean) {
        handle.forward = axis(input.forward, input.backward)
        handle.strafe = axis(input.left, input.right)
        handle.jump = input.jump
        handle.isSneaking = input.shift
        handle.holdsSprint = input.sprint
    }

    override val sentRotation: ViewRotation
        get() = ViewRotation(handle.yaw, handle.pitch)

    override var safeWalk: Boolean
        get() = handle.safeWalk
        set(value) {
            handle.safeWalk = value
        }

    override val traits: BodyTraits
        get() = TRAITS

    override fun selectHotbarSlot(slot: Int) {
        if (releasedSlot < 0) releasedSlot = handle.inventory.itemInHandIndex
        holdSlot(slot)
    }

    override fun releaseHotbarSlot() {
        if (releasedSlot < 0) return
        holdSlot(releasedSlot)
        releasedSlot = -1
    }

    // As the server takes a client's held slot change: an item in use is let go
    private fun holdSlot(slot: Int) {
        require(slot in 0 until PlayerInventory.getHotbarSize()) { "Not a hotbar slot: $slot" }
        if (handle.inventory.itemInHandIndex == slot) return
        if (handle.bS()) handle.bV()
        handle.inventory.itemInHandIndex = slot
    }

    override fun startDestroy(pos: BlockPos, face: Direction): Boolean = digging.start(pos.toMinecraft(), face.toMinecraft())

    override fun continueDestroy(pos: BlockPos, face: Direction): Boolean =
        digging.proceed(pos.toMinecraft(), face.toMinecraft())

    override fun stopDestroy() {
        digging.stop()
    }

    /**
     * A 1.8 client's block click: the block's use, then the item's, with the events Spigot fires for them.
     */
    override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
        if (hand != InteractionHand.MAIN_HAND) return false
        val pos = hit.blockPos
        val location = hit.location
        val used = handle.playerInteractManager.interact(
            handle, handle.world, handle.inventory.itemInHand, pos.toMinecraft(), hit.direction.toMinecraft(),
            (location.x - pos.x).toFloat(), (location.y - pos.y).toFloat(), (location.z - pos.z).toFloat(),
        )
        touched(pos)
        touched(pos.relative(hit.direction))
        if (used) handle.bw()
        return used
    }

    /**
     * A 1.8 client's click into the air: eating, drinking, raising a sword, drawing a bow, throwing.
     */
    override fun useItem(hand: InteractionHand): Boolean {
        if (hand != InteractionHand.MAIN_HAND) return false
        val stack = handle.inventory.itemInHand ?: return false
        return handle.playerInteractManager.useItem(handle, handle.world, stack)
    }

    /**
     * Not offered: the walk predicts a 1.8 body with the lighter 1.8 model, as [BodyTraits.exactSimulation] is off.
     */
    override fun simulate(input: SimulatedInput, yRot: Float): PlayerSimulation.Steered =
        throw UnsupportedOperationException("A 1.8 body is predicted with PlayerMotion")

    override val flight: FlightControl?
        get() = null

    override val teleporter: Teleporter?
        get() = null

    override var anchored: Boolean
        get() = handle.anchored
        set(value) {
            handle.anchored = value
        }

    override var knockback: Boolean
        get() = handle.knockback
        set(value) {
            handle.knockback = value
        }

    override var immortal: Boolean
        get() = handle.immortal
        set(value) {
            handle.immortal = value
        }

    /**
     * Whether players keep the body's tab list entry. A 1.8 client lists every entry it has, so without one the body
     * leaves the tab list two seconds after a player starts seeing it, once the skin loaded.
     */
    override var tabEntry: Boolean
        get() = handle.tabEntry
        set(value) {
            handle.tabEntry = value
        }

    override var isShiftKeyDown: Boolean
        get() = handle.isSneaking
        set(value) {
            handle.isSneaking = value
        }

    override var isSprinting: Boolean
        get() = handle.isSprinting
        set(value) {
            handle.holdsSprint = value
            if (!value) handle.isSprinting = false
        }

    override val isOnGround: Boolean
        get() = handle.onGround

    override val fallDistance: Float
        get() = handle.fallDistance

    override fun rotate(yRot: Float, xRot: Float) {
        handle.look(yRot, xRot)
    }

    override fun teleport(x: Double, y: Double, z: Double, yRot: Float, xRot: Float) {
        handle.moveTo(x, y, z, yRot, xRot)
    }

    override fun push(x: Double, y: Double, z: Double) {
        handle.motX += x
        handle.motY += y
        handle.motZ += z
        handle.velocityChanged = true
    }

    override fun move(forward: Float, strafe: Float, jump: Boolean) {
        handle.forward = forward.coerceIn(-1f, 1f)
        handle.strafe = strafe.coerceIn(-1f, 1f)
        handle.jump = jump
    }

    override fun swing(hand: InteractionHand) {
        handle.bw()
    }

    /**
     * Hits [target] as a 1.8 player: the main hand item's damage and enchantments, sprint knockback and critical hits
     * while falling. True when the target was hurt.
     */
    override fun attack(target: org.bukkit.entity.Entity): Boolean = handle.attackAsPlayer((target as CraftEntity).handle)

    override fun attack(target: net.ccbluex.agent.world.entity.Entity) {
        handle.attackAsPlayer((target as EntityView).handle)
    }

    /**
     * 1.8 has no jabs.
     */
    override fun stab() {}

    /**
     * Always full: 1.8 has no attack cooldown.
     */
    override val attackStrength: Float
        get() = 1f

    override val isCombat1_8: Boolean
        get() = true

    /**
     * Starts using the main hand item: eating, drinking, drawing a bow, or raising a sword, which blocks while held.
     */
    override fun startUsingItem(hand: InteractionHand): Boolean {
        if (hand != InteractionHand.MAIN_HAND || handle.bS()) return false
        useItem(hand)
        return handle.bS()
    }

    override fun stopUsingItem() {
        handle.bV()
    }

    /**
     * Lets go of the item as a client releasing the use key: a drawn bow shoots.
     */
    override fun releaseUsingItem() {
        handle.bU()
    }

    override val isUsingItem: Boolean
        get() = handle.bS()

    override fun swapWithHotbar(inventorySlot: Int, hotbarSlot: Int) {
        require(hotbarSlot in 0 until PlayerInventory.getHotbarSize()) { "Not a hotbar slot: $hotbarSlot" }
        ownClick(inventorySlot, hotbarSlot, ContainerInput.SWAP)
    }

    /**
     * 1.8 has no off hand.
     */
    override fun swapWithOffhand(inventorySlot: Int) {}

    override fun drop(inventorySlot: Int, wholeStack: Boolean) {
        ownClick(inventorySlot, if (wholeStack) 1 else 0, ContainerInput.THROW)
    }

    private fun ownClick(inventorySlot: Int, button: Int, type: ContainerInput) {
        val container = handle.defaultContainer
        container.clickItem(menuSlot(inventorySlot), button, type.ordinal, handle)
        container.b()
    }

    override val containerMenu: ContainerMenu?
        get() {
            val container = handle.activeContainer.takeIf { it !== handle.defaultContainer } ?: return null
            return object : ContainerMenu {
                override val slots: List<Slot> = container.c.map { slot ->
                    object : Slot {
                        override val index: Int get() = slot.rawSlotIndex
                        override val item get() = StackView.of(slot.item)
                        override val isPlayerInventory: Boolean get() = slot.inventory is PlayerInventory
                    }
                }
            }
        }

    override fun takeFromContainer(containerSlot: Int) {
        clickContainer(containerSlot, 0, ContainerInput.QUICK_MOVE)
    }

    // 1.8's click modes are ContainerInput's constants in order
    override fun clickContainer(containerSlot: Int, button: Int, type: ContainerInput) {
        val container = handle.activeContainer
        if (containerSlot !in container.c.indices) return
        container.clickItem(containerSlot, button, type.ordinal, handle)
        container.b()
    }

    override fun closeContainer() {
        if (handle.activeContainer !== handle.defaultContainer) handle.closeInventory()
    }

    /**
     * Whether a raised sword blocks hits now, halving what they do.
     */
    override val isBlocking: Boolean
        get() = handle.isBlocking

    override val minAttackReach: Double
        get() = 0.0

    override val maxAttackReach: Double
        get() = ATTACK_REACH

    override fun launch(item: org.bukkit.inventory.ItemStack): Projectile? =
        handle.launch(CraftItemStack.asNMSCopy(item))?.bukkitEntity as? Projectile

    /**
     * Places [item]'s block against [face] of [against] as a 1.8 player would, the item put in the main hand first:
     * BlockPlaceEvent fires with the body, so protection plugins see it. False when nothing was placed.
     */
    override fun placeBlock(against: Block, face: BlockFace, item: org.bukkit.inventory.ItemStack): Boolean =
        useItemOn(against, face, item)

    override fun destroyProgress(block: Block): Float {
        val pos = BlockPosition(block.x, block.y, block.z)
        return handle.world.getType(pos).block.getDamage(handle, handle.world, pos)
    }

    override fun showDestroyProgress(block: Block, progress: Float) {
        val stage = if (progress < 0f) -1 else minOf(MAX_STAGE, (progress * (MAX_STAGE + 1)).toInt())
        handle.world.c(handle.id, BlockPosition(block.x, block.y, block.z), stage)
    }

    override fun destroyBlock(block: Block): Boolean {
        if (!handle.playerInteractManager.breakBlock(BlockPosition(block.x, block.y, block.z))) return false
        handle.bw()
        return true
    }

    override fun useItemOn(block: Block, face: BlockFace, item: org.bukkit.inventory.ItemStack): Boolean {
        if (item.type == org.bukkit.Material.AIR) return false
        holdInMainHand(item)
        val pos = BlockPos(block.x, block.y, block.z)
        val direction = Direction.valueOf(face.name)
        // A click on the middle of the face, as a client sends one
        val point = Vec3.atCenterOf(pos).add(direction.stepX * 0.5, direction.stepY * 0.5, direction.stepZ * 0.5)
        return useItemOn(InteractionHand.MAIN_HAND, BlockHitResult(point, direction, pos, false))
    }

    override fun useItem(item: org.bukkit.inventory.ItemStack): Boolean {
        if (item.type == org.bukkit.Material.AIR) return false
        holdInMainHand(item)
        return useItem(InteractionHand.MAIN_HAND)
    }

    private fun holdInMainHand(item: org.bukkit.inventory.ItemStack): McItemStack {
        val stack = CraftItemStack.asNMSCopy(item)
        handle.inventory.setItem(handle.inventory.itemInHandIndex, stack)
        return stack
    }

    override fun remove() {
        if (!handle.dead) handle.die()
        handle.world.removeEntity(handle)
    }

    private companion object {
        val TRAITS = BodyTraits(jumpClimbsLadders = false, exactSimulation = false, navigatesInMovementTick = false)
        const val ATTACK_REACH = 3.0
        const val MAX_STAGE = 9
        const val HOTBAR_START = 36
        const val FEET = 36
        const val HEAD = 39
        const val FEET_SLOT = 8

        // ContainerPlayer lists the armor head first and the hotbar after the rest of the inventory
        fun menuSlot(inventorySlot: Int): Int = when (inventorySlot) {
            in 0 until PlayerInventory.getHotbarSize() -> HOTBAR_START + inventorySlot
            in PlayerInventory.getHotbarSize() until FEET -> inventorySlot
            in FEET..HEAD -> FEET_SLOT - (inventorySlot - FEET)
            else -> throw IllegalArgumentException("Not an inventory slot: $inventorySlot")
        }

        fun axis(positive: Boolean, negative: Boolean): Float = when {
            positive == negative -> 0f
            positive -> 1f
            else -> -1f
        }

        fun BlockPos.toMinecraft(): BlockPosition = BlockPosition(x, y, z)

        fun Direction.toMinecraft(): EnumDirection = EnumDirection.valueOf(name)
    }
}
