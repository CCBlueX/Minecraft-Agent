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

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.ViewRotation
import net.ccbluex.agent.navigation.LookPurpose
import net.ccbluex.agent.navigation.Turning
import net.ccbluex.agent.skills.AgentBody
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.inventory.ItemStack
import kotlin.math.abs

/**
 * The body of a server bot: a player on the server that clients and anticheats see as any other player, driven by
 * the keys it is given as a client's packets would drive it. It is not online: not in `Bukkit.getOnlinePlayers()` or
 * `World.getPlayers()`, only among its world's entities, with the `NPC` metadata plugins check for NPCs.
 *
 * Besides what a [net.ccbluex.agent.navigation.Navigator] needs, it fights as a player of its game version does.
 *
 * @agent claude-opus-5-5
 */
public abstract class BotBody internal constructor() : AgentBody {

    /**
     * The Bukkit side of the body, for equipment, health and plugins.
     */
    public abstract val entity: Player

    /**
     * How the body turns towards a rotation it looks for, a tick at a time. A bot sets it from its settings'
     * difficulty; [LookPurpose.STEER] while gliding on an elytra skips it.
     */
    public var turning: Turning = Turning.linear(BotSettings.DEFAULT_TURN_SPEED)

    private var lookTarget: ViewRotation? = null
    private var lookPurpose = LookPurpose.VIEW
    private var requested: ViewRotation? = null
    private var requestedPurpose = LookPurpose.VIEW
    private var requestedRank = -1
    private val touched = ArrayList<BlockPos>()

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
        get() = ViewRotation(player.yRot, player.xRot)

    override val latencyMillis: Int
        get() = 0

    /**
     * Holds the body where it is: no movement keys, no gravity, no pushing. Only [moveTo] moves it then.
     */
    public abstract var anchored: Boolean

    /**
     * Whether hits push the body.
     */
    public abstract var knockback: Boolean

    /**
     * Whether the body can die; an immortal body is healed after every hit.
     */
    public abstract var immortal: Boolean

    /**
     * Whether players keep the body's tab list entry, unlisted where the game has unlisted entries. Without, it goes
     * two seconds after a player starts seeing the body, once the skin loaded.
     */
    public abstract var tabEntry: Boolean

    public abstract var isShiftKeyDown: Boolean

    /**
     * Sprints whenever the body runs forward, as a held sprint key; sprinting stops when it stops.
     */
    public abstract var isSprinting: Boolean

    public abstract val isOnGround: Boolean

    /**
     * Fall distance as a player has it, which crits depend on.
     */
    public abstract val fallDistance: Float

    /**
     * Whether a raised shield or, on 1.8, a raised sword blocks hits now.
     */
    public abstract val isBlocking: Boolean

    /**
     * Turns the body at once.
     */
    public fun setRot(yRot: Float, xRot: Float) {
        lookTarget = null
        rotate(yRot, xRot)
    }

    /**
     * Sets the position; an anchored body is held at it from then on. Clients see it walk there.
     */
    public fun moveTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float) {
        lookTarget = null
        teleport(x, y, z, yRot, xRot)
    }

    /**
     * Adds to the body's velocity, as a jump boost or a wind charge does.
     */
    public abstract fun push(x: Double, y: Double, z: Double)

    /**
     * Holds analog movement keys until the next [move] or [press]: [forward] and [strafe] in -1 to 1, [strafe]
     * positive to the left, as a controller would give them; a walking speed below a player's is a [forward] under 1.
     */
    public abstract fun move(forward: Float, strafe: Float, jump: Boolean)

    /**
     * Hits [target] as a player of the game version would with the main hand item. True when the target was hurt.
     */
    public abstract fun attack(target: Entity): Boolean

    /**
     * Lets go of the item at once, as switching away does: a bow does not shoot.
     */
    public abstract fun stopUsingItem()

    /**
     * Throws or fires [item] the way the body looks: ender pearl, snowball, arrow; null for others.
     */
    public abstract fun launch(item: ItemStack): Projectile?

    /**
     * Places [item]'s block against [face] of [against] as a player would, the item put in the main hand first:
     * BlockPlaceEvent fires with the body, so protection plugins see it. False when nothing was placed.
     */
    public abstract fun placeBlock(against: Block, face: BlockFace, item: ItemStack): Boolean

    /**
     * How much of [block] one tick of digging with the main hand item takes off, as the client counts it; 1 breaks it.
     */
    public abstract fun destroyProgress(block: Block): Float

    /**
     * Shows players the cracks of [block] dug [progress] far, in [0, 1); a negative progress takes them away.
     */
    public abstract fun showDestroyProgress(block: Block, progress: Float)

    /**
     * Breaks [block] as a player who dug it out: BlockBreakEvent with the body, the held tool's drops. False when it
     * stays.
     */
    public abstract fun destroyBlock(block: Block): Boolean

    /**
     * Right-clicks [face] of [block] with [item] as a player, the item put in the main hand first: the block's own use,
     * then the item's. False when nothing happened.
     */
    public abstract fun useItemOn(block: Block, face: BlockFace, item: ItemStack): Boolean

    /**
     * Right-clicks with [item] where the body looks, the item put in the main hand first: a bucket pours, a rod casts,
     * a splash potion is thrown. The item stays in the main hand as the use left it. False when nothing happened.
     */
    public abstract fun useItem(item: ItemStack): Boolean

    /**
     * Sets the rotation the clients see.
     */
    internal abstract fun rotate(yRot: Float, xRot: Float)

    internal abstract fun teleport(x: Double, y: Double, z: Double, yRot: Float, xRot: Float)

    internal abstract fun remove()

    /**
     * Whether the rotation goes straight to the target, as while steering an elytra glide.
     */
    internal open val turnsAtOnce: Boolean
        get() = false

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
        val from = sentRotation
        val turned = if (lookPurpose == LookPurpose.STEER && turnsAtOnce) target else turning.turn(from, target, lookPurpose)
        val yRot = from.yRot + Mth.wrapDegrees(turned.yRot - from.yRot)
        val xRot = turned.xRot.coerceIn(-MAX_X_ROT, MAX_X_ROT)
        if (abs(Mth.wrapDegrees(target.yRot - yRot)) < REACHED && abs(target.xRot - xRot) < REACHED) lookTarget = null
        rotate(yRot, xRot)
    }

    /**
     * Remembers a block a use touched, whose state answers a placement the way a client gets it from the server.
     */
    internal fun touched(pos: BlockPos) {
        touched += pos
    }

    internal fun drainTouched(each: (BlockPos) -> Unit) {
        touched.forEach(each)
        touched.clear()
    }

    private companion object {
        const val MAX_X_ROT = 90f
        const val REACHED = 1.0E-3f

        fun LookPurpose.rank(): Int = when (this) {
            LookPurpose.VIEW -> 0
            LookPurpose.MOVEMENT -> 1
            LookPurpose.AIM -> 2
            LookPurpose.STEER -> 3
        }
    }
}
