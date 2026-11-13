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
 * the one of its colour, its island the agent's spawn or the bed, its generator the iron or gold lying by the bed,
 * the neutral generators the diamonds and emeralds lying between the islands, its shopkeepers the entities the
 * facts name near the island. Blocks that appear while the round plays count as placed by players.
 *
 * The host calls [tick] every client tick and [blockChanged] for every block the server changes.
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
    private val placed = HashSet<BlockPos>()
    private val spawns = HashMap<String, Vec3>()
    private val islandGenerators = HashMap<String, Generator>()
    private val neutralGenerators = ArrayList<Generator>()

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
     * The server changed the block at [pos] to [state]; one that appears while the round plays was placed by someone.
     */
    public fun blockChanged(pos: BlockPos, state: BlockState) {
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
        val rows = facts.teams
        ownTeamName = facts.ownTeamName ?: rows.firstOrNull { me.scoreboardName in it.memberNames }?.name ?: ownTeamName
        val beds = blocks.find(level, me.blockPosition()) { it.block is BedBlock }.mapNotNull { pos ->
            (level.getBlockState(pos).block as? BedBlock)?.let { it.color to pos }
        }
        known = rows.map { seeTeam(it, beds) }
        seeGenerators()
    }

    private fun seeTeam(row: TeamFacts, beds: List<Pair<DyeColor, BlockPos>>): SeenTeam {
        val isOwn = row.name == ownTeamName
        val colors = bedColors(row.color)
        val bed = beds.filter { (color, _) -> color in colors }.minByOrNull { (_, pos) ->
            if (isOwn) ownSpawn?.let { pos.distToCenterSqr(it) } ?: 0.0 else 0.0
        }?.second
        val spawn = (if (isOwn) ownSpawn else null) ?: bed?.let { Vec3.atBottomCenterOf(it) } ?: spawns[row.name]
        spawn?.let { spawns[row.name] = it }
        return SeenTeam(
            name = row.name,
            spawn = spawn ?: Vec3.ZERO,
            bed = bed,
            isBedAlive = bed != null && row.isBedAlive,
            memberNames = row.memberNames,
            members = level.players().filter { it.scoreboardName in row.memberNames && it.isAlive && !it.isSpectator },
            shopkeeper = spawn?.let { npcNear(it, facts::isShopkeeper) },
            upgrader = spawn?.let { npcNear(it, facts::isUpgrader) },
        )
    }

    private fun seeGenerators() {
        val bases = ArrayList<AABB>()
        for (team in known) {
            val center = Vec3.atCenterOf(team.bed ?: continue)
            val base = AABB.ofSize(center, 16.0, 12.0, 16.0)
            bases += base
            items(base) { it == Currency.IRON || it == Currency.GOLD }.minByOrNull { it.distanceToSqr(center) }
                ?.let { islandGenerators[team.name] = Generator(Currency.IRON, it.position(), team.name) }
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
