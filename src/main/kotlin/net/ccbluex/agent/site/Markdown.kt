package net.ccbluex.agent.site

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import java.net.URI
import java.net.URISyntaxException

private const val BLOB = "https://github.com/CCBlueX/Minecraft-Agent/blob/main/"
private const val RAW = "https://raw.githubusercontent.com/CCBlueX/Minecraft-Agent/main/"
private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

private val extensions = listOf(
    TablesExtension.create(),
    AutolinkExtension.create(),
    HeadingAnchorExtension.create(),
    YamlFrontMatterExtension.create(),
)
private val parser = Parser.builder().extensions(extensions).build()

/**
 * Renders the Markdown file at [path] in the main branch. Front matter is dropped, and links relative to the file
 * point at it on GitHub, as they do when GitHub shows the file.
 *
 * @agent claude-opus-5-5
 */
fun markdownToHtml(markdown: String, path: String): String =
    HtmlRenderer.builder()
        .extensions(extensions)
        .attributeProviderFactory { RepositoryLinks(path) }
        .build()
        .render(parser.parse(markdown))

/**
 * Points relative links at the GitHub page of their target and relative images at its raw file.
 *
 * @agent claude-opus-5-5
 */
private class RepositoryLinks(private val path: String) : AttributeProvider {
    override fun setAttributes(node: Node, tagName: String, attributes: MutableMap<String, String>) {
        when (node) {
            is Link -> attributes.computeIfPresent("href") { _, href -> onGitHub(BLOB, href) }
            is Image -> attributes.computeIfPresent("src") { _, src -> onGitHub(RAW, src) }
        }
    }

    private fun onGitHub(base: String, target: String): String {
        if (target.isEmpty() || target.startsWith("#") || target.startsWith("//") || scheme.containsMatchIn(target)) {
            return target
        }
        return try {
            // GitHub reads a leading slash as the repository root
            if (target.startsWith("/")) URI(base).resolve(target.drop(1)).toString()
            else URI(base + path).resolve(target).toString()
        } catch (_: URISyntaxException) {
            target
        }
    }
}
