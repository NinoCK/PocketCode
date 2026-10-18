package io.github.ninock.pocketcode.data

import io.github.ninock.pocketcode.model.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneOffset

class UsageStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun record(day: LocalDate, project: String, chat: String?, tokens: Long, cost: Double?) = UsageRecord(
        atMillis = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        projectId = project,
        projectName = "Project $project",
        chatId = chat,
        chatTitle = chat?.let { "Chat $it" },
        agent = "claude-code",
        usage = TokenUsage(inputTokens = tokens, outputTokens = 1, costUsd = cost, model = "sonnet"),
        source = if (chat == null) UsageSource.GIT_DRAFT else UsageSource.CHAT,
    )

    @Test
    fun appendsAndLoadsRecords() {
        val store = UsageStore(folder.root)
        val first = record(LocalDate.of(2026, 10, 1), "p1", "c1", 100, 0.5)
        val second = record(LocalDate.of(2026, 10, 2), "p1", null, 20, null)
        store.append(first)
        store.append(second)
        assertEquals(listOf(first, second), UsageStore(folder.root).load())
    }

    @Test
    fun skipsCorruptLines() {
        assertNull(UsageStore.decode("{not json"))
        assertNull(UsageStore.decode(""))
    }

    @Test
    fun aggregatesByDayProjectAndChat() {
        val zone = ZoneOffset.UTC
        val oct1 = LocalDate.of(2026, 10, 1)
        val records = listOf(
            record(oct1, "p1", "c1", 100, 0.5),
            record(oct1, "p1", "c2", 50, 0.25),
            record(oct1.plusDays(2), "p2", "c3", 10, null),
        )
        val days = UsageAggregation.byDay(records, oct1, oct1.plusDays(2), zone)
        assertEquals(3, days.size)
        assertEquals(2, days[0].second.runs)
        assertEquals(0.75, days[0].second.usage.costUsd!!, 1e-9)
        assertEquals(0, days[1].second.runs)
        assertEquals(11L, days[2].second.usage.totalTokens)

        val projects = UsageAggregation.byProject(records)
        assertEquals(152L, projects.getValue("p1").usage.totalTokens)
        assertNull(projects.getValue("p2").usage.costUsd)

        val chats = UsageAggregation.byChat(records.filter { it.projectId == "p1" })
        assertEquals(101L, chats.getValue("c1").usage.totalTokens)
    }
}
