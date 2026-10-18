package io.github.ninock.pocketcode.data

import io.github.ninock.pocketcode.model.TokenUsage
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class UsageSource { CHAT, GIT_DRAFT }

/** One agent run's usage, attributed to the project and chat it ran in. */
data class UsageRecord(
    val atMillis: Long,
    val projectId: String,
    val projectName: String,
    /** Null for work outside a chat, such as drafting a commit message. */
    val chatId: String?,
    val chatTitle: String?,
    val agent: String,
    val usage: TokenUsage,
    val source: UsageSource = UsageSource.CHAT,
) {
    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
}

/** Totals for a group of runs. */
data class UsageTotals(val usage: TokenUsage = TokenUsage(), val runs: Int = 0) {
    operator fun plus(record: UsageRecord) = UsageTotals(usage + record.usage, runs + 1)
}

internal object UsageAggregation {
    fun total(records: Iterable<UsageRecord>): UsageTotals = records.fold(UsageTotals(), UsageTotals::plus)

    /** Every day from [from] to [to] inclusive, including days with no usage. */
    fun byDay(records: Iterable<UsageRecord>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, UsageTotals>> {
        val grouped = records.groupBy { it.day(zone) }
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .map { day -> day to total(grouped[day].orEmpty()) }
            .toList()
    }

    fun byProject(records: Iterable<UsageRecord>): Map<String, UsageTotals> =
        records.groupBy { it.projectId }.mapValues { (_, list) -> total(list) }

    /** Keyed by chat id; work outside a chat is keyed by null. */
    fun byChat(records: Iterable<UsageRecord>): Map<String?, UsageTotals> =
        records.groupBy { it.chatId }.mapValues { (_, list) -> total(list) }
}

/** Append-only log of agent usage, kept in the app's private storage. */
class UsageStore(directory: File) {
    private val file = File(directory, "usage/usage.jsonl")

    @Synchronized
    fun append(record: UsageRecord) {
        file.parentFile?.mkdirs()
        file.appendText(encode(record).toString() + "\n")
        if (file.length() > MAX_BYTES) compact()
    }

    @Synchronized
    fun load(): List<UsageRecord> {
        if (!file.isFile) return emptyList()
        return file.useLines { lines -> lines.mapNotNull(::decode).toList() }
    }

    private fun compact() {
        val kept = file.readLines().takeLast(KEEP_AFTER_COMPACT)
        val temporary = File(file.parentFile, ".usage.jsonl.tmp")
        temporary.writeText(kept.joinToString("\n", postfix = "\n"))
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    internal companion object {
        private const val MAX_BYTES = 8L * 1024 * 1024
        private const val KEEP_AFTER_COMPACT = 30_000

        fun encode(record: UsageRecord): JSONObject = JSONObject().apply {
            put("at", record.atMillis)
            put("project", record.projectId)
            put("projectName", record.projectName)
            record.chatId?.let { put("chat", it) }
            record.chatTitle?.let { put("chatTitle", it) }
            put("agent", record.agent)
            put("source", record.source.name)
            put("in", record.usage.inputTokens)
            put("out", record.usage.outputTokens)
            put("cacheRead", record.usage.cacheReadTokens)
            put("cacheWrite", record.usage.cacheWriteTokens)
            record.usage.costUsd?.let { put("cost", it) }
            record.usage.model?.let { put("model", it) }
        }

        fun decode(line: String): UsageRecord? = runCatching {
            if (line.isBlank()) return null
            val json = JSONObject(line)
            UsageRecord(
                atMillis = json.getLong("at"),
                projectId = json.getString("project"),
                projectName = json.optString("projectName"),
                chatId = json.optString("chat").takeIf(String::isNotBlank),
                chatTitle = json.optString("chatTitle").takeIf(String::isNotBlank),
                agent = json.optString("agent"),
                source = runCatching { UsageSource.valueOf(json.optString("source")) }.getOrDefault(UsageSource.CHAT),
                usage = TokenUsage(
                    inputTokens = json.optLong("in"),
                    outputTokens = json.optLong("out"),
                    cacheReadTokens = json.optLong("cacheRead"),
                    cacheWriteTokens = json.optLong("cacheWrite"),
                    costUsd = if (json.has("cost")) json.optDouble("cost") else null,
                    model = json.optString("model").takeIf(String::isNotBlank),
                ),
            )
        }.getOrNull()
    }
}
