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

package net.ccbluex.agent.skills.maps

import net.minecraft.world.phys.Vec3

/**
 * What the bot learnt about a map by playing it, stored as `<id>.json`: how to tell it again
 * ([samples], [signs]), the [steps] that moved the story on, the [courses] flown or walked, where it touched
 * something ([hazards]), what the map asks of the player ([asks]) and the best times.
 *
 * Gson fills the fields without the constructor; a field newer than a file is null.
 *
 * @agent claude-opus-5-5
 */
public class KnownMap(
    public val id: String,
    public var name: String,
    public val samples: MutableList<Sample> = ArrayList(),
    public val signs: MutableList<Sign> = ArrayList(),
    public val steps: MutableList<Step> = ArrayList(),
    public val courses: MutableList<Course> = ArrayList(),
    public val hazards: MutableList<Point> = ArrayList(),
    public val asks: MutableSet<String> = LinkedHashSet(),
    /**
     * Seconds per course, by its index, and of the whole entry sequence (`entry`).
     */
    public val best: MutableMap<String, Double> = LinkedHashMap(),
    /**
     * Whether a run got to the end of the map.
     */
    public var complete: Boolean = false,
) {

    /**
     * A block seen at a place: the map is told again by the blocks at the same places.
     *
     * @agent claude-opus-5-5
     */
    public data class Sample(public val x: Int, public val y: Int, public val z: Int, public val block: String)

    /**
     * @agent claude-opus-5-5
     */
    public data class Sign(public val x: Int, public val y: Int, public val z: Int, public val text: String)

    /**
     * A point in the level, rounded to a tenth of a block in the file.
     *
     * @agent claude-opus-5-5
     */
    public data class Point(public val x: Double, public val y: Double, public val z: Double) {
        public fun toVec3(): Vec3 = Vec3(x, y, z)

        public companion object {
            public fun of(pos: Vec3): Point = Point(round(pos.x), round(pos.y), round(pos.z))

            private fun round(value: Double) = Math.round(value * 10) / 10.0
        }
    }

    /**
     * One thing done that moved the map on: [action] at [at] (the player's place), on the [entity] type or block at
     * [target], and the [effect] it had, [seconds] after the step began. A `wait` step waited for chat containing
     * [until].
     *
     * @agent claude-opus-5-5
     */
    public data class Step(
        public val action: String,
        public val at: Point,
        public val target: Point?,
        public val entity: String?,
        public val effect: String,
        public val until: String?,
        public val seconds: Double,
    )

    /**
     * A flight or walk from [start] along [points] to [end]; [seconds] for the best clean run, [deaths] on the way
     * while learning it.
     *
     * @agent claude-opus-5-5
     */
    public data class Course(
        public val start: Point,
        public val end: Point,
        public val points: List<Point>,
        public val seconds: Double,
        public val deaths: Int,
    )

    public companion object {
        public const val ASK_GLIDE: String = "glide-no-contact"
    }
}
