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

package net.ccbluex.agent.v26_3.paper

import kotlin.math.abs
import kotlin.math.max
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.BodyTraits
import net.ccbluex.agent.navigation.FlightControl
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.Teleporter
import net.ccbluex.agent.navigation.Turning
import net.ccbluex.agent.bukkit.BotSkin
import net.ccbluex.agent.v26_3.paper.body.BotPlayer
import net.ccbluex.agent.v26_3.paper.body.Digging
import net.ccbluex.agent.v26_3.paper.body.InventoryClicks
import net.ccbluex.agent.v26_3.paper.body.PositionTeleporter
import net.ccbluex.agent.v26_3.paper.body.launch
import net.ccbluex.agent.v26_3.paper.body.spawnBotPlayer
import net.ccbluex.agent.skills.AgentBody
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.phys.BlockHitResult
import org.bukkit.Location
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Projectile
import org.bukkit.plugin.Plugin

/**
 * The body of a server bot: a player on the server that clients and anticheats see as any other player, driven by
 * the keys it is given as a client's packets would drive it. It is not online: not in `Bukkit.getOnlinePlayers()` or
 * `World.getPlayers()`, only among its world's entities, with the `NPC` metadata plugins check for NPCs.
 *
 * Besides what a [net.ccbluex.agent.navigation.Navigator] needs, it fights as a player does: hits, jabs, shields,
 * projectiles.
 *
 * @agent claude-opus-5-5
 */
public class PaperBody internal constructor(internal val handle: BotPlayer) : AgentBody {

    /**
     * The Bukkit side of the body, for equipment, health and plugins.
     */
    public val entity: org.bukkit.entity.Player
        get() = handle.bukkitEntity

    override val player: Player
        get() = handle

    /**
     * The level's players and the other living bot bodies in it, which the level keeps out of its player list.
     */
    override fun players(): List<Player> {
        val level = handle.level()
        val bodies = BotPlayer.spawned.filter { it !== handle && it.level() === level && it.isAlive && !it.isRemoved }
        return if (bodies.isEmpty()) level.players() else level.players() + bodies
    }

    /**
     * How the body turns towards a rotation it looks for, a tick at a time. A bot sets it from its settings'
     * difficulty; [LookPurpose.STEER] while gliding on an elytra skips it.
     */
    public var turning: Turning = Turning.linear(DEFAULT_TURN_SPEED)

    private var lookTarget: ViewRotation? = null
    private var lookPurpose = LookPurpose.VIEW
    private var requested: ViewRotation? = null
    private var requestedPurpose = LookPurpose.VIEW
    private var requestedRank = -1
    private val digging = Digging(handle)
    private val clicks = InventoryClicks(handle)
    private var releasedSlot = -1
    private val touched = ArrayList<BlockPos>()

    /**
     * A server bot has no host features to change keys, so [exact] changes nothing.
     */
    override fun press(input: Input, exact: Boolean) {
        handle.forward = axis(input.forward(), input.backward())
        handle.strafe = axis(input.left(), input.right())
        handle.jump = input.jump()
        handle.isShiftKeyDown = input.shift()
        handle.sprintKey = input.sprint()
    }

    /**
     * The rotation is turned to over the next ticks as [turning] paces it; steering wins over aiming over walking.
     */
    override fun look(rotation: ViewRotation, purpose: LookPurpose) {
        val rank = purpose.rank()
        if (rank >= requestedRank) {
            requested = rotation
            requestedPurpose = purpose
            requestedRank = rank
        }
    }

    override val sentRotation: ViewRotation
        get() = ViewRotation(handle.yRot, handle.xRot)

    override var safeWalk: Boolean
        get() = handle.safeWalk
        set(value) {
            handle.safeWalk = value
        }

    override val latencyMillis: Int
        get() = 0

    override val traits: BodyTraits
        get() = TRAITS

    /**
     * Holds [slot] until [releaseHotbarSlot], which goes back to the slot held before.
     */
    override fun selectHotbarSlot(slot: Int) {
        if (releasedSlot < 0) releasedSlot = handle.inventory.selectedSlot
        holdSlot(slot)
    }

    override fun releaseHotbarSlot() {
        if (releasedSlot < 0) return
        holdSlot(releasedSlot)
        releasedSlot = -1
    }

    // As the server takes a client's held slot change
    private fun holdSlot(slot: Int) {
        val inventory = handle.inventory
        if (inventory.selectedSlot == slot) return
        if (handle.usedItemHand == InteractionHand.MAIN_HAND) handle.stopUsingItem()
        inventory.selectedSlot = slot
    }

    override fun startDestroy(pos: BlockPos, face: Direction): Boolean = digging.start(pos, face)

    override fun continueDestroy(pos: BlockPos, face: Direction): Boolean = digging.proceed(pos, face)

    override fun stopDestroy() {
        digging.stop()
    }

    // ServerGamePacketListenerImpl.handleUseItemOn: the server's checks, then the block's use and the item's
    override fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
        val level = handle.level()
        val stack = handle.getItemInHand(hand)
        val pos = hit.blockPos
        if (!stack.isItemEnabled(level.enabledFeatures()) || !handle.isWithinBlockInteractionRange(pos, 1.0)) {
            return false
        }
        if (pos.y > level.maxY || pos.y < level.minY) return false
        if (level.server.isUnderSpawnProtection(level, pos, handle) || !level.mayInteract(handle, pos)) return false
        val animation = stack.interactAnimation
        val result = handle.gameMode.useItemOn(handle, level, stack, hand, hit)
        touched += pos.immutable()
        touched += pos.relative(hit.direction)
        if (result is InteractionResult.Success && result.shouldSwing()) {
            handle.swingAndResetAttackStrength(hand, animation, false)
        }
        return result.consumesAction()
    }

    // ServerGamePacketListenerImpl.handleUseItem, with the rotation the body has
    override fun useItem(hand: InteractionHand): Boolean {
        val level = handle.level()
        val stack = handle.getItemInHand(hand)
        if (stack.isEmpty || !stack.isItemEnabled(level.enabledFeatures())) return false
        val animation = stack.interactAnimation
        val result = handle.gameMode.useItem(handle, level, stack, hand)
        if (result is InteractionResult.Success && result.shouldSwing()) {
            handle.swingAndResetAttackStrength(hand, animation, false)
        }
        return result.consumesAction()
    }

    /**
     * A server bot flies by creative flight only, when it may fly.
     */
    override val flight: FlightControl?
        get() = null

    override val teleporter: Teleporter = PositionTeleporter(handle)

    /**
     * Holds the body where it is: no movement keys, no gravity, no pushing. Only [moveTo] moves it then.
     */
    public var anchored: Boolean
        get() = handle.anchored
        set(value) {
            handle.setAnchored(value)
        }

    /**
     * Whether hits push the body.
     */
    public var knockback: Boolean
        get() = handle.knockback
        set(value) {
            handle.knockback = value
        }

    /**
     * Whether the body can die; an immortal body is healed after every hit.
     */
    public var immortal: Boolean
        get() = handle.immortal
        set(value) {
            handle.immortal = value
        }

    /**
     * Whether players keep the body's tab list entry, unlisted, as they keep a real player's. Without, it goes two
     * seconds after a player starts seeing the body, once the skin loaded: clients before 1.19.3 list every entry.
     */
    public var tabEntry: Boolean
        get() = handle.tabEntry
        set(value) {
            handle.tabEntry = value
        }

    public var isShiftKeyDown: Boolean
        get() = handle.isShiftKeyDown
        set(value) {
            handle.isShiftKeyDown = value
        }

    /**
     * Sprints whenever the body runs forward, as a held sprint key; sprinting stops when it stops.
     */
    public var isSprinting: Boolean
        get() = handle.isSprinting
        set(value) {
            handle.holdsSprint = value
            if (!value) handle.isSprinting = false
        }

    public val isOnGround: Boolean
        get() = handle.onGround()

    /**
     * Fall distance as a player has it, which crits and the mace smash depend on.
     */
    public val fallDistance: Float
        get() = handle.fallDistance.toFloat()

    /**
     * Turns the body at once.
     */
    public fun setRot(yRot: Float, xRot: Float) {
        lookTarget = null
        handle.look(yRot, xRot)
    }

    /**
     * Sets the position; an anchored body is held at it from then on. Clients see it walk there.
     */
    public fun moveTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float) {
        lookTarget = null
        handle.moveTo(x, y, z, yRot, xRot)
    }

    /**
     * Adds to the body's velocity, as a jump boost or a wind charge does.
     */
    public fun push(x: Double, y: Double, z: Double) {
        handle.addDeltaMovement(x, y, z)
    }

    /**
     * Holds analog movement keys until the next [move] or [press]: [forward] and [strafe] in -1 to 1, [strafe]
     * positive to the left, as a controller would give them; a walking speed below a player's is a [forward] under 1.
     *
     * @agent claude-opus-5-5
     */
    public fun move(forward: Float, strafe: Float, jump: Boolean) {
        handle.forward = forward.coerceIn(-1f, 1f)
        handle.strafe = strafe.coerceIn(-1f, 1f)
        handle.jump = jump
    }

    override fun swing(hand: InteractionHand) {
        handle.swingHand(hand)
    }

    /**
     * Hits [target] as a player would with the main hand item: weapon damage and enchantments scaled by the attack
     * strength, sprint knockback, critical hits, the mace smash, a sword's sweep and an axe disabling a shield. Resets
     * the attack strength. True when the target was hurt.
     */
    public fun attack(target: org.bukkit.entity.Entity): Boolean = handle.attackAsPlayer((target as CraftEntity).handle)

    override fun attack(target: Entity) {
        handle.attackAsPlayer(target)
    }

    /**
     * A spear's jab where the body looks, as the client's stab action (ServerGamePacketListenerImpl): hits whatever
     * is along the main hand item's attack range, then its jab effects such as Lunge. False when it can't jab now.
     */
    public fun jab(): Boolean {
        val item = handle.mainHandItem
        val piercing = item.get(DataComponents.PIERCING_WEAPON) ?: return false
        if (handle.cannotAttackWithItem(item, JAB_COOLDOWN_TICKS)) return false
        piercing.attack(handle, net.minecraft.world.entity.EquipmentSlot.MAINHAND)
        return true
    }

    override fun stab() {
        jab()
    }

    /**
     * Attack strength in [0, 1], as the client's cooldown indicator.
     */
    override val attackStrength: Float
        get() = handle.getAttackStrengthScale(0.5f)

    /**
     * The server's own combat; a plugin that brings 1.8 combat back is told by the fight rules.
     */
    override val isCombat1_8: Boolean
        get() = false

    /**
     * Starts using the item in [hand]: raising a shield, eating, drinking, drawing a bow.
     */
    override fun startUsingItem(hand: InteractionHand): Boolean {
        val item = handle.getItemInHand(hand)
        if (item.isEmpty || handle.isUsingItem || handle.cooldowns.isOnCooldown(item)) return false
        handle.startUsingItem(hand)
        return handle.isUsingItem
    }

    /**
     * Lets go of the item at once, as switching away does: a bow does not shoot.
     */
    public fun stopUsingItem() {
        handle.stopUsingItem()
    }

    /**
     * Lets go of the item as a client releasing the use key: a drawn bow shoots, a trident is thrown.
     */
    override fun releaseUsingItem() {
        handle.releaseUsingItem()
    }

    override val isUsingItem: Boolean
        get() = handle.isUsingItem

    override fun swapWithHotbar(inventorySlot: Int, hotbarSlot: Int) {
        clicks.swapWithHotbar(inventorySlot, hotbarSlot)
    }

    override fun swapWithOffhand(inventorySlot: Int) {
        clicks.swapWithOffhand(inventorySlot)
    }

    override fun drop(inventorySlot: Int, wholeStack: Boolean) {
        clicks.drop(inventorySlot, wholeStack)
    }

    override val containerMenu: AbstractContainerMenu?
        get() = handle.containerMenu.takeIf { it !== handle.inventoryMenu }

    override fun takeFromContainer(containerSlot: Int) {
        clicks.click(containerSlot, 0, ContainerInput.QUICK_MOVE)
    }

    override fun clickContainer(containerSlot: Int, button: Int, type: ContainerInput) {
        clicks.click(containerSlot, button, type)
    }

    override fun closeContainer() {
        if (containerMenu != null) handle.closeContainer()
    }

    /**
     * Whether a raised shield blocks hits from the front now.
     */
    public val isBlocking: Boolean
        get() = handle.isBlocking

    /**
     * The shortest distance from the eyes to a target's hitbox at which a main hand hit lands (AttackRange).
     */
    override val minAttackReach: Double
        get() {
            val range = handle.getAttackRangeWith(handle.mainHandItem)
            return max(0.0, (range.effectiveMinRange(handle) - range.hitboxMargin()).toDouble())
        }

    /**
     * The longest such distance.
     */
    override val maxAttackReach: Double
        get() {
            val range = handle.getAttackRangeWith(handle.mainHandItem)
            return (range.effectiveMaxRange(handle) + range.hitboxMargin()).toDouble()
        }

    /**
     * Ticks a shield stays down after an axe hit; 0 when it can be raised.
     */
    public val shieldCooldown: Int
        get() {
            val cooldowns = handle.cooldowns
            return InteractionHand.entries.maxOf { hand ->
                val item = handle.getItemInHand(hand)
                if (item.has(DataComponents.BLOCKS_ATTACKS)) {
                    cooldowns.getRemainingCooldown(cooldowns.getCooldownGroup(item))
                } else {
                    0
                }
            }
        }

    /**
     * Throws or fires [item] the way the body looks: wind charge, ender pearl, snowball, arrow; null for others.
     */
    public fun launch(item: org.bukkit.inventory.ItemStack): Projectile? =
        handle.launch(CraftItemStack.asNMSCopy(item))?.bukkitEntity as? Projectile

    /**
     * Turns towards the rotation looked for last tick, as a client's rotation reaches the server a tick later.
     */
    internal fun turn() {
        requested?.let {
            lookTarget = it
            lookPurpose = requestedPurpose
        }
        requested = null
        requestedRank = -1
        val target = lookTarget ?: return
        val from = ViewRotation(handle.yRot, handle.xRot)
        val turned = if (lookPurpose == LookPurpose.STEER && handle.isFallFlying) {
            target
        } else {
            turning.turn(from, target, lookPurpose)
        }
        val yRot = from.yRot + Mth.wrapDegrees(turned.yRot - from.yRot)
        val xRot = turned.xRot.coerceIn(-MAX_X_ROT, MAX_X_ROT)
        if (abs(Mth.wrapDegrees(target.yRot - yRot)) < REACHED && abs(target.xRot - xRot) < REACHED) lookTarget = null
        handle.look(yRot, xRot)
    }

    /**
     * The blocks used this tick, whose state answers a placement the way a client gets it from the server.
     */
    internal fun drainTouched(each: (BlockPos) -> Unit) {
        touched.forEach(each)
        touched.clear()
    }

    internal fun remove() {
        // BotPlayer.onRemoval takes the tab entry and the metadata away
        if (!handle.isRemoved) handle.discard()
    }

    internal companion object {
        const val DEFAULT_TURN_SPEED: Float = 40f

        private val TRAITS = BodyTraits(
            jumpClimbsLadders = true,
            exactSimulation = false,
            navigatesInMovementTick = false,
        )
        private const val MAX_X_ROT = 90f
        private const val REACHED = 1.0E-3f
        // ServerGamePacketListenerImpl's attack cooldown check for a stab
        private const val JAB_COOLDOWN_TICKS = 5

        fun spawn(plugin: Plugin, location: Location, name: String, skin: BotSkin?): PaperBody =
            PaperBody(spawnBotPlayer(plugin, location, name, skin))

        private fun axis(positive: Boolean, negative: Boolean): Float = when {
            positive == negative -> 0f
            positive -> 1f
            else -> -1f
        }

        private fun LookPurpose.rank(): Int = when (this) {
            LookPurpose.VIEW -> 0
            LookPurpose.MOVEMENT -> 1
            LookPurpose.AIM -> 2
            LookPurpose.STEER -> 3
        }
    }
}
