package net.ccbluex.agent.site

private const val SITE = "Minecraft Agent"

/**
 * A plain HTML page [depth] folders below the site root, with the nav line on top. Links are relative, so the site
 * works under any path.
 *
 * @agent claude-opus-5-5
 */
fun page(title: String?, depth: Int, body: String): String {
    val root = "../".repeat(depth)
    return buildString {
        append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<meta name=\"color-scheme\" content=\"light dark\">\n")
        append("<title>").append(if (title == null) SITE else "${title.escaped()} - $SITE").append("</title>\n")
        append("</head>\n<body>\n")
        append("<nav><a href=\"${root.ifEmpty { "./" }}\">Home</a> | <a href=\"${root}api/\">API</a> | ")
        append("<a href=\"${root}skill/\">Skill</a> | <a href=\"${root}ai-usage/\">AI usage</a></nav>\n")
        append("<main>\n").append(body).append("</main>\n</body>\n</html>\n")
    }
}

/** @agent claude-opus-5-5 */
fun String.escaped() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
