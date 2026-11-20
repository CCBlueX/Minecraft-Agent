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

package net.ccbluex.agent.navigation

import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets

/**
 * What a [Navigator] may do on its way and how it looks; read every tick, so a new copy takes effect at once.
 *
 * @agent claude-opus-5-5
 * @agent claude-fable-5-2
 */
public data class Options(
    public val canBreak: Boolean = false,
    public val canPlace: Boolean = false,
    public val canParkour: Boolean = true,
    public val riskyJumps: Boolean = false,

    /**
     * Highest drop onto ground, in blocks, on top of what the safe fall distance has over the vanilla one.
     */
    public val maxFall: Int = 3,

    /**
     * Longest route searched, in blocks along the path.
     */
    public val pathRange: Int = 64,
    public val canFly: Boolean = true,
    public val canElytra: Boolean = true,
    public val elytraRockets: Boolean = true,

    /**
     * Whether the camera turns the way navigation goes.
     */
    public val lookAhead: Boolean = true,

    /**
     * Fastest camera turn, in degrees per tick.
     */
    public val lookAheadTurnSpeed: Float = 10f,

    /**
     * How far along the route the point looked at lies, in blocks.
     */
    public val lookAheadDistance: Float = 4f,

    /**
     * Degrees downwards from the horizon.
     */
    public val lookAheadXRot: Float = 8f,

    /**
     * Whether another feature runs level bridges, so searches count them as quick to cross.
     */
    public val fastBridges: Boolean = false,

    /**
     * Whether that feature places the floor of bridges now, so the walk only runs across them.
     */
    public val placesBridgeFloors: Boolean = false,

    /**
     * The blocks the walk places.
     */
    public val placeableBlocks: PlaceableBlocks = PlaceableBlocks.DEFAULT,

    /**
     * Whether the player may click what it does not see: place against or use a block face another block hides, dig
     * through one, hit an entity behind a wall. Off, such a click is refused and not sent.
     */
    public val canClickThroughBlocks: Boolean = false,

    /**
     * Blocks a glide in the air keeps between the body and every block wherever there is room; 0 for none.
     */
    public val glideClearance: Double = 0.0,

    /**
     * Fewest ticks a movement key stays down once pressed on a plain walk, as a person's tap lasts; 0 for no
     * minimum. Precise moves such as jumps, climbing and block actions press their keys exactly.
     */
    public val minPressTicks: Int = 0,

    /**
     * Fewest ticks a movement key stays up on a plain walk before it is pressed again; 0 for no minimum.
     */
    public val minReleaseTicks: Int = 0,

    /**
     * How the walk crosses the bridges it places: one method, Ninja where that one cannot, or [Bridging.AUTO].
     */
    public val bridging: Bridging = Bridging.AUTO,

    /**
     * Whether the walk steps onto bridge blocks before the server keeps them, and [Bridging.AUTO] may pick methods
     * that fall on one slip, such as godbridging.
     */
    public val riskyBridges: Boolean = false,

    /**
     * The blocks the walk and the agent's digging may break while [canBreak].
     */
    public val breakableBlocks: BreakableBlocks = BreakableBlocks.ANY,

    /**
     * Whether water the walk would swim across is crossed in a boat from the inventory: placed at the shore, ridden
     * to the far one and taken along again.
     */
    public val canBoat: Boolean = false,

    /**
     * Whether the player eats the best plain food it carries once hungry, walking or riding, but never while it
     * places or digs blocks on the way.
     */
    public val canEat: Boolean = false,

    /**
     * Cells, as [net.ccbluex.agent.math.BlockPos.asLong], the walk never puts a block in while [canPlace], such as
     * those a build is still to fill.
     */
    public val reserved: LongSet = LongSets.EMPTY_SET,
) {

    /**
     * Longest route searched, in blocks along the path.
     */
    internal val maxLength: Double
        get() = pathRange.toDouble()
}
