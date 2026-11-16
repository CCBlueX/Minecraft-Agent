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

import net.ccbluex.agent.math.Vec3
import net.ccbluex.agent.math.horizontalDistanceTo
import net.ccbluex.agent.tick.waitTicks

private const val CENTER_TICKS = 45 * Round.TICKS_PER_SECOND
private const val CENTER_RADIUS = 14.0
private const val CHEST_DETOUR = 6.0
private const val TRAVEL_RANGE = 64.0
private const val GEAR_TICKS = 3 * Round.TICKS_PER_SECOND
private const val MAX_GEAR_TICKS = 15 * Round.TICKS_PER_SECOND
private const val HUNGER_RETRY_TICKS = 30 * Round.TICKS_PER_SECOND

/**
 * At the center, a player who hit the agent this recently is fought instead of looted past.
 */
private const val DEFEND_TICKS = 40
private const val PREPARE_TICKS = 10 * Round.TICKS_PER_SECOND
private const val MAX_PREPARE_TICKS = 60 * Round.TICKS_PER_SECOND
private const val LANDING_TICKS = Round.TICKS_PER_SECOND
private const val PREPARE_CHEST_RANGE = 16.0

/**
 * The round from the start until the deathmatch: to the center and its chests first, then hunting, stopping to
 * gear up whenever the kit arrives or hunger comes.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L366-L382)
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.play(fromPedestal: Boolean) {
    locateCenter(fromPedestal)
    lootCenter()
    enter(Phase.HUNT, "geared")
    hunt()
}

/**
 * Walks to the center and opens its chests, for at most [CENTER_TICKS]; only a player who hit the agent lately is
 * fought or evaded, as everyone starts around it. Gears up after.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L433-L465)
 *
 * @agent claude-opus-5-5
 */
private suspend fun Round.lootCenter() {
    val since = ticks
    while (true) {
        val attacked = targets.hitsTaken(DEFEND_TICKS) > 0
        if (attacked && engage() || isLooting() || pickUpGear()) {
            waitTicks(1)
            continue
        }
        if (opponent != null) stopFight("center chests first")
        evading = false

        val reason = gearReason()
        if (reason != null) {
            gearUp(reason)
            enter(Phase.CENTER, "geared")
            continue
        }
        val done = centerStep(since)
        if (done != null) {
            centerDone = true
            gearUp(done)
            return
        }
        waitTicks(1)
    }
}

/**
 * One tick of looting the center; why it is done, or null while it goes on.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L433-L460)
 *
 * @agent claude-opus-5-5
 */
private fun Round.centerStep(since: Long): String? {
    val middle = center ?: return "no center"
    if (ticks - since >= CENTER_TICKS) return "center time over"
    val chest = pickChest { Vec3.atCenterOf(it).horizontalDistanceTo(middle) <= CENTER_RADIUS }
    when {
        chest != null -> openChest(chest)
        !isReachable(Round.CENTER_LABEL) -> return "center unreachable"
        player.position().horizontalDistanceTo(middle) > Round.CENTER_ARRIVAL ->
            walkTo(Round.CENTER_LABEL, middle, Round.CENTER_ARRIVAL)
        else -> return "center looted"
    }
    return null
}

/**
 * Stands while the abilities wear the loot and eat, at least [GEAR_TICKS] and until fed, at most [MAX_GEAR_TICKS].
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L604-L642)
 *
 * @agent claude-opus-5-5
 */
private suspend fun Round.gearUp(reason: String) {
    enter(Phase.GEAR, reason)
    val since = ticks
    while (true) {
        if (!engage() && !isLooting()) {
            resetTarget()
            val fed = agent.skills.eat?.wantsToEat != true
            if (fed && ticks - since >= GEAR_TICKS || ticks - since >= MAX_GEAR_TICKS) break
        }
        waitTicks(1)
    }
    hungerCheckAt = ticks + HUNGER_RETRY_TICKS
}

/**
 * Why to stop and gear up now: the kit items arrived, or hunger with food at hand.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L624-L636)
 *
 * @agent claude-opus-5-5
 */
private fun Round.gearReason(): String? {
    kitAt?.let {
        if (ticks >= it) {
            kitAt = null
            return "kit items"
        }
    }
    if (ticks >= hungerCheckAt && agent.skills.eat?.wantsToEat == true) return "hungry"
    return null
}

/**
 * Opens chests close to the way, then goes for what [plan] picks among the chests and the players in reach;
 * under-equipped only clearly weaker players count. Geared with nobody in sight it looks for players.
 *
 * @agent claude-opus-5-5
 */
private suspend fun Round.hunt() {
    while (true) {
        if (engage() || isLooting() || pickUpGear()) {
            waitTicks(1)
            continue
        }
        val reason = gearReason()
        if (reason != null) {
            gearUp(reason)
            enter(Phase.HUNT, "geared")
            continue
        }
        huntStep()
        waitTicks(1)
    }
}

/**
 * One tick of hunting: a chest close by, else what [plan] picks.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L472-L506)
 *
 * @agent claude-opus-5-5
 */
private fun Round.huntStep() {
    val position = player.position()
    pickChest { Vec3.atCenterOf(it).closerThan(position, CHEST_DETOUR) }?.let { return openChest(it) }

    val armed = isArmed
    val equipped = isEquipped
    val rivals = rivals { armed && it.distanceTo(player) <= TRAVEL_RANGE && (equipped || isWeaker(it)) }
    when (val choice = choose(rivals, caches(), armed, canExplore = true)) {
        is Choice.Loot -> openChest(choice.chest)
        is Choice.Hunt -> chase(choice)
        is Choice.Hold -> resetTarget()
        Choice.Seek -> seek()
        null -> explore()
    }
}

/**
 * The deathmatch countdown after its teleport, while attacks do nothing: loots the chests around the arena nearest
 * first and stands while the abilities eat and heal. Ends with the countdown, the deathmatch start, a player's hit
 * after landing, or a time cap.
 *
 * From LiquidBounce: [GameBotBlitz.kt](https://github.com/CCBlueX/LiquidBounce/blob/e603d93ac11c603588968fa504b27a5393341104/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/gamebot/games/GameBotBlitz.kt#L592-L598)
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.prepare() {
    val since = ticks
    var endAt: Long? = null
    while (true) {
        val elapsed = ticks - since
        senses.secondsToDeathmatch?.takeIf { it > 0 }?.let { endAt = ticks + it * Round.TICKS_PER_SECOND }
        val hit = elapsed > LANDING_TICKS && targets.hitsTaken((elapsed - LANDING_TICKS).toInt(), byPlayer = true) > 0
        val reason = when {
            senses.isDeathmatchStarted -> "deathmatch started"
            hit -> "hit taken"
            ticks >= (endAt ?: (since + PREPARE_TICKS)) -> "countdown over"
            elapsed >= MAX_PREPARE_TICKS -> "prepare too long"
            else -> null
        }
        if (reason != null) {
            prepared = true
            enter(Phase.DEATHMATCH, reason)
            return
        }
        if (!isLooting()) {
            val position = player.position()
            val chest = pickChest { Vec3.atCenterOf(it).closerThan(position, PREPARE_CHEST_RANGE) }
            if (chest != null) openChest(chest) else resetTarget()
        }
        waitTicks(1)
    }
}

/**
 * Fights whom [plan] picks among everyone left; loots and explores otherwise.
 *
 * @agent claude-opus-5-5
 */
internal suspend fun Round.deathmatch() {
    while (true) {
        if (!engage() && !isLooting() && !pickUpGear()) {
            val armed = isArmed
            val choice = choose(rivals { armed }, emptyList(), armed, canExplore = false)
            if (choice is Choice.Hunt) {
                chase(choice)
            } else {
                pickChest { true }?.let { openChest(it) } ?: explore()
            }
        }
        waitTicks(1)
    }
}
