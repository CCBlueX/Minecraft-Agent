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

package net.ccbluex.agent.skills.games.bedwars

import net.ccbluex.agent.ExperimentalAgentApi
import net.ccbluex.agent.skills.games.LevelRoundSenses
import net.ccbluex.agent.skills.games.LoadedBlocks
import net.ccbluex.agent.skills.games.RoundFacts
import net.ccbluex.agent.skills.games.RoundState
import net.ccbluex.agent.skills.shop.Currency
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * A Bed Wars round of a client: the team texts from [LevelSenses.Facts], the rest from the [level]. A team's bed is
 * the one of its colour, or, where the beds' colours do not tell the teams apart as on 1.8 servers, the free bed
 * nearest to where the team was seen. Its island is the agent's spawn, where the agent stands after the teleport
 * from the waiting lobby, or the bed; its generator the iron or gold lying by the bed, the neutral generators the
 * diamonds and emeralds lying between the islands, its shopkeepers the entities the facts name near the island.
 * Blocks that appear while the round plays count as placed by players, as the server's block update packets tell:
 * a block set in the level can also be the client taking back its own prediction.
 *
 * The host calls [tick] every client tick and [received] for every packet the server sends.
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
@ExperimentalAgentApi
public class LevelSenses internal constructor(
    level: Level,
    private val facts: LevelSenses.Facts,
    private val blocks: LoadedBlocks,
) : LevelRoundSenses(level, facts), Senses {

    public constructor(level: Level, facts: Facts) : this(level, facts, LoadedBlocks.IN_CHUNKS)

    /**
     * What a client learns about a Bed Wars round from text; the level gives the beds, generators, shopkeepers and
     * players.
     *
     * @agent claude-sonnet-5-5
     */
    public interface Facts : RoundFacts {

        /**
         * The name of the agent's team; null while the text does not tell it, then the team with the agent as a
         * member counts.
         */
        public val ownTeamName: String?

        /**
         * Every team the text names, the own one included.
         */
        public val teams: List<TeamFacts>

        /**
         * Whether [entity] sells items, by its name or the hologram over it.
         */
        public fun isShopkeeper(entity: Entity): Boolean

        /**
         * Whether [entity] sells team upgrades.
         */
        public fun isUpgrader(entity: Entity): Boolean
    }

    private var ownTeamName: String? = null
    private var known: List<SeenTeam> = emptyList()

    /**
     * Where the agent first stood on its island this round: the own team's spawn.
     */
    private var home: Vec3? = null

    /**
     * The bed of each team, kept while it stands when the beds' colours do not tell the teams apart.
     */
    private var claimed: Map<String, BlockPos> = emptyMap()
    private val placed = HashSet<BlockPos>()
    private val spawns = HashMap<String, Vec3>()
    private val islandGenerators = HashMap<String, Generator>()
    private val neutralGenerators = ArrayList<Generator>()

    override val ownSpawn: Vec3?
        get() = home ?: super.ownSpawn

    override val ownTeam: BedWarsTeam?
        get() = ownTeamName?.let { name -> known.firstOrNull { it.name == name } }

    override val teams: List<BedWarsTeam>
        get() = known

    override var generators: List<Generator> = emptyList()
        private set

    override val isRespawning: Boolean
        get() = isSpectator && ownTeam?.isBedAlive == true

    override fun isRespawning(player: Player): Boolean = player.isSpectator && teamOf(player)?.isBedAlive == true

    override fun teamOf(player: Player): BedWarsTeam? = known.firstOrNull { player.scoreboardName in it.memberNames }

    override fun isTeammate(entity: Entity): Boolean {
        val own = ownTeam ?: return false
        return entity is Player && entity !== self && teamOf(entity) === own
    }

    override fun isPlayerPlaced(pos: BlockPos): Boolean = pos in placed

    /**
     * A packet the server sent. Block and section updates are the server's word on which blocks stand; one that appears
     * while the round plays was placed by someone. Other packets are ignored.
     */
    public fun received(packet: Packet<*>) {
        when (packet) {
            is ClientboundBlockUpdatePacket -> blockChanged(packet.pos, packet.blockState)
            is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates(::blockChanged)
        }
    }

    private fun blockChanged(pos: BlockPos, state: BlockState) {
        if (this.state != RoundState.PLAYING) {
            return
        }
        if (state.isAir) {
            placed -= pos
        } else {
            placed += pos.immutable()
        }
    }

    override fun scan() {
        val me = self ?: return
        if (home == null && state == RoundState.PLAYING && !isSpectator && me.onGround()) {
            home = islandSpawn(super.ownSpawn, me.position(), matchSeconds)
        }
        val rows = facts.teams
        ownTeamName = facts.ownTeamName ?: rows.firstOrNull { me.scoreboardName in it.memberNames }?.name ?: ownTeamName
        val beds = blocks.find(level, me.blockPosition()) { it.block is BedBlock }.mapNotNull { pos ->
            (level.getBlockState(pos).block as? BedBlock)?.let { it.color to pos }
        }
        val colours = beds.associate { (color, pos) -> pos to color }
        val members = rows.associate { row ->
            row.name to level.players().filter { it.scoreboardName in row.memberNames && it.isAlive && !it.isSpectator }
        }
        val teamColors = rows.associate { it.name to bedColors(it.color) }
        claimed = claimBeds(
            beds = beds.map { it.second },
            teams = rows.sortedByDescending { it.name == ownTeamName }.map { it.name },
            byColour = colours.values.distinct().size > 1,
            coloured = { team, bed -> colours[bed] in teamColors.getValue(team) },
            seen = { team -> if (team == ownTeamName) ownSpawn else middleOf(members.getValue(team)) },
            claimed = claimed,
        )
        known = rows.map { seeTeam(it, members.getValue(it.name)) }
        seeGenerators()
    }

    private fun seeTeam(row: TeamFacts, members: List<Player>): SeenTeam {
        val isOwn = row.name == ownTeamName
        val bed = claimed[row.name]
        val spawn = (if (isOwn) ownSpawn else null) ?: bed?.let { Vec3.atBottomCenterOf(it) } ?: spawns[row.name]
        spawn?.let { spawns[row.name] = it }
        return SeenTeam(
            name = row.name,
            spawn = spawn ?: Vec3.ZERO,
            bed = bed,
            isBedAlive = bed != null && row.isBedAlive,
            memberNames = row.memberNames,
            members = members,
            shopkeeper = spawn?.let { npcNear(it, facts::isShopkeeper) },
            upgrader = spawn?.let { npcNear(it, facts::isUpgrader) },
        )
    }

    /**
     * The iron and gold of an island drop by the own spawn, or by the bed where the spawn is not known yet, up to 16
     * blocks away; islands lie farther apart. A generator once seen stays known while its drops are picked up.
     */
    private fun seeGenerators() {
        val bases = ArrayList<AABB>()
        for (team in known) {
            val centers = listOfNotNull(
                if (team.name == ownTeamName) home else null,
                team.bed?.let { Vec3.atCenterOf(it) },
            )
            bases += centers.map { AABB.ofSize(it, 16.0, 12.0, 16.0) }
            centers.firstNotNullOfOrNull { center ->
                items(AABB.ofSize(center, 32.0, 12.0, 32.0)) { it == Currency.IRON || it == Currency.GOLD }
                    .minByOrNull { it.distanceToSqr(center) }
            }?.let { islandGenerators[team.name] = Generator(Currency.IRON, it.position(), team.name) }
        }
        if (spawns.size >= 2) {
            // The neutral generators lie between the islands, within a margin of the farthest one
            val middle = spawns.values.reduce { a, b -> a.add(b) }.scale(1.0 / spawns.size)
            val radius = spawns.values.maxOf { it.distanceTo(middle) } + 24.0
            val area = AABB.ofSize(middle, radius * 2, 128.0, radius * 2)
            for (item in items(area) { it == Currency.DIAMOND || it == Currency.EMERALD }) {
                val resource = Currency.of(item.item.item) ?: continue
                val at = item.position()
                if (bases.none { it.contains(at) } && neutralGenerators.none {
                        it.resource == resource && it.position.distanceToSqr(at) <= MERGE_RANGE * MERGE_RANGE
                    }
                ) {
                    neutralGenerators += Generator(resource, at, null)
                }
            }
        }
        generators = islandGenerators.values + neutralGenerators
    }

    private fun items(box: AABB, wanted: (Currency) -> Boolean): List<ItemEntity> =
        level.getEntities(null as Entity?, box) {
            it is ItemEntity && it.isAlive && Currency.of(it.item.item)?.let(wanted) == true
        }.filterIsInstance<ItemEntity>()

    private fun npcNear(spawn: Vec3, test: (Entity) -> Boolean): Vec3? = level
        .getEntities(null as Entity?, AABB.ofSize(spawn, NPC_RANGE * 2, NPC_RANGE * 2, NPC_RANGE * 2)) { test(it) }
        .filter { it.position().distanceToSqr(spawn) <= NPC_RANGE * NPC_RANGE }
        .minByOrNull { it.position().distanceToSqr(spawn) }
        ?.position()

    private fun bedColors(color: DyeColor): Set<DyeColor> = when (color) {
        DyeColor.LIME, DyeColor.GREEN -> setOf(DyeColor.LIME, DyeColor.GREEN)
        DyeColor.CYAN, DyeColor.LIGHT_BLUE -> setOf(DyeColor.CYAN, DyeColor.LIGHT_BLUE)
        else -> setOf(color)
    }

    /**
     * @agent claude-opus-5-5
     */
    private class SeenTeam(
        override val name: String,
        override val spawn: Vec3,
        override val bed: BlockPos?,
        override val isBedAlive: Boolean,
        val memberNames: Set<String>,
        override val members: List<Player>,
        override val shopkeeper: Vec3?,
        override val upgrader: Vec3?,
    ) : BedWarsTeam {

        override val chest: BlockPos? = null
    }

    private companion object {
        const val NPC_RANGE = 24.0
        const val MERGE_RANGE = 3.0
    }
}

/**
 * A team as the text shows it. [color] is the colour of its bed; lime and green, cyan and light blue count as one.
 *
 * @property memberNames the names of the players on the team, as the level's players carry them
 * @property isBedAlive whether the text says the bed still stands
 * @agent claude-sonnet-5-5
 */
@ExperimentalAgentApi
public data class TeamFacts(
    public val name: String,
    public val color: DyeColor,
    public val memberNames: Set<String>,
    public val isBedAlive: Boolean,
)

/**
 * The own spawn, once the player stands on the ground at [at] [seconds] into the round: there when it is more than 24
 * blocks from [start], as servers teleport from the waiting lobby after announcing the start; [start] itself once
 * the player stayed for 2 seconds, as servers that teleport first. Null while it may still be teleported.
 *
 * @agent claude-sonnet-5-5
 */
internal fun islandSpawn(start: Vec3?, at: Vec3, seconds: Int): Vec3? = when {
    start == null || start.distanceTo(at) > 24.0 -> at
    seconds >= 2 -> start
    else -> null
}

/**
 * The bed of each of [teams], the own team first: by colour where the beds' colours tell the teams apart; else, as
 * on 1.8 servers whose beds all show red, the free bed nearest to where the team was [seen], within 24 blocks.
 * Without colours a bed [claimed] before stays its team's while it stands. Each half of a bed counts as the bed.
 *
 * @agent claude-sonnet-5-5
 */
internal fun claimBeds(
    beds: List<BlockPos>,
    teams: List<String>,
    byColour: Boolean,
    coloured: (team: String, bed: BlockPos) -> Boolean,
    seen: (team: String) -> Vec3?,
    claimed: Map<String, BlockPos>,
): Map<String, BlockPos> {
    val claims = LinkedHashMap<String, BlockPos>()
    if (!byColour) {
        for (team in teams) claimed[team]?.takeIf { it in beds }?.let { claims[team] = it }
    }
    for (team in teams) {
        if (team in claims) continue
        val near = seen(team)
        val free = beds.filter { bed -> claims.values.none { it.distManhattan(bed) <= 1 } }
        val bed = when {
            byColour -> free.filter { coloured(team, it) }.minByOrNull { if (near == null) 0.0 else it.distToCenterSqr(near) }
            near == null -> null
            else -> free.filter { it.distToCenterSqr(near) <= 24.0 * 24.0 }.minByOrNull { it.distToCenterSqr(near) }
        }
        bed?.let { claims[team] = it }
    }
    return claims
}

/**
 * Where [players] stand on average; null for none.
 */
private fun middleOf(players: List<Player>): Vec3? {
    if (players.isEmpty()) return null
    val count = players.size.toDouble()
    return Vec3(players.sumOf { it.x } / count, players.sumOf { it.y } / count, players.sumOf { it.z } / count)
}
