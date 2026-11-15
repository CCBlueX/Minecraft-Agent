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

import com.mojang.authlib.GameProfile
import io.papermc.paper.event.entity.EntityKnockbackEvent
import java.util.EnumSet
import java.util.UUID
import net.ccbluex.agent.bukkit.body.Reflect
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerPlayerGameMode
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.component.UseEffects
import net.minecraft.world.level.GameType
import net.minecraft.world.level.portal.TeleportTransition
import net.minecraft.world.phys.Vec3
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.metadata.MetadataStoreBase

/**
 * A player on the server that no client controls, set up as Citizens sets up its player NPCs: an empty connection,
 * never in the player list, and taken out of its level's player list once added, so it loads no chunks and takes no
 * part in sleeping, mob spawning or mob targeting. The server runs its player tick (`doTick`, which a client's
 * packets drive for a real player) every tick, moving it by the keys the bot holds.
 *
 * @agent claude-opus-5-5
 */
internal class BotPlayer(level: ServerLevel, profile: GameProfile) :
    ServerPlayer(level.server, level, profile, ClientInformation.createDefault()) {

    var anchored: Boolean = false
        private set
    var knockback: Boolean = false
    var immortal: Boolean = true

    // Keys as a client holds them: forward and strafe in [-1, 1], strafe positive to the left
    var forward: Float = 0f
    var strafe: Float = 0f
    var jump: Boolean = false
    var sprintKey: Boolean = false
    var holdsSprint: Boolean = false
    var safeWalk: Boolean = false

    private val moveEvents = MoveEvents(this)

    private var anchor: Vec3? = null
    private var resyncVelocity = false
    private var deadTicks = 0
    private var wasJumping = false
    private var attackLanded = false
    private var fullyBlocked = false
    private val tabRemovals = HashMap<ServerPlayer, Int>()

    // Unlisted, current clients show no tab entry either way; clients before 1.19.3 list every entry. A body spawns
    // seen by the players around already, whose entries go as well
    var tabEntry: Boolean = true
        set(value) {
            field = value
            if (value) {
                tabRemovals.clear()
            } else {
                for (viewer in level().server.playerList.players) tabRemovals[viewer] = tickCount + TAB_ENTRY_TICKS
            }
        }

    init {
        connection = BotConnection.connect(level.server, this)
        Reflect.invoke(SET_GAME_MODE, gameMode, GameType.SURVIVAL, null)
        getAdvancements().clearTriggers()
        getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, ALL_SKIN_PARTS)
    }

    fun leavePlayerList() {
        val level = level()
        level.players().remove(this)
        Reflect.invoke(UPDATE_PLAYER_STATUS, level.chunkSource.chunkMap, this, false)
    }

    /**
     * Sends [viewer] the info entry its client needs to spawn the body, and takes it away again once the skin loaded
     * when the body has no [tabEntry].
     */
    fun trackedBy(viewer: ServerPlayer) {
        viewer.connection.send(infoPacket())
        if (!tabEntry) tabRemovals[viewer] = tickCount + TAB_ENTRY_TICKS
    }

    private fun infoPacket(): ClientboundPlayerInfoUpdatePacket {
        val profile = gameProfile
        val entry = ClientboundPlayerInfoUpdatePacket.Entry(
            profile.id(), profile, false, 0, GameType.SURVIVAL, null, true, 0, null,
        )
        return ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ),
            entry,
        )
    }

    override fun tick() {
        // Cleared on the hit so that the attacker's Player.attack keeps the knockback; the tracker sends it now
        if (resyncVelocity) {
            resyncVelocity = false
            syncVelocity = true
        }
        super.tick()
        if (isRemoved) return
        if (tabRemovals.isNotEmpty()) removeTabEntries()
        // A client's teleport confirmation ends the change of dimension, until which the player takes no damage
        if (isChangingDimension) hasChangedDimension()
        doTick()
        moveEvents.tick()
        // LocalPlayer lands from creative flight on the ground
        val abilities = getAbilities()
        if (onGround() && abilities.flying && !isSpectator) {
            abilities.flying = false
            onUpdateAbilities()
        }
        val anchor = anchor
        if (anchor != null && position() != anchor) {
            setPos(anchor)
            deltaMovement = Vec3.ZERO
        }
        if (isDeadOrDying && ++deadTicks >= CORPSE_TICKS && !isRemoved) {
            discard()
        }
    }

    private fun removeTabEntries() {
        val packet = ClientboundPlayerInfoRemovePacket(listOf(getUUID()))
        val due = tabRemovals.entries.iterator()
        while (due.hasNext()) {
            val (viewer, at) = due.next()
            if (at > tickCount) continue
            if (!viewer.hasDisconnected()) viewer.connection.send(packet)
            due.remove()
        }
    }

    /**
     * Plugins drop what they keep of a player when it quits, which a body never does, and the metadata and maps they
     * hold it in would keep its world, every chunk of it, once the world unloads. A move to another world keeps the
     * body. Every removal ends here, chunk and world unloads too.
     *
     * Minigames hide a body from the players outside its arena and show it again once it leaves, a few ticks after it
     * went: showing a hidden player sends its tab list entry, listed, which nothing would take away again.
     */
    @Suppress("UNCHECKED_CAST")
    override fun onRemoval(reason: RemovalReason) {
        super.onRemoval(reason)
        if (reason == RemovalReason.CHANGED_DIMENSION) return
        spawned -= this
        tabRemovals.clear()
        val id = getUUID()
        val server = level().server
        val packet = ClientboundPlayerInfoRemovePacket(listOf(id))
        for (viewer in server.playerList.players) {
            (HIDDEN.get(viewer.bukkitEntity) as MutableMap<UUID, *>).remove(id)
            viewer.connection.send(packet)
        }
        val store = (Bukkit.getServer() as CraftServer).playerMetadata
        val prefix = "$id:"
        synchronized(store) {
            (METADATA.get(store) as MutableMap<String, *>).keys.removeIf { it.startsWith(prefix) }
        }
        setServerLevel(server.overworld())
    }

    /**
     * A player changes worlds through its client's respawn, which a body has none of. It moves over as the same
     * entity instead, added to the new world as at its spawn: loading no chunks and out of the world's player list.
     */
    override fun teleport(transition: TeleportTransition): ServerPlayer? {
        val target = transition.newLevel()
        val old = level()
        if (isRemoved || target === old) return super.teleport(transition)
        old.removePlayerImmediately(this, RemovalReason.CHANGED_DIMENSION)
        unsetRemoved()
        setServerLevel(target)
        val position = transition.position()
        snapTo(position.x, position.y, position.z, transition.yRot(), transition.xRot())
        deltaMovement = Vec3.ZERO
        if (anchored) anchor = position()
        withoutViewDistance(target) { target.addDuringTeleport(this) }
        leavePlayerList()
        return this
    }

    fun setAnchored(anchored: Boolean) {
        this.anchored = anchored
        anchor = if (anchored) position() else null
        isNoGravity = anchored
        deltaMovement = Vec3.ZERO
    }

    fun moveTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float) {
        if (anchored) anchor = Vec3(x, y, z)
        setPos(x, y, z)
        look(yRot, xRot)
    }

    fun look(yRot: Float, xRot: Float) {
        setYRot(yRot)
        setXRot(xRot)
        setYHeadRot(yRot)
        setYBodyRot(yRot)
    }

    fun addDeltaMovement(x: Double, y: Double, z: Double) {
        deltaMovement = deltaMovement.add(x, y, z)
        needsSync = true
    }

    override fun isImmobile(): Boolean = anchored || super.isImmobile()

    // A real player's client moves it and reports its falls; the server simulates this one, falls included
    override fun isClientAuthoritative(): Boolean = false

    // Sneaking holds a player at ledges; safe walk holds the bot there without sneaking
    override fun isStayingOnGroundSurface(): Boolean = safeWalk || super.isStayingOnGroundSurface()

    // LocalPlayer's input handling: diagonal input normalised, then slowed while using an item and while sneaking
    override fun serverAiStep() {
        super.serverAiStep()
        var strafe = strafe
        var forward = forward
        val length = Mth.sqrt(strafe * strafe + forward * forward)
        if (length > 1) {
            strafe /= length
            forward /= length
        }
        updateSprint()
        if (isUsingItem && !isPassenger) {
            val effects = getUseItem().getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT)
            strafe *= effects.speedMultiplier()
            forward *= effects.speedMultiplier()
            if (!effects.canSprint()) isSprinting = false
        }
        if (isShiftKeyDown) {
            val sneaking = getAttributeValue(Attributes.SNEAKING_SPEED).toFloat()
            strafe *= sneaking
            forward *= sneaking
            isSprinting = false
        }
        if (forward <= 0) isSprinting = false
        xxa = strafe
        zza = forward
        flyByKeys()
        setJumping(jump)
        wasJumping = jump
    }

    // LocalPlayer's sprint rules: the sprint key starts it; no forward key, no food or a wall ahead end it
    private fun updateSprint() {
        val forwardImpulse = forward > FORWARD_IMPULSE
        val possible = !isMobilityRestricted &&
            (vehicle?.canSprint() ?: hasEnoughFoodToDoExhaustiveManoeuvres()) &&
            (getAbilities().flying || !isInShallowWater)
        if (isSprinting) {
            if (!isSwimming && (!possible || !forwardImpulse || horizontalCollision && !minorHorizontalCollision)) {
                isSprinting = false
            }
            return
        }
        val slowedByItem = isUsingItem &&
            !getUseItem().getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).canSprint()
        if ((sprintKey || holdsSprint) && forwardImpulse && possible && !slowedByItem &&
            (!isFallFlying || isUnderWater) && (!isCrouching && !isVisuallyCrawling || isUnderWater)
        ) {
            isSprinting = true
        }
    }

    // LocalPlayer's flight keys: a double jump toggles creative flight, jump and sneak climb and sink, a jump in the
    // air opens an elytra
    private fun flyByKeys() {
        val abilities = getAbilities()
        val pressed = jump && !wasJumping
        var toggled = false
        if (abilities.mayfly) {
            if (isSpectator) {
                if (!abilities.flying) {
                    abilities.flying = true
                    toggled = true
                    onUpdateAbilities()
                }
            } else if (pressed) {
                if (jumpTriggerTime == 0) {
                    jumpTriggerTime = FLIGHT_TOGGLE_TICKS
                } else if (!isSwimming && vehicle == null) {
                    abilities.flying = !abilities.flying
                    if (abilities.flying && onGround()) jumpFromGround()
                    toggled = true
                    onUpdateAbilities()
                    jumpTriggerTime = 0
                }
            }
        }
        if (pressed && !toggled && !onClimbable()) tryToStartFallFlying()
        if (abilities.flying) {
            val climb = (if (jump) 1 else 0) - (if (isShiftKeyDown) 1 else 0)
            if (climb != 0) {
                deltaMovement = deltaMovement.add(0.0, climb * abilities.flyingSpeed * FLIGHT_CLIMB.toDouble(), 0.0)
            }
        }
    }

    override fun knockback(
        strength: Double,
        x: Double,
        z: Double,
        source: DamageSource,
        damage: Float,
        extra: Boolean,
        attacker: Entity?,
        cause: EntityKnockbackEvent.Cause,
    ) {
        if (knockback && !anchored) super.knockback(strength, x, z, source, damage, extra, attacker, cause)
    }

    override fun isPushable(): Boolean = knockback && !anchored && super.isPushable()

    override fun push(entity: Entity) {
        if (isPushable()) super.push(entity)
    }

    override fun push(x: Double, y: Double, z: Double) {
        if (isPushable()) super.push(x, y, z)
    }

    override fun push(x: Double, y: Double, z: Double, pushingEntity: Entity?) {
        if (isPushable()) super.push(x, y, z, pushingEntity)
    }

    // Explosions are movement, not hits: a wind charge boosts the body even with knockback off
    override fun pushFromExplosion(impulse: Vec3) {
        if (!anchored) addDeltaMovement(impulse.x, impulse.y, impulse.z)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val outer = fullyBlocked
        fullyBlocked = false
        val hurt = try {
            super.hurtServer(level, source, amount)
        } finally {
            fullyBlocked = outer
        }
        if (hurt && syncVelocity) {
            syncVelocity = false
            resyncVelocity = true
        }
        if (immortal) health = maxHealth
        return hurt
    }

    @Suppress("DEPRECATION")
    override fun actuallyHurt(level: ServerLevel, source: DamageSource, dmg: Float, event: EntityDamageEvent): Boolean {
        val hurt = super.actuallyHurt(level, source, dmg, event)
        fullyBlocked = hurt && isFullyBlocked(dmg, event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING))
        return hurt
    }

    // LivingEntity.hurtServer plays the hurt sounds of a hit the shield took whole too
    override fun playHurtSound(source: DamageSource) {
        if (!fullyBlocked) super.playHurtSound(source)
    }

    override fun playSecondaryHurtSound(source: DamageSource) {
        if (!fullyBlocked) super.playSecondaryHurtSound(source)
    }

    override fun die(source: DamageSource) {
        if (immortal) {
            health = maxHealth
            return
        }
        super.die(source)
    }

    // Player.attack marks the target with this once it is hurt
    override fun setLastHurtMob(target: Entity) {
        super.setLastHurtMob(target)
        attackLanded = true
    }

    fun attackAsPlayer(target: Entity): Boolean {
        swingHand(InteractionHand.MAIN_HAND)
        attackLanded = false
        attack(target)
        return attackLanded
    }

    // As a client's swing packet does
    fun swingHand(hand: InteractionHand) {
        swing(hand, getItemInHand(hand).attackAnimation, false)
    }

    companion object {
        const val NPC_METADATA: String = "NPC"

        /**
         * Every body in a level, which the levels keep out of their player lists. Server thread only.
         */
        val spawned: MutableSet<BotPlayer> = LinkedHashSet()

        private val SET_GAME_MODE = Reflect.method(
            ServerPlayerGameMode::class.java, "setGameModeForPlayer", GameType::class.java, GameType::class.java,
        )
        private val UPDATE_PLAYER_STATUS = Reflect.method(
            ChunkMap::class.java, "updatePlayerStatus", ServerPlayer::class.java, java.lang.Boolean.TYPE,
        )
        @Suppress("DEPRECATION")
        private val METADATA = Reflect.field(MetadataStoreBase::class.java, "metadataMap")
        private val HIDDEN = Reflect.field(CraftPlayer::class.java, "invertedVisibilityEntities")
        // A client needs the entry until the skin loaded
        private const val TAB_ENTRY_TICKS = 40
        private const val ALL_SKIN_PARTS: Byte = 0x7F
        // A dead player stays until its client respawns; a body without a client goes after the death animation
        private const val CORPSE_TICKS = 20
        // LocalPlayer's window for the second jump of a flight toggle
        private const val FLIGHT_TOGGLE_TICKS = 7
        private const val FLIGHT_CLIMB = 3f
        private const val FORWARD_IMPULSE = 1.0E-5f
    }
}

/**
 * Adds a bot to [level] as Citizens adds its NPCs: no view distance while the chunk map takes the player in.
 *
 * @agent claude-opus-5-5
 */
internal inline fun <T> withoutViewDistance(level: ServerLevel, add: () -> T): T {
    val chunkMap = level.chunkSource.chunkMap
    val viewDistance = chunkMap.serverViewDistance
    chunkMap.serverViewDistance = -1
    try {
        return add()
    } finally {
        chunkMap.serverViewDistance = viewDistance
    }
}

/**
 * Whether a shield took the whole hit: some damage went to [blocking] (negative, as the damage modifier counts it)
 * and none of [damage] is left. Such a hit makes no hurt sound.
 *
 * @agent claude-opus-5-5
 */
internal fun isFullyBlocked(damage: Float, blocking: Double): Boolean = damage <= 0f && blocking < 0.0
