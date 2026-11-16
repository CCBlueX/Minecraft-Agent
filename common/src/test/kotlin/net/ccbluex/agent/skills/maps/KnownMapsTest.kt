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

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @agent claude-opus-5-5
 */
internal class KnownMapsTest {

    private val samples = (0 until 40).map { KnownMap.Sample(it, 64, 0, if (it % 3 == 0) "minecraft:air" else "minecraft:stone") }
    private val level = samples.associate { Triple(it.x, it.y, it.z) to it.block }

    @Test
    fun `the same blocks are alike, open sky counts for nothing`() {
        val (alike, compared) = Fingerprint.similarity(samples) { x, y, z -> level[Triple(x, y, z)] }
        assertEquals(1.0, alike)
        assertEquals(samples.count { it.block != "minecraft:air" }, compared)
    }

    @Test
    fun `unloaded places are left out, changed blocks lower the similarity`() {
        val changed = level.mapValues { (pos, block) -> if (pos.first < 4) "minecraft:barrier" else block }
        val (alike, compared) = Fingerprint.similarity(samples) { x, y, z -> if (x >= 30) null else changed[Triple(x, y, z)] }
        assertTrue(alike in 0.8..0.95, "a few changed blocks: $alike")
        assertEquals(samples.count { it.x < 30 && (it.block != "minecraft:air" || it.x < 4) }, compared)
    }

    @Test
    fun `a saved map is recognised, another world is not`(@TempDir folder: File) {
        val maps = KnownMaps(folder)
        val map = maps.create("Terra Swoop Force")
        map.samples += samples
        map.steps += KnownMap.Step("interact", KnownMap.Point(25.5, 55.0, 3.5), null, "minecraft:villager", "teleport", null, 4.0)
        maps.save(map)

        val loaded = KnownMaps(folder).apply { reload {} }
        assertEquals(listOf("terra-swoop-force"), loaded.maps.map { it.id })
        val found = assertNotNull(loaded.recognise { Fingerprint.similarity(it) { x, y, z -> level[Triple(x, y, z)] } })
        assertEquals("Terra Swoop Force", found.map.name)
        assertEquals(1, found.map.steps.size)
        assertNull(loaded.recognise { Fingerprint.similarity(it) { _, _, _ -> "minecraft:grass_block" } })
        assertNull(loaded.recognise { Fingerprint.similarity(it) { _, _, _ -> null } }, "nothing loaded yet")
    }

    @Test
    fun `a second map of the same name gets an id of its own`(@TempDir folder: File) {
        val maps = KnownMaps(folder)
        maps.create("Parkour Spiral")
        assertEquals("parkour-spiral-2", maps.create("Parkour Spiral").id)
    }

    @Test
    fun `a map is named by what the world shows before its resource pack`() {
        assertEquals("Terra Swoop Force", KnownMaps.nameOf(null, null, null, listOf("Start Game"), "Terra Swoop Force Resource Pack"))
        assertEquals("Geo Descent Labs", KnownMaps.nameOf(null, null, null, listOf("<Alan> Welcome to Geo Descent Labs!"), "Pack"))
        assertEquals("Parkour Spiral", KnownMaps.nameOf("Parkour Spiral", "SPIRAL", null, emptyList(), "Pack"))
        assertNull(KnownMaps.nameOf(null, null, null, listOf("welcome to the club"), null))
    }
}
