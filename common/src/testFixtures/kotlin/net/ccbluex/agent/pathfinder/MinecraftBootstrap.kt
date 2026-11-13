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

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

/**
 * Bootstraps the vanilla registries once per JVM, for tests that read blocks or items.
 *
 * @agent claude-opus-5-5
 */
public object MinecraftBootstrap {

    private val bootstrapped by lazy {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    public fun ensureInitialized() {
        bootstrapped
    }

}
