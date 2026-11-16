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
import net.ccbluex.agent.simulation.PlayerSimulation
import net.ccbluex.agent.simulation.SimulatedInput
import net.ccbluex.agent.simulation.SimulatedPlayer
import net.ccbluex.agent.skills.AgentBody
import net.ccbluex.agent.v26_3.toAgent
import net.ccbluex.agent.v26_3.toMinecraft
import net.ccbluex.agent.world.ContainerMenu
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.craftbukkit.block.CraftBlock
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Projectile
import org.bukkit.plugin.Plugin
import kotlin.math.abs
import kotlin.math.max
import net.ccbluex.agent.math.BlockPos as AgentBlockPos
import net.ccbluex.agent.math.Direction as AgentDirection
import net.ccbluex.agent.world.BlockHitResult as AgentBlockHitResult

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

    override val player: net.ccbluex.agent.world.entity.Player
        get() = handle.toAgent()

    /**
     * The level's players and the other living bot bodies in it, which the level keeps out of its player list.
     */
    override fun players(): List<net.ccbluex.agent.world.entity.Player> {
        val level = handle.level()
        val bodies = BotPlayer.spawned.filter { it !== handle && it.level() === level && it.isAlive && !it.isRemoved }
        return (if (bodies.isEmpty()) level.players() else level.players() + bodies).map(Player::toAgent)
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
    override fun press(input: net.ccbluex.agent.world.Input, exact: Boolean) {
        handle.forward = axis(input.forward, input.backward)
        handle.strafe = axis(input.left, input.right)
        handle.jump = input.jump
        handle.isShiftKeyDown = input.shift
        handle.sprintKey = input.sprint
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

    override fun startDestroy(pos: AgentBlockPos, face: AgentDirection): Boolean =
        digging.start(pos.toMinecraft(), face.toMinecraft())

    override fun continueDestroy(pos: AgentBlockPos, face: AgentDirection): Boolean =
        digging.proceed(pos.toMinecraft(), face.toMinecraft())

    override fun stopDestroy() {
        digging.stop()
    }

    override fun useItemOn(hand: net.ccbluex.agent.world.InteractionHand, hit: AgentBlockHitResult): Boolean =
        useItemOn(hand.toMinecraft(), hit.toMinecraft())

    // ServerGamePacketListenerImpl.handleUseItemOn: the server's checks, then the block's use and the item's
    private fun useItemOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
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

    override fun useItem(hand: net.ccbluex.agent.world.InteractionHand): Boolean = useItem(hand.toMinecraft())

    // ServerGamePacketListenerImpl.handleUseItem, with the rotation the body has
    private fun useItem(hand: InteractionHand): Boolean {
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

    override fun simulate(input: SimulatedInput, yRot: Float): PlayerSimulation.Steered =
        SimulatedPlayer.fromPlayer(handle, input, TRAITS.navigatesInMovementTick, simulationHooks)
            .also { it.yRot = yRot }

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

    override fun swing(hand: net.ccbluex.agent.world.InteractionHand) {
        handle.swingHand(hand.toMinecraft())
    }

    /**
     * Hits [target] as a player would with the main hand item: weapon damage and enchantments scaled by the attack
     * strength, sprint knockback, critical hits, the mace smash, a sword's sweep and an axe disabling a shield. Resets
     * the attack strength. True when the target was hurt.
     */
    public fun attack(target: org.bukkit.entity.Entity): Boolean = handle.attackAsPlayer((target as CraftEntity).handle)

    override fun attack(target: net.ccbluex.agent.world.entity.Entity) {
        handle.attackAsPlayer(target.toMinecraft())
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
    override fun startUsingItem(hand: net.ccbluex.agent.world.InteractionHand): Boolean {
        val hand = hand.toMinecraft()
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

    override val containerMenu: ContainerMenu?
        get() = handle.containerMenu.takeIf { it !== handle.inventoryMenu }?.toAgent()

    override fun takeFromContainer(containerSlot: Int) {
        clicks.click(containerSlot, 0, ContainerInput.QUICK_MOVE)
    }

    override fun clickContainer(containerSlot: Int, button: Int, type: net.ccbluex.agent.world.ContainerInput) {
        clicks.click(containerSlot, button, type.toMinecraft())
    }

    override fun closeContainer() {
        if (handle.containerMenu !== handle.inventoryMenu) handle.closeContainer()
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
     * Places [item]'s block against [face] of [against] as a player would, the item put in the main hand first:
     * BlockPlaceEvent fires with the body, so protection plugins see it. False when nothing was placed.
     */
    public fun placeBlock(against: Block, face: BlockFace, item: org.bukkit.inventory.ItemStack): Boolean {
        if (item.type.isAir) return false
        val hit = hitOn(against, face)
        val result = holdInMainHand(item).useOn(UseOnContext(handle, InteractionHand.MAIN_HAND, hit))
        touched += hit.blockPos
        touched += hit.blockPos.relative(hit.direction)
        if (!result.consumesAction()) return false
        handle.swingHand(InteractionHand.MAIN_HAND)
        return true
    }

    /**
     * How much of [block] one tick of digging with the main hand item takes off, as the client counts it; 1 breaks it.
     */
    public fun destroyProgress(block: Block): Float {
        val pos = posOf(block)
        return handle.level().getBlockState(pos).getDestroyProgress(handle, handle.level(), pos)
    }

    /**
     * Shows players the cracks of [block] dug [progress] far, in [0, 1); a negative progress takes them away.
     */
    public fun showDestroyProgress(block: Block, progress: Float) {
        val stage = if (progress < 0f) -1 else minOf(9, (progress * 10).toInt())
        handle.level().destroyBlockProgress(handle.id, posOf(block), stage)
    }

    /**
     * Breaks [block] as a player who dug it out: BlockBreakEvent with the body, the held tool's drops. False when it
     * stays.
     */
    public fun destroyBlock(block: Block): Boolean {
        if (!handle.gameMode.destroyBlock(posOf(block))) return false
        handle.swingHand(InteractionHand.MAIN_HAND)
        return true
    }

    /**
     * Right-clicks [face] of [block] with [item] as a player, the item put in the main hand first: the block's own use,
     * then the item's, such as charging a respawn anchor or putting an end crystal on obsidian. False when nothing
     * happened.
     */
    public fun useItemOn(block: Block, face: BlockFace, item: org.bukkit.inventory.ItemStack): Boolean {
        if (item.type.isAir) return false
        holdInMainHand(item)
        return useItemOn(InteractionHand.MAIN_HAND, hitOn(block, face))
    }

    /**
     * Right-clicks with [item] where the body looks, the item put in the main hand first: a bucket pours, a rod casts,
     * a splash potion is thrown. The item stays in the main hand as the use left it. False when nothing happened.
     */
    public fun useItem(item: org.bukkit.inventory.ItemStack): Boolean {
        if (item.type.isAir) return false
        holdInMainHand(item)
        return useItem(InteractionHand.MAIN_HAND)
    }

    private fun holdInMainHand(item: org.bukkit.inventory.ItemStack): ItemStack {
        val stack = CraftItemStack.asNMSCopy(item)
        handle.setItemInHand(InteractionHand.MAIN_HAND, stack)
        return stack
    }

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

        private fun posOf(block: Block): BlockPos = BlockPos(block.x, block.y, block.z)

        // A click on the middle of the face, as a client sends one
        private fun hitOn(block: Block, face: BlockFace): BlockHitResult {
            val direction = requireNotNull(CraftBlock.blockFaceToNotch(face)) { "Not a block face: $face" }
            val pos = posOf(block)
            val point = Vec3.atCenterOf(pos).add(direction.stepX * 0.5, direction.stepY * 0.5, direction.stepZ * 0.5)
            return BlockHitResult(point, direction, pos, false)
        }

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
