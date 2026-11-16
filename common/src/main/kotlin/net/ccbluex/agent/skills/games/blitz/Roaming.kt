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

package net.ccbluex.agent.skills.games.blitz

import net.ccbluex.agent.math.BlockPos
import net.ccbluex.agent.math.ChunkPos
import net.ccbluex.agent.math.Mth
import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.math.horizontalDistanceToSqr
import net.ccbluex.agent.world.block.BlockTags
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where an opponent was last seen.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L1153-L1177)
 *
 * @agent claude-opus-5-5
 */
internal class Sighting(val name: String, val position: Vec3, val at: Long) {
    val label: String
        get() = "seen $name"
}

/**
 * Players farther away at the start are not taken for pedestals of the agent's ring.
 */
private const val SPAWN_RANGE = 64.0
private const val MIN_SPAWNS = 4
private const val CENTER_SEARCH = 32.0
private const val CLUSTER_RADIUS = 8.0
private const val MIN_CLUSTER = 3
private const val COMPASS_LABEL = "compass"
private const val SEEK_ARRIVAL = 8.0
private const val SEEN_TICKS = 60 * Round.TICKS_PER_SECOND
private const val CENTER_REVISIT_TICKS = 90 * Round.TICKS_PER_SECOND
private const val ROAM_HEADINGS = 12
private const val ROAM_DISTANCE = 48.0
private const val ROAM_RADIUS = 3.0
private const val ROAM_ARRIVAL = 6.0
private const val VISITED_SPREAD = 2
private const val VISITED_WEIGHT = 30.0

/**
 * Finds the center: the players stand on pedestals in a ring around it at the start, so the middle of their
 * positions leads to it, moved onto the nearest dense group of chests; without the spawns it is the group of chests
 * closest to the middle of the world border.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L389-L413)
 *
 * @agent claude-opus-5-5
 */
internal fun Round.locateCenter(fromPedestal: Boolean) {
    val position = player.position()
    val spawns = if (fromPedestal) {
        (targets.around().map { it.position() } + position).filter { it.horizontalDistanceTo(position) <= SPAWN_RANGE }
    } else {
        emptyList()
    }
    center = if (spawns.size >= MIN_SPAWNS) {
        val middle = Vec3(spawns.sumOf { it.x } / spawns.size, position.y, spawns.sumOf { it.z } / spawns.size)
        chestCluster(middle, CENTER_SEARCH) ?: groundAt(middle.x, middle.z) ?: middle
    } else {
        val border = agent.body.level.worldBorder
        chestCluster(Vec3(border.centerX, position.y, border.centerZ), Double.MAX_VALUE)
    }
    agent.logger.debug("Center at {} from {} spawns", center, spawns.size)
}

/**
 * The middle of the group of at least [MIN_CLUSTER] chests closest to [reference].
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L416-L427)
 *
 * @agent claude-opus-5-5
 */
private fun Round.chestCluster(reference: Vec3, maxDistance: Double): Vec3? {
    val all = senses.chests.toList()
    return all.asSequence()
        .filter { reference.horizontalDistanceTo(Vec3.atBottomCenterOf(it)) <= maxDistance }
        .map { pos -> all.filter { it.closerThan(pos, CLUSTER_RADIUS) } }
        .filter { it.size >= MIN_CLUSTER }
        .map { group ->
            Vec3(group.sumOf { it.x + 0.5 }, group.sumOf { it.y.toDouble() }, group.sumOf { it.z + 0.5 })
                .scale(1.0 / group.size)
        }
        .minByOrNull { reference.horizontalDistanceToSqr(it) }
}

/**
 * Follows the compass, which the server points at a player, else goes where a recent killer or anyone else was
 * last seen, else to the center now and then, else explores.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L831-L853)
 *
 * @agent claude-opus-5-5
 */
internal fun Round.seek() {
    compassTarget()?.takeIf { isReachable(COMPASS_LABEL) }?.let { return walkTo(COMPASS_LABEL, it, ROAM_RADIUS) }

    val position = player.position()
    lastSeen.values.removeIf { ticks - it.at > SEEN_TICKS || position.horizontalDistanceTo(it.position) <= SEEK_ARRIVAL }
    val sighting = targets.recentKillers(SEEN_TICKS)
        .firstNotNullOfOrNull { name -> lastSeen[name]?.takeIf { isReachable(it.label) } }
        ?: lastSeen.values.filter { isReachable(it.label) }.maxByOrNull { it.at }
    if (sighting != null) return walkTo(sighting.label, sighting.position, ROAM_RADIUS)

    val middle = center
    if (middle != null && ticks - centerSoughtAt >= CENTER_REVISIT_TICKS && isReachable(Round.CENTER_LABEL)) {
        if (position.horizontalDistanceTo(middle) > Round.CENTER_ARRIVAL) {
            return walkTo(Round.CENTER_LABEL, middle, Round.CENTER_ARRIVAL)
        }
        centerSoughtAt = ticks
    }
    explore()
}

/**
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L856-L866)
 *
 * @agent claude-opus-5-5
 */
private fun Round.compassTarget(): Vec3? {
    val target = senses.compassTarget ?: return null
    val x = target.x + 0.5
    val z = target.z + 0.5
    val point = groundAt(x, z) ?: Vec3(x, player.y, z)
    return point.takeIf { player.position().horizontalDistanceTo(it) > SEEK_ARRIVAL }
}

/**
 * Walks on into the parts of the map not walked yet.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L890-L901)
 *
 * @agent claude-opus-5-5
 */
internal fun Round.explore() {
    val position = player.position()
    val current = roam?.takeIf { isReachable(roamLabel(it)) && position.horizontalDistanceTo(it) > ROAM_ARRIVAL }
        ?: pickRoam()
    roam = current
    if (current == null) return resetTarget()
    walkTo(roamLabel(current), current, ROAM_RADIUS)
}

/**
 * A point [ROAM_DISTANCE] away in the direction least walked so far, preferring to keep the heading.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L904-L935)
 *
 * @agent claude-opus-5-5
 */
private fun Round.pickRoam(): Vec3? {
    val position = player.position()
    val border = agent.body.level.worldBorder
    var best: Vec3? = null
    var bestAngle = Double.NaN
    var bestScore = Double.MAX_VALUE
    for (i in 0 until ROAM_HEADINGS) {
        val angle = i * 2 * PI / ROAM_HEADINGS
        val x = position.x + cos(angle) * ROAM_DISTANCE
        val z = position.z + sin(angle) * ROAM_DISTANCE
        if (!border.isWithinBounds(x, z)) continue
        val point = groundAt(x, z) ?: continue
        if (!isReachable(roamLabel(point))) continue
        val turn = if (roamAngle.isNaN()) 0.0 else abs(Mth.wrapDegrees(Math.toDegrees(angle - roamAngle)))
        val score = visitedAround(point) * VISITED_WEIGHT + turn
        if (score < bestScore) {
            best = point
            bestAngle = angle
            bestScore = score
        }
    }
    if (best != null) roamAngle = bestAngle
    return best
}

/**
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L937-L949)
 *
 * @agent claude-opus-5-5
 */
private fun Round.visitedAround(point: Vec3): Int {
    val chunkX = Mth.floor(point.x) shr Round.CHUNK_SHIFT
    val chunkZ = Mth.floor(point.z) shr Round.CHUNK_SHIFT
    var count = 0
    for (x in chunkX - VISITED_SPREAD..chunkX + VISITED_SPREAD) {
        for (z in chunkZ - VISITED_SPREAD..chunkZ + VISITED_SPREAD) {
            if (visitedChunks.contains(ChunkPos.pack(x, z))) count++
        }
    }
    return count
}

/**
 * The ground at [x] [z] below any tree, or null where the chunk is not loaded.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L952-L970)
 *
 * @agent claude-opus-5-5
 */
private fun Round.groundAt(x: Double, z: Double): Vec3? {
    val level = agent.body.level
    val blockX = Mth.floor(x)
    val blockZ = Mth.floor(z)
    if (!level.hasChunk(blockX shr Round.CHUNK_SHIFT, blockZ shr Round.CHUNK_SHIFT)) return null
    var y = level.getHeight(blockX, blockZ)
    val pos = BlockPos.MutableBlockPos(blockX, y - 1, blockZ)
    while (y > level.minY) {
        val state = level.getBlockState(pos)
        if (!state.`is`(BlockTags.LEAVES) && !state.`is`(BlockTags.LOGS)) break
        y--
        pos.move(0, -1, 0)
    }
    return Vec3(x, y.toDouble(), z)
}

/**
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L1120-L1121)
 *
 * @agent claude-opus-5-5
 */
private fun roamLabel(point: Vec3) =
    "roam ${Mth.floor(point.x) shr Round.CHUNK_SHIFT},${Mth.floor(point.z) shr Round.CHUNK_SHIFT}"
