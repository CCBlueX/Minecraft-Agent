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

package net.ccbluex.agent.pathfinder

import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ExplosionParticleInfo
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagLoader
import net.minecraft.util.random.WeightedList
import net.minecraft.world.Difficulty
import net.minecraft.world.TickRateManager
import net.minecraft.world.attribute.EnvironmentAttributeSystem
import net.minecraft.world.clock.ClockManager
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart
import net.minecraft.world.entity.player.Player
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.crafting.RecipeAccess
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ExplosionDamageCalculator
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkSource
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.entity.LevelEntityGetter
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.WritableLevelData
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.ticks.BlackholeTickAccess
import net.minecraft.world.ticks.LevelTickAccess
import java.util.UUID
import java.util.function.Predicate

/**
 * A server-side [Level] over the blocks of [world], so entities such as players move through a [TestWorld] as they
 * do in the game. It holds the [Entity]s [add]ed to it for entity lookups; nothing in it ever ticks by itself, and
 * what a game changes in a level (blocks, sounds, particles) is dropped.
 *
 * Call [MinecraftBootstrap.ensureInitialized] before the test class first names [TestLevel]: loading [Level] already
 * needs the registries.
 *
 * @agent claude-opus-5-5
 */
public class TestLevel(private val world: TestWorld) : Level(
    LEVEL_DATA,
    Level.OVERWORLD,
    registries,
    registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD),
    false,
    false,
    0L,
    0,
) {

    private val members = ArrayList<Entity>()
    private val border = WorldBorder()
    private val plains: Holder<Biome> = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)
    private val tickRates = TickRateManager()
    private val attributes = EnvironmentAttributeSystem.builder().build()

    /**
     * Gives [entity] the next id and lets [getEntities] find it.
     */
    public fun add(entity: Entity): TestLevel = apply {
        members += entity
        entity.setId(members.size)
    }

    /**
     * A survival player standing still at [feet], facing [yRot], added to this level.
     */
    @JvmOverloads
    public fun player(feet: Vec3, yRot: Float = 0f): Player {
        val player = object : Player(this, GameProfile(UUID.randomUUID(), "Tester")) {
            override fun gameMode(): GameType = GameType.SURVIVAL
        }
        player.snapTo(feet, yRot, 0f)
        player.setOnGround(true)
        add(player)
        return player
    }

    override fun getBlockState(pos: BlockPos): BlockState = world.getBlockState(pos)

    override fun getFluidState(pos: BlockPos): FluidState = world.getFluidState(pos)

    override fun getChunkForCollisions(chunkX: Int, chunkZ: Int): BlockGetter = world

    override fun hasChunk(chunkX: Int, chunkZ: Int): Boolean = true

    // shortcut: no chunks, so entities never find fluids; give it chunks when a test needs water
    override fun getChunk(chunkX: Int, chunkZ: Int, status: ChunkStatus, loadOrGenerate: Boolean): ChunkAccess? = null

    override fun isLoaded(pos: BlockPos): Boolean = true

    override fun getEntities(except: Entity?, box: AABB, predicate: Predicate<in Entity>): List<Entity> =
        members.filter { it !== except && it.boundingBox.intersects(box) && predicate.test(it) }

    override fun setBlock(pos: BlockPos, state: BlockState, flags: Int, maxUpdateDepth: Int): Boolean = false

    override fun sendBlockUpdated(pos: BlockPos, old: BlockState, new: BlockState, flags: Int) {}

    override fun playSeededSound(
        except: Entity?,
        x: Double,
        y: Double,
        z: Double,
        sound: Holder<SoundEvent>,
        source: SoundSource,
        volume: Float,
        xRot: Float,
        seed: Long,
    ) {}

    override fun playSeededSound(
        except: Entity?,
        from: Entity,
        sound: Holder<SoundEvent>,
        source: SoundSource,
        volume: Float,
        xRot: Float,
        seed: Long,
    ) {}

    override fun explode(
        source: Entity?,
        damageSource: DamageSource?,
        damageCalculator: ExplosionDamageCalculator?,
        x: Double,
        y: Double,
        z: Double,
        r: Float,
        fire: Boolean,
        interactionType: ExplosionInteraction,
        smallExplosionParticles: ParticleOptions,
        largeExplosionParticles: ParticleOptions,
        blockParticles: WeightedList<ExplosionParticleInfo>,
        explosionSound: Holder<SoundEvent>,
    ) {}

    override fun addParticle(
        particle: ParticleOptions,
        x: Double,
        y: Double,
        z: Double,
        xd: Double,
        yd: Double,
        zd: Double,
    ) {}

    override fun levelEvent(source: Entity?, type: Int, pos: BlockPos, data: Int) {}

    override fun gameEvent(event: Holder<GameEvent>, position: Vec3, context: GameEvent.Context) {}

    override fun destroyBlockProgress(id: Int, blockPos: BlockPos, progress: Int) {}

    override fun gatherChunkSourceStats(): String = "TestLevel"

    override fun setRespawnData(respawnData: LevelData.RespawnData) {}

    override fun getRespawnData(): LevelData.RespawnData = LevelData.RespawnData.DEFAULT

    override fun getEntity(id: Int): Entity? = members.firstOrNull { it.id == id }

    override fun dragonParts(): Collection<EnderDragonPart> = emptyList()

    override fun tickRateManager(): TickRateManager = tickRates

    override fun getMapData(id: MapId): MapItemSavedData? = null

    override fun getScoreboard(): Scoreboard = unsupported()

    override fun recipeAccess(): RecipeAccess = unsupported()

    override fun getEntities(): LevelEntityGetter<Entity> = unsupported()

    override fun clockManager(): ClockManager = unsupported()

    override fun environmentAttributes(): EnvironmentAttributeSystem = attributes

    override fun getChunkSource(): ChunkSource = unsupported()

    override fun getBlockTicks(): LevelTickAccess<Block> = BlackholeTickAccess.emptyLevelList()

    override fun getFluidTicks(): LevelTickAccess<Fluid> = BlackholeTickAccess.emptyLevelList()

    override fun players(): List<Player> = members.filterIsInstance<Player>()

    override fun getWorldBorder(): WorldBorder = border

    override fun getUncachedNoiseBiome(quartX: Int, quartY: Int, quartZ: Int): Holder<Biome> = plains

    override fun getSeaLevel(): Int = SEA_LEVEL

    override fun enabledFeatures(): FeatureFlagSet = FeatureFlags.DEFAULT_FLAGS

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not in a TestLevel")

    public companion object {
        private const val SEA_LEVEL = 63

        /**
         * The vanilla registries with their data pack entries, as a server loads them before its first level.
         */
        private val registries: RegistryAccess by lazy {
            MinecraftBootstrap.ensureInitialized()
            val vanilla = ServerPacksSource.createVanillaPackSource().fullResources()
            val resources = MultiPackResourceManager(PackType.SERVER_DATA, listOf(vanilla))
            val layers = RegistryLayer.createRegistryAccess()
            val tags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC))
            val lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLD), tags)
            val world = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run)
                .join()
            layers.replaceFrom(RegistryLayer.WORLD, world).compositeAccess()
        }

        private val LEVEL_DATA = object : WritableLevelData {
            override fun getRespawnData(): LevelData.RespawnData = LevelData.RespawnData.DEFAULT

            override fun setSpawn(respawnData: LevelData.RespawnData) {}

            override fun getGameTime(): Long = 0L

            override fun isHardcore(): Boolean = false

            override fun getDifficulty(): Difficulty = Difficulty.NORMAL

            override fun isDifficultyLocked(): Boolean = false
        }
    }
}
