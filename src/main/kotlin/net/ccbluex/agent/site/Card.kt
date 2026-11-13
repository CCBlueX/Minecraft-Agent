package net.ccbluex.agent.site

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Colors of one card variant; [other] is the color of providers without their own.
 *
 * @agent claude-opus-5-5
 */
enum class Theme(val panel: String, val border: String, val text: String, val muted: String, val other: String) {
    DARK("#0d1117", "#30363d", "#e6edf3", "#8b949e", "#6e7681"),
    LIGHT("#ffffff", "#d0d7de", "#1f2328", "#59636e", "#8c959f"),
}

private val providerColors = mapOf(
    "anthropic" to "#d97757",
    "deepseek" to "#4d6bfe",
    "opencode-go" to "#e5484d",
    "github-copilot" to "#8957e5",
    "openai" to "#10a37f",
)

// Further models of a provider get lighter shades of its color
private val shades = listOf(1.0, 0.7, 0.5, 0.35)

private const val WIDTH = 560
private const val PAD = 20
private const val BAR = WIDTH - 2 * PAD
private const val FONT = "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif"

/**
 * The AI usage card as SVG: total cost, a bar of the models' shares, a row per model and the date range.
 *
 * @agent claude-opus-5-5
 */
fun card(usages: List<Usage>, theme: Theme): String {
    val models = totals(usages)
    val total = models.sumOf { it.usd }
    val body = StringBuilder()
    body.text(PAD, 34, "AI usage", theme.text, size = 14, weight = 600)

    val height = if (models.isEmpty()) {
        body.text(PAD, 62, "No usage recorded yet.", theme.muted)
        84
    } else {
        body.append("<text x=\"$PAD\" y=\"78\" fill=\"${theme.text}\" font-size=\"32\" font-weight=\"700\">")
            .append(dollars(total))
            .append("<tspan font-size=\"20\" dy=\"-10\" fill=\"${theme.muted}\">*</tspan></text>\n")
        body.text(PAD, 100, "* if billed at full API rate", theme.muted, size = 12)

        val fills = fills(models, theme)
        var start = 0.0
        body.append("<clipPath id=\"bar\"><rect x=\"$PAD\" y=\"116\" width=\"$BAR\" height=\"10\" rx=\"5\"/></clipPath>\n")
        body.append("<g clip-path=\"url(#bar)\">\n")
        models.forEachIndexed { index, model ->
            val share = if (total > 0) model.usd / total else 0.0
            val from = (start * BAR).roundToInt()
            start += share
            val gap = if (index == models.lastIndex) 0 else 2
            val width = (start * BAR).roundToInt() - from - gap
            if (width > 0) {
                body.append("<rect x=\"${PAD + from}\" y=\"116\" width=\"$width\" height=\"10\" ${fills[index]}/>\n")
            }
        }
        body.append("</g>\n")

        body.text(36, 152, "Model", theme.muted, size = 11)
        body.text(220, 152, "Provider", theme.muted, size = 11)
        body.text(400, 152, "Cost", theme.muted, size = 11, end = true)
        body.text(466, 152, "Share", theme.muted, size = 11, end = true)
        body.text(WIDTH - PAD, 152, "Tokens", theme.muted, size = 11, end = true)

        var y = 152
        models.forEachIndexed { index, model ->
            y += 24
            val share = if (total > 0) model.usd / total else 0.0
            body.append("<circle cx=\"${PAD + 5}\" cy=\"${y - 4.5}\" r=\"4.5\" ${fills[index]}/>\n")
            body.text(36, y, model.model.cut(25), theme.text)
            body.text(220, y, model.provider.cut(16), theme.muted)
            body.text(400, y, dollars(model.usd), theme.text, end = true)
            body.text(466, y, percent(share), theme.muted, end = true)
            body.text(WIDTH - PAD, y, abbreviated(model.tokens.total), theme.muted, end = true)
        }

        body.append("<line x1=\"$PAD\" y1=\"${y + 14}\" x2=\"${WIDTH - PAD}\" y2=\"${y + 14}\" stroke=\"${theme.border}\"/>\n")
        val first = usages.minOf { it.date }
        val last = usages.maxOf { it.date }
        val changes = if (usages.size == 1) "1 change" else "${usages.size} changes"
        body.text(PAD, y + 36, if (first == last) "$changes, $first" else "$changes, $first to $last", theme.muted, size = 12)
        y + 52
    }

    return buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$WIDTH\" height=\"$height\" ")
        append("viewBox=\"0 0 $WIDTH $height\" font-family=\"$FONT\" font-size=\"13\" ")
        append("style=\"font-variant-numeric: tabular-nums\" role=\"img\">\n")
        val title = if (models.isEmpty()) "none recorded yet" else "${dollars(total)}* (* if billed at full API rate)"
        append("<title>AI usage: $title</title>\n")
        append("<rect x=\"0.5\" y=\"0.5\" width=\"${WIDTH - 1}\" height=\"${height - 1}\" rx=\"10\" ")
        append("fill=\"${theme.panel}\" stroke=\"${theme.border}\"/>\n")
        append(body)
        append("</svg>\n")
    }
}

/**
 * Fill attributes per model: the provider's color, lighter for each further model of the same provider.
 *
 * @agent claude-opus-5-5
 */
private fun fills(models: List<ModelUsage>, theme: Theme): List<String> {
    val seen = mutableMapOf<String, Int>()
    return models.map { model ->
        val index = seen.merge(model.provider, 1, Int::plus)!! - 1
        val color = providerColors[model.provider] ?: theme.other
        "fill=\"$color\" fill-opacity=\"${shades[index.coerceAtMost(shades.lastIndex)]}\""
    }
}

/** @agent claude-opus-5-5 */
private fun StringBuilder.text(x: Int, y: Int, text: String, fill: String, size: Int = 13, weight: Int = 400, end: Boolean = false) {
    append("<text x=\"$x\" y=\"$y\" fill=\"$fill\"")
    if (size != 13) append(" font-size=\"$size\"")
    if (weight != 400) append(" font-weight=\"$weight\"")
    if (end) append(" text-anchor=\"end\"")
    append(">").append(text.escaped()).append("</text>\n")
}

/** @agent claude-opus-5-5 */
private fun String.cut(length: Int) = if (this.length <= length) this else take(length - 1) + "…"

/** @agent claude-opus-5-5 */
fun dollars(usd: Double) = "$" + "%,.2f".format(Locale.US, usd)

/** @agent claude-opus-5-5 */
private fun percent(share: Double) = when {
    share > 0 && share < 0.0005 -> "<0.1%"
    else -> "%.1f%%".format(Locale.US, share * 100)
}

/**
 * [count] with a K, M or B suffix: one decimal below 100 of the unit, none above.
 *
 * @agent claude-opus-5-5
 */
private fun abbreviated(count: Long): String {
    for ((size, unit) in listOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")) {
        if (count >= size) {
            val value = count.toDouble() / size
            return (if (value < 100) "%.1f" else "%.0f").format(Locale.US, value) + unit
        }
    }
    return count.toString()
}
