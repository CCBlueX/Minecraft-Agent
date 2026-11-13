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
 * Checks that every source file starts with the Minecraft Agent license header. The copyright line may name a
 * year range.
 *
 * @agent claude-opus-5-5
 */
abstract class LicenseHeaderCheck : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val missing = sources.files.sorted().filterNot { file ->
            val lines = file.useLines { it.take(header.size).toList() }
            lines.size == header.size && header.indices.all { header[it].matches(lines[it]) }
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Source files without the license header (copy it from any other source file):\n" +
                    missing.joinToString("\n") { it.path },
            )
        }
    }

    private companion object {
        val header = listOf(
            "/*",
            " * This file is part of Minecraft Agent (https://github.com/CCBlueX/Minecraft-Agent)",
            " *",
            """ \* Copyright \(c\) \d{4}( - \d{4})? CCBlueX""",
            " *",
            " * Minecraft Agent is free software: you can redistribute it and/or modify",
            " * it under the terms of the GNU General Public License as published by",
            " * the Free Software Foundation, either version 3 of the License, or",
            " * (at your option) any later version.",
            " *",
            " * Minecraft Agent is distributed in the hope that it will be useful,",
            " * but WITHOUT ANY WARRANTY; without even the implied warranty of",
            " * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the",
            " * GNU General Public License for more details.",
            " *",
            " * You should have received a copy of the GNU General Public License",
            " * along with Minecraft Agent. If not, see <https://www.gnu.org/licenses/>.",
            " */",
        ).mapIndexed { index, line -> if (index == 3) Regex(line) else Regex(Regex.escape(line)) }
    }
}
