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

package net.ccbluex.agent.skills.fight

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

/**
 * Obsidian beside the target, a crystal on it, and the crystal hit at once; crystals already there get hit too.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L904-L935)
 *
 * @agent claude-opus-5-5
 */
internal class Crystal : Technique(Skill.CRYSTAL, 45, 10) {

    private enum class Phase { OBSIDIAN, PLACE, HIT }

    private var phase = Phase.HIT
    private var base = BlockPos.ZERO
    private var since = 0L
    private var phaseAt = 0L

    /**
     * Holds the pop while the target's raised shield faces the crystal.
     */
    private var wary = false
    private var axing = false

    /**
     * Ticks the crystal stands before the hit: none on hard.
     */
    private var arm = 0

    override fun can(fight: BuiltInFight): Boolean {
        if (fight.distance() > RANGE) return false
        return existing(fight) != null || fight.hands.has { it.isOf(Items.END_CRYSTAL) } && spot(fight) != null
    }

    private fun existing(fight: BuiltInFight): EndCrystal? {
        val target = fight.target ?: return null
        val here = fight.player.position()
        return fight.body.level.getEntitiesOfClass(EndCrystal::class.java, target.boundingBox.inflate(3.0))
            .firstOrNull { fight.inReach(it) && it.position().distanceTo(here) > 2 }
    }

    private fun crystalBase(fight: BuiltInFight, pos: BlockPos): Boolean =
        fight.body.level.getBlockState(pos).let { it.`is`(Blocks.OBSIDIAN) || it.`is`(Blocks.BEDROCK) }

    /**
     * The block the crystal goes on: under a free cell beside the target, within reach.
     */
    private fun spot(fight: BuiltInFight): BlockPos? {
        val target = fight.target ?: return null
        val feet = Building.landing(fight, target)
        val eye = fight.player.eyePosition
        val obsidian = fight.hands.has { it.isOf(Items.OBSIDIAN) }
        var best: BlockPos? = null
        var bestScore = Double.MAX_VALUE
        // Obsidian already in the ground beside the target, or a free cell beside its feet to put obsidian in
        for (dy in -1..0) for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            val block = feet.offset(dx, dy, dz)
            val usable = crystalBase(fight, block) || dy == 0 && obsidian && Building.air(fight, block) &&
                Building.solid(fight, block.below()) && !Building.occupied(fight, block, 1)
            if (!usable || !Building.air(fight, block.above()) || !Building.air(fight, block.above(2))) continue
            // A crystal needs the two cells above free of entities
            if (Building.occupied(fight, block.above(), 2)) continue
            val top = Building.top(block)
            if (top.distanceTo(eye) > PLACE_REACH) continue
            val score = top.distanceTo(target.position()) - 0.3 * top.distanceTo(fight.player.position()) +
                fight.placementNoise()
            if (score < bestScore) {
                best = block
                bestScore = score
            }
        }
        return best
    }

    override fun begin(fight: BuiltInFight) {
        since = fight.tick
        wary = fight.random.nextDouble() < minOf(1.0, fight.skillLevel.technique)
        axing = false
        arm = fight.slow(0, 10)
        val spot = if (existing(fight) == null) spot(fight) else null
        if (spot == null) {
            phase = Phase.HIT
        } else {
            base = spot
            phase = if (crystalBase(fight, spot)) Phase.PLACE else Phase.OBSIDIAN
        }
        phaseAt = fight.tick
        fight.attackHeld = true
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        val target = fight.target ?: return false
        if (fight.tick - since > (if (axing) 24 else 12) + arm) return false
        when (phase) {
            Phase.OBSIDIAN -> {
                if (!fight.hands.hold { it.isOf(Items.OBSIDIAN) }) return false
                // Still turning or a missed click: the skill's own time limit ends the wait
                if (!place(fight, base.below())) return true
                next(fight, Phase.PLACE)
            }
            Phase.PLACE -> {
                // A crystal is no block: it goes on by a right click on the obsidian
                if (!fight.hands.hold { it.isOf(Items.END_CRYSTAL) }) return false
                if (!place(fight, base)) return true
                next(fight, Phase.HIT)
            }
            Phase.HIT -> {
                val crystal = existing(fight) ?: return fight.tick - phaseAt < 3
                val point = crystal.position().add(0.0, 1.0, 0.0)
                if (fight.tick - phaseAt < arm) {
                    fight.lookAt(point, 4.0)
                    return true
                }
                // A raised shield takes the whole blast from in front: axe it down first, or wait for it to drop
                if (wary && Shielding.targetBlocking(fight) && faces(target, crystal.position())) {
                    if (!Shielding.holdAxe(fight)) return true
                    axing = true
                    if (fight.inReach(target) && fight.aimedAt(target)) {
                        fight.strike(target)
                        if (!Shielding.targetBlocking(fight)) fight.stunUntil = fight.tick + Shielding.STUN_TICKS
                    }
                    return true
                }
                val rotation = Aiming.rotation(fight.player.eyePosition, point)
                fight.lookAt(rotation, 4.0)
                if (fight.aimed(rotation, 12.0)) {
                    fight.hands.holdPrimary()
                    if (fight.inReach(crystal)) fight.strike(crystal)
                    return false
                }
            }
        }
        return true
    }

    private fun next(fight: BuiltInFight, phase: Phase) {
        this.phase = phase
        phaseAt = fight.tick
    }

    /**
     * Whether [point] is in front of [entity], where its shield blocks explosions.
     */
    private fun faces(entity: LivingEntity, point: Vec3): Boolean {
        val view = entity.getViewVector(1f).multiply(1.0, 0.0, 1.0)
        return view.dot(point.subtract(entity.position()).multiply(1.0, 0.0, 1.0)) > 0
    }

    /**
     * Clicks the top of [against] with what is held once looking at it; false while it still turns or misses.
     */
    private fun place(fight: BuiltInFight, against: BlockPos): Boolean {
        fight.lookAt(Building.top(against), 4.0)
        if (!fight.navigator.canClick(against, Direction.UP)) return false
        return fight.clickLands() && Building.placeOn(fight, against, Direction.UP)
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val RANGE = 5.5
        const val PLACE_REACH = 4.5
    }
}

/**
 * A respawn anchor beside the target, charged with glowstone and set off: outside the nether a charged anchor
 * explodes when used with anything else. Gives up after a few tries; never in the nether, where anchors set spawn.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L128-L128)
 *
 * @agent claude-opus-5-5
 */
internal class Anchor(cooldown: Int) : Technique(Skill.ANCHOR, 46, cooldown) {

    private var anchor = BlockPos.ZERO
    private var placing = false
    private var since = 0L
    private var phaseAt = 0L
    private var useAt = 0L
    private var tries = 0
    private var charged = false
    private var covered = false
    private var coverFace: Direction? = null
    private var coverAt = 0L

    override fun can(fight: BuiltInFight): Boolean {
        if (fight.distance() > RANGE || fight.body.level.dimension() == Level.NETHER) return false
        return fight.hands.has { it.isOf(Items.GLOWSTONE) } && spot(fight) != null
    }

    private fun isAnchor(fight: BuiltInFight, pos: BlockPos) =
        fight.body.level.getBlockState(pos).`is`(Blocks.RESPAWN_ANCHOR)

    /**
     * A free cell or an anchor beside the target's feet, within reach.
     */
    private fun spot(fight: BuiltInFight): BlockPos? {
        val target = fight.target ?: return null
        val feet = Building.landing(fight, target)
        val eye = fight.player.eyePosition
        val anchors = fight.hands.has { it.isOf(Items.RESPAWN_ANCHOR) }
        var best: BlockPos? = null
        var bestScore = Double.MAX_VALUE
        for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            val cell = feet.offset(dx, 0, dz)
            val usable = isAnchor(fight, cell) ||
                anchors && Building.air(fight, cell) && Building.solid(fight, cell.below()) &&
                !Building.occupied(fight, cell, 1)
            if (!usable) continue
            val centre = Vec3.atCenterOf(cell)
            if (centre.distanceTo(eye) > PLACE_REACH) continue
            val score = centre.distanceTo(target.position()) - 0.3 * centre.distanceTo(fight.player.position()) +
                fight.placementNoise()
            if (score < bestScore) {
                best = cell
                bestScore = score
            }
        }
        return best
    }

    override fun begin(fight: BuiltInFight) {
        anchor = spot(fight) ?: BlockPos.ZERO
        since = fight.tick
        phaseAt = fight.tick
        useAt = fight.tick
        tries = 0
        charged = false
        covered = false
        coverFace = null
        placing = !isAnchor(fight, anchor)
        fight.attackHeld = true
    }

    override fun tick(fight: BuiltInFight): Boolean {
        fight.attackHeld = true
        if (fight.tick - since > MAX_TICKS) return false
        if (placing) return place(fight)
        // Gone: it blew up
        if (!isAnchor(fight, anchor)) return false
        coverFace?.let { return cover(fight, it) }
        // One charge is enough: a charged anchor goes off when used with anything but glowstone
        val item: (ItemStack) -> Boolean =
            if (charged) { it -> !it.isOf(Items.GLOWSTONE) } else { it -> it.isOf(Items.GLOWSTONE) }
        if (!fight.hands.hold(item)) return false
        fight.lookAt(Building.top(anchor), 4.0)
        if (fight.tick < useAt) return true
        // A block on the anchor's face towards the fighter takes most of the blast off it
        if (charged && !covered) {
            covered = true
            if (fight.random.nextDouble() < minOf(1.0, fight.skillLevel.technique)) {
                coverFace = coverSide(fight)
                coverAt = fight.tick
                if (coverFace != null) return true
            }
        }
        // The click waits for the rotation the server has to hit the top; MAX_TICKS bounds the wait
        if (!fight.navigator.canClick(anchor, Direction.UP)) return true
        if (tries++ >= TRIES) return false
        if (!fight.clickLands()) {
            useAt = fight.tick + 1
            return true
        }
        if (Building.placeOn(fight, anchor, Direction.UP) && !charged) {
            charged = true
            // A charged anchor waits a moment before it is set off on the easier levels
            useAt = fight.tick + fight.random.nextInt(1, 3) + fight.slow(0, 10)
            return true
        }
        useAt = fight.tick + fight.random.nextInt(1, 3)
        return true
    }

    private fun place(fight: BuiltInFight): Boolean {
        if (!fight.hands.hold { it.isOf(Items.RESPAWN_ANCHOR) }) return false
        val below = anchor.below()
        fight.lookAt(Building.top(below), 4.0)
        if (!Building.air(fight, anchor)) return false
        // The click waits for the rotation the server has to hit the top; MAX_TICKS bounds the wait
        if (!fight.navigator.canClick(below, Direction.UP)) return true
        if (!fight.clickLands() || !Building.placeOn(fight, below, Direction.UP)) return true
        placing = false
        phaseAt = fight.tick
        useAt = fight.tick + 1
        return true
    }

    /**
     * The anchor's side towards the fighter when a block fits there, else null.
     */
    private fun coverSide(fight: BuiltInFight): Direction? {
        val face = Building.towards(Vec3.atBottomCenterOf(anchor), fight.player.position())
        val cell = anchor.relative(face)
        return face.takeIf { Building.air(fight, cell) && !Building.occupied(fight, cell, 1) }
    }

    /**
     * Puts a block on [face] of the anchor once the rotation the server has hits that face, for half a second at
     * most; then the anchor is set off.
     */
    private fun cover(fight: BuiltInFight, face: Direction): Boolean {
        val held = fight.hands.hold { it.isOf(Items.OBSIDIAN) } || fight.hands.hold { it.isBuildingBlock } ||
            fight.hands.hold { it.isOf(Items.GLOWSTONE) }
        fight.lookAt(Vec3.atCenterOf(anchor).add(face.unitVec3.scale(0.5)), 4.0)
        val turning = !fight.navigator.canClick(anchor, face)
        if (held && Building.air(fight, anchor.relative(face)) && turning && fight.tick - coverAt < 10) return true
        if (held && !turning) Building.placeOn(fight, anchor, face)
        coverFace = null
        useAt = fight.tick + 1
        return true
    }

    override fun end(fight: BuiltInFight) = fight.hands.holdPrimary()

    private companion object {
        const val RANGE = 5.5
        const val PLACE_REACH = 4.5
        const val MAX_TICKS = 40

        /**
         * A charge and the use that sets it off, with room for misses.
         */
        const val TRIES = 6
    }
}
