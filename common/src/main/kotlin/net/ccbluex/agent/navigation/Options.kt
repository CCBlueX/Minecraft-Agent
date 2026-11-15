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
) {

    /**
     * Longest route searched, in blocks along the path.
     */
    internal val maxLength: Double
        get() = pathRange.toDouble()
}
