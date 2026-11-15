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

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.function.Consumer

/**
 * The maps known, one plain JSON file each in [folder]. A file edited by hand counts from the next [reload].
 *
 * @agent claude-opus-5-5
 * @agent claude-sonnet-5-5
 */
public class KnownMaps(private val folder: File) {

    public var maps: List<KnownMap> = emptyList()
        private set

    /**
     * Reads the folder again; [log] hears of the files that could not be read.
     */
    public fun reload(log: Consumer<String>) {
        maps = folder.listFiles { file -> file.extension == "json" }.orEmpty().sortedBy { it.name }.mapNotNull { file ->
            runCatching { file.reader().use { gson.fromJson(it, KnownMap::class.java) } }
                .onFailure { log.accept("known map ${file.name} not loaded: ${it.message}") }
                .getOrNull()
        }
    }

    /**
     * The known map most alike the level, by [similarity] of its samples, if alike enough.
     */
    internal fun recognise(similarity: (List<KnownMap.Sample>) -> Pair<Double, Int>): Recognised? = maps
        .map { map -> similarity(map.samples).let { (alike, compared) -> Recognised(map, alike, compared) } }
        .filter { it.compared >= MIN_COMPARED && it.similarity >= MIN_SIMILARITY }
        .maxByOrNull { it.similarity }

    /**
     * A new map named [name], with an id of its own.
     */
    internal fun create(name: String): KnownMap {
        val base = name.lowercase().replace(NOT_ID, "-").trim('-').ifEmpty { "map" }.take(MAX_ID)
        val taken = maps.map { it.id }.toSet()
        val id = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
        return KnownMap(id, name).also { maps = maps + it }
    }

    /**
     * Writes [map] at once; a reader never sees half a file.
     */
    internal fun save(map: KnownMap) {
        folder.mkdirs()
        val file = folder.resolve("${map.id}.json")
        val temporary = folder.resolve("${map.id}.json.tmp")
        temporary.writeText(gson.toJson(map))
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /**
     * @agent claude-opus-5-5
     */
    internal data class Recognised(val map: KnownMap, val similarity: Double, val compared: Int)

    private companion object {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val NOT_ID = Regex("""[^a-z0-9]+""")
        const val MAX_ID = 40

        /**
         * Fewer places compared tell nothing, as in a world whose chunks are still loading.
         */
        const val MIN_COMPARED = 24
        const val MIN_SIMILARITY = 0.8
    }
}
