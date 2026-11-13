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

package net.ccbluex.agent.v26_3.paper

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale
import net.ccbluex.agent.bukkit.BotSkin
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory

/**
 * The skins of Minecraft accounts by name, looked up from Mojang off the server thread and kept for the session.
 *
 * @agent claude-opus-5-5
 */
internal class Skins(private val plugin: Plugin) {

    private val skins = HashMap<String, BotSkin>()
    private val lookups = HashSet<String>()
    private val waiting = HashMap<String, MutableList<(BotSkin) -> Unit>>()
    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    fun cached(name: String): BotSkin? = skins[name.lowercase(Locale.ROOT)]

    /**
     * Hands the skin of account [name] to [then] on the server thread once it is known; never when there is none.
     */
    fun lookUp(name: String, then: (BotSkin) -> Unit) {
        val key = name.lowercase(Locale.ROOT)
        skins[key]?.let {
            then(it)
            return
        }
        waiting.getOrPut(key) { ArrayList() } += then
        if (!lookups.add(key)) return
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val skin = fetchOrWarn(name)
            plugin.server.scheduler.runTask(plugin, Runnable { found(key, skin) })
        })
    }

    private fun fetchOrWarn(name: String): BotSkin? {
        try {
            val skin = fetch(name)
            if (skin == null) LOGGER.warn("No Minecraft account named {} for a bot skin", name)
            return skin
        } catch (e: IOException) {
            LOGGER.warn("Could not look up the skin of {}: {}", name, e.toString())
        } catch (e: RuntimeException) {
            LOGGER.warn("Could not look up the skin of {}: {}", name, e.toString())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return null
    }

    private fun found(key: String, skin: BotSkin?) {
        lookups.remove(key)
        val callbacks = waiting.remove(key) ?: return
        if (skin == null) return
        skins[key] = skin
        callbacks.forEach { it(skin) }
    }

    private fun fetch(name: String): BotSkin? {
        val profile = json(PROFILE_URL + URLEncoder.encode(name, StandardCharsets.UTF_8)) ?: return null
        val id = profile.get("id")?.asString ?: return null
        val session = json("$SESSION_URL$id?unsigned=false") ?: return null
        val properties = session.getAsJsonArray("properties") ?: return null
        for (element in properties) {
            val property = element.asJsonObject
            if (property.get("name")?.asString == "textures" && property.has("signature")) {
                return BotSkin(property.get("value").asString, property.get("signature").asString)
            }
        }
        return null
    }

    private fun json(url: String): JsonObject? {
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == NO_CONTENT || response.statusCode() == NOT_FOUND) return null
        if (response.statusCode() != OK) throw IOException("HTTP ${response.statusCode()} from $url")
        return JsonParser.parseString(response.body()).asJsonObject
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("Agent/Skins")
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        const val PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/"
        const val SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/"
        const val OK = 200
        const val NO_CONTENT = 204
        const val NOT_FOUND = 404
    }
}
