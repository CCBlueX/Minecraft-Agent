package net.ccbluex.agent.site

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.roundToLong

/** @agent claude-opus-5-5 */
@Serializable
data class Tokens(val input: Long, val cachedInput: Long, val cacheWrite: Long, val output: Long) {
    val total get() = input + cachedInput + cacheWrite + output

    operator fun plus(other: Tokens) = Tokens(
        input + other.input,
        cachedInput + other.cachedInput,
        cacheWrite + other.cacheWrite,
        output + other.output,
    )
}

/**
 * What one model used, in a change or summed over all of them. [usd] is the cost at the provider's list price.
 *
 * @agent claude-opus-5-5
 */
@Serializable
data class ModelUsage(val model: String, val provider: String, val tokens: Tokens, val usd: Double)

/**
 * One file in `ai-usage/`: the AI usage of one change.
 *
 * @agent claude-opus-5-5
 */
@Serializable
data class Usage(val date: String, val change: String, val tool: String, val models: List<ModelUsage>) {
    val usd get() = models.sumOf { it.usd }
}

/**
 * The content of `/ai-usage.json`, for scripts.
 *
 * @agent claude-opus-5-5
 */
@Serializable
data class Summary(val usd: Double, val changes: Int, val first: String?, val last: String?, val models: List<ModelUsage>)

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

/**
 * The usage files in [dir], the newest first; none when the folder is missing.
 *
 * @agent claude-opus-5-5
 */
fun readUsages(dir: Path): List<Usage> {
    if (!dir.isDirectory()) return emptyList()
    return dir.listDirectoryEntries("*.json")
        .filter { it.name != "schema.json" }
        .sortedByDescending { it.name }
        .map { json.decodeFromString<Usage>(it.readText()) }
        .sortedByDescending { it.date }
}

/**
 * Sums of [usages] per model and provider, the most expensive first.
 *
 * @agent claude-opus-5-5
 */
fun totals(usages: List<Usage>): List<ModelUsage> =
    usages.flatMap { it.models }
        .groupBy { it.model to it.provider }
        .map { (key, models) ->
            ModelUsage(key.first, key.second, models.map { it.tokens }.reduce(Tokens::plus), cents(models.sumOf { it.usd }))
        }
        .filter { it.usd > 0 }
        .sortedByDescending { it.usd }

/** @agent claude-opus-5-5 */
fun summary(usages: List<Usage>): String {
    val models = totals(usages)
    val summary = Summary(
        usd = cents(models.sumOf { it.usd }),
        changes = usages.size,
        first = usages.minOfOrNull { it.date },
        last = usages.maxOfOrNull { it.date },
        models = models,
    )
    return json.encodeToString(summary) + "\n"
}

/** @agent claude-opus-5-5 */
private fun cents(usd: Double) = (usd * 100).roundToLong() / 100.0
