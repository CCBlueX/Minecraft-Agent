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

import com.mojang.authlib.GameProfile
import net.minecraft.server.v1_8_R3.Block
import net.minecraft.server.v1_8_R3.BlockPosition
import net.minecraft.server.v1_8_R3.DamageSource
import net.minecraft.server.v1_8_R3.Entity
import net.minecraft.server.v1_8_R3.EntityLiving
import net.minecraft.server.v1_8_R3.EntityPlayer
import net.minecraft.server.v1_8_R3.PlayerInteractManager
import net.minecraft.server.v1_8_R3.WorldServer
import net.minecraft.server.v1_8_R3.WorldSettings.EnumGamemode
import org.bukkit.plugin.Plugin

/**
 * A 1.8 player on the server that no client controls, set up as Citizens sets up its player NPCs: an empty
 * connection, never in the player list, and taken out of its world's player list once added. The server runs its
 * player tick (`l()`, which a client's packets drive for a real player) every tick, moving it by the keys the bot
 * holds, as a 1.8 client moves its player.
 *
 * @agent claude-opus-5-5
 */
internal class BotPlayer(world: WorldServer, profile: GameProfile, private val plugin: Plugin) :
    EntityPlayer(world.minecraftServer, world, profile, PlayerInteractManager(world)) {

    var forward = 0f
    var strafe = 0f
    var jump = false
    var holdsSprint = false
    var safeWalk = false
    var anchored = false
        set(value) {
            field = value
            if (value) anchor()
        }
    var knockback = false
    var immortal = true
    var tabEntry = true
    var tracked = false

    private var anchorX = 0.0
    private var anchorY = 0.0
    private var anchorZ = 0.0
    private var moving = false

    /**
     * The velocity a client would take from the server's velocity packet, applied as the next tick starts.
     */
    private var knockedX = Double.NaN
    private var knockedY = 0.0
    private var knockedZ = 0.0

    init {
        BotConnection.connect(world.minecraftServer, this)
        playerInteractManager.gameMode = EnumGamemode.SURVIVAL
        // A joining player's spawn protection, which keeps hits off
        invulnerableTicks = 0
        // Entity.stepHeight: EntityPlayer zeroes it, since a real player's client does the stepping
        S = STEP_HEIGHT
        dataWatcher.watch(SKIN_PARTS, ALL_SKIN_PARTS)
        bukkitEntity = BotCraftPlayer(world.server, this)
    }

    // Entity.isSpectatedByPlayer: the tracker shows the body only once Tracking, which sends the player info first,
    // has replaced its entry
    override fun a(viewer: EntityPlayer?): Boolean = tracked && super.a(viewer)

    // Entity.onUpdate; EntityPlayer.onUpdateEntity (l) is what a real player's move packets run
    override fun t_() {
        if (!knockedX.isNaN()) {
            if (knockback && !anchored) {
                motX = knockedX
                motY = knockedY
                motZ = knockedZ
            }
            knockedX = Double.NaN
        }
        super.t_()
        if (dead) return
        l()
        if (anchored) {
            setPosition(anchorX, anchorY, anchorZ)
            motX = 0.0
            motY = 0.0
            motZ = 0.0
        }
    }

    /**
     * Takes the velocity the server sent this player's client, which a 1.8 attack takes back from the server's own
     * copy of the player.
     */
    fun knocked(x: Double, y: Double, z: Double) {
        knockedX = x
        knockedY = y
        knockedZ = z
    }

    fun look(yaw: Float, pitch: Float) {
        this.yaw = yaw
        this.pitch = pitch
        aK = yaw
        aI = yaw
    }

    fun moveTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        setLocation(x, y, z, yaw, pitch)
        look(yaw, pitch)
        if (anchored) anchor()
    }

    private fun anchor() {
        anchorX = locX
        anchorY = locY
        anchorZ = locZ
    }

    // EntityPlayer.updateEntityActionState, called by onLivingUpdate (m): the move inputs for this tick, slowed as
    // EntityPlayerSP slows them while using an item (a blocking sword) and while sneaking
    override fun doTick() {
        super.doTick()
        if (anchored) {
            aZ = 0f
            ba = 0f
            aY = false
            return
        }
        var strafe = strafe
        var forward = forward
        // EntityHuman.isUsingItem
        if (bS()) {
            strafe *= USING_ITEM_SLOWDOWN
            forward *= USING_ITEM_SLOWDOWN
        }
        if (isSneaking) {
            strafe *= SNEAK_SLOWDOWN
            forward *= SNEAK_SLOWDOWN
        }
        // EntityPlayerSP.onLivingUpdate: sprinting starts on a held key while running forward fed, and stops with it
        val canSprint = forward >= SPRINT_FORWARD && !bS() && !isSneaking && foodData.foodLevel > SPRINT_FOOD
        if (holdsSprint && canSprint && !isSprinting) isSprinting = true
        if (isSprinting && (forward < SPRINT_FORWARD || positionChanged || bS() || isSneaking)) isSprinting = false
        aZ = strafe
        ba = forward
        aY = jump
    }

    // Entity.moveEntity holds a sneaking player back at edges; safe walk does it without the sneak others see
    override fun move(x: Double, y: Double, z: Double) {
        moving = true
        try {
            super.move(x, y, z)
        } finally {
            moving = false
        }
    }

    override fun isSneaking(): Boolean = super.isSneaking() || moving && safeWalk

    // Entity.updateFallState: EntityPlayer leaves falls to its client's move packets; the server simulates this one
    override fun a(y: Double, onGround: Boolean, block: Block?, position: BlockPosition?) = a(y, onGround)

    // Entity.canBePushed
    override fun ae(): Boolean = knockback && !anchored && super.ae()

    // EntityLivingBase.knockBack
    override fun a(attacker: Entity?, damage: Float, x: Double, z: Double) {
        if (knockback && !anchored) super.a(attacker, damage, x, z)
    }

    // Entity.addVelocity
    override fun g(x: Double, y: Double, z: Double) {
        if (knockback && !anchored) super.g(x, y, z)
    }

    override fun damageEntity(source: DamageSource?, amount: Float): Boolean =
        super.damageEntity(source, amount).also { if (immortal) health = maxHealth }

    override fun die(source: DamageSource?) {
        if (immortal) health = maxHealth else super.die(source)
    }

    override fun die() {
        super.die()
        bukkitEntity.removeMetadata(NPC_METADATA, plugin)
        spawned -= this
    }

    /**
     * Hits [target] as a 1.8 player does: the swing, then the attack. True when it was hurt.
     */
    fun attackAsPlayer(target: Entity): Boolean {
        bw()
        val living = target as? EntityLiving
        val health = living?.health ?: 0f
        val hurtTicks = living?.hurtTicks ?: 0
        attack(target)
        return living != null && (living.health < health || living.hurtTicks > hurtTicks)
    }

    companion object {
        const val NPC_METADATA = "NPC"

        /**
         * The bodies alive in some world, which the worlds keep out of their player lists.
         */
        val spawned: MutableSet<BotPlayer> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

        private const val STEP_HEIGHT = 0.6f
        private const val USING_ITEM_SLOWDOWN = 0.2f
        private const val SNEAK_SLOWDOWN = 0.3f
        private const val SPRINT_FORWARD = 0.8f
        private const val SPRINT_FOOD = 6
        private const val ALL_SKIN_PARTS: Byte = 0x7F

        // EntityHuman's data watcher entry for the shown skin layers
        private const val SKIN_PARTS = 10
    }
}
