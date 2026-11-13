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

package net.ccbluex.agent.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Keeps client-only Minecraft packages out of a shared module: neither its sources nor its compiled
 * classes (strings included) may name them.
 *
 * @agent claude-opus-5-5
 */
abstract class ClientClassCheck : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val problems = mutableListOf<String>()
        for (file in sources.asFileTree.files.sorted()) {
            file.readLines().forEachIndexed { index, line ->
                clientPackages.firstOrNull { it in line }?.let { problems += "${file.path}:${index + 1}: $it" }
            }
        }
        for (file in classes.asFileTree.matching { include("**/*.class") }.files.sorted()) {
            val content = String(file.readBytes(), Charsets.ISO_8859_1)
            clientPackages.firstOrNull { it in content || it.replace('.', '/') in content }
                ?.let { problems += "${file.path}: $it" }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Client-only classes in a shared module:\n" + problems.joinToString("\n"))
        }
    }

    private companion object {
        val clientPackages = listOf("net.minecraft.client", "com.mojang.blaze3d")
    }
}
