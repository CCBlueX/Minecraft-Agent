package net.ccbluex.agent.site

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val USAGE = "Usage: --main <main checkout> --api <Dokka HTML folder or none> --out <folder> " +
    "[--static <folder copied as is, such as clips/>]"
private const val SKILL = "SKILL.md"

/**
 * Writes the site for a checkout of the main branch. Missing inputs get a placeholder line on their page.
 *
 * @agent claude-opus-5-5
 */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { it.first() to it.getOrNull(1) }
    if (options.keys.any { it !in setOf("--main", "--api", "--out", "--static") } || null in options.values) usage()
    val main = Path(options["--main"] ?: usage())
    val out = Path(options["--out"] ?: usage())
    val api = options["--api"]?.takeIf { it != "none" }?.let(::Path)

    writeHome(main, out)
    writeApi(api, out)
    writeSkill(main, out)
    writeAiUsage(main, out)
    options["--static"]?.let { copyStatic(Path(it), out) }
}

/** @agent claude-opus-5-5 */
@OptIn(ExperimentalPathApi::class)
private fun copyStatic(static: Path, out: Path) {
    if (static.isDirectory()) {
        static.copyToRecursively(out, followLinks = false, overwrite = true)
    }
}

/** @agent claude-opus-5-5 */
private fun usage(): Nothing {
    System.err.println(USAGE)
    exitProcess(2)
}

/** @agent claude-opus-5-5 */
private fun writeHome(main: Path, out: Path) {
    val readme = main / "README.md"
    val body = if (readme.exists()) markdownToHtml(readme.readText(), "README.md") else "<p>No README yet.</p>\n"
    write(out / "index.html", page(null, 0, body))
}

/**
 * Copies [api], either one Dokka publication or a folder per module, which then gets an index of the modules.
 *
 * @agent claude-opus-5-5
 */
@OptIn(ExperimentalPathApi::class)
private fun writeApi(api: Path?, out: Path) {
    val index = out / "api" / "index.html"
    if (api == null || !api.isDirectory()) {
        return write(index, page("API", 1, "<h1>API</h1>\n<p>The API docs are not built yet.</p>\n"))
    }
    (out / "api").createParentDirectories()
    api.copyToRecursively(out / "api", followLinks = false, overwrite = true)
    if ((api / "index.html").exists()) return

    val modules = api.listDirectoryEntries().filter { (it / "index.html").exists() }.map { it.name }.sorted()
    val list = modules.joinToString("") { "<li><a href=\"$it/\">$it</a></li>\n" }
    write(index, page("API", 1, "<h1>API</h1>\n<ul>\n$list</ul>\n"))
}

/** @agent claude-opus-5-5 */
private fun writeSkill(main: Path, out: Path) {
    val skill = main / SKILL
    val body = if (skill.exists()) {
        skill.copyTo((out / "skill" / "SKILL.md").createParentDirectories(), overwrite = true)
        "<p><a href=\"SKILL.md\">SKILL.md</a> as plain Markdown, for agents.</p>\n" + markdownToHtml(skill.readText(), SKILL)
    } else {
        "<h1>Skill</h1>\n<p>The skill is not written yet.</p>\n"
    }
    write(out / "skill" / "index.html", page("Skill", 1, body))
}

/** @agent claude-opus-5-5 */
private fun writeAiUsage(main: Path, out: Path) {
    val usages = readUsages(main / "ai-usage")
    write(out / "ai-usage-dark.svg", card(usages, Theme.DARK))
    write(out / "ai-usage-light.svg", card(usages, Theme.LIGHT))
    write(out / "ai-usage.json", summary(usages))

    val body = StringBuilder("<h1>AI usage</h1>\n")
    if (usages.isEmpty()) {
        body.append("<p>No AI usage recorded yet.</p>\n")
    } else {
        body.append("<p><picture>\n")
        body.append("<source media=\"(prefers-color-scheme: dark)\" srcset=\"../ai-usage-dark.svg\">\n")
        body.append("<img alt=\"AI models used in Minecraft Agent and their cost\" src=\"../ai-usage-light.svg\">\n")
        body.append("</picture></p>\n")
        body.append("<table>\n<thead><tr><th>Date</th><th>Change</th><th>Models</th><th>Cost</th></tr></thead>\n<tbody>\n")
        for (usage in usages) {
            val models = usage.models.joinToString(", ") { it.model }
            body.append("<tr><td>${usage.date.escaped()}</td><td>${usage.change.escaped()}</td>")
            body.append("<td>${models.escaped()}</td><td>${dollars(usage.usd)}</td></tr>\n")
        }
        body.append("</tbody>\n</table>\n")
        body.append("<p>Totals per model as JSON: <a href=\"../ai-usage.json\">ai-usage.json</a></p>\n")
    }
    write(out / "ai-usage" / "index.html", page("AI usage", 1, body.toString()))
}

/** @agent claude-opus-5-5 */
private fun write(file: Path, text: String) {
    file.createParentDirectories()
    file.writeText(text)
}
