package app.okusurikiroku

import app.okusurikiroku.data.IntakeLog
import app.okusurikiroku.data.Medication
import app.okusurikiroku.domain.DaySummary
import app.okusurikiroku.domain.entriesForDay
import app.okusurikiroku.domain.nextReminder
import app.okusurikiroku.domain.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class ScheduleTest {
    private val day = 20_000L
    private val a = Medication(id = 1, name = "A", times = listOf(480, 1140), startDay = day - 10)
    private val b = Medication(id = 2, name = "B", times = listOf(480), startDay = day - 10)

    @Test
    fun entriesAreSortedByTimeThenNameAndCarryLogs() {
        val logs = listOf(
            IntakeLog(medicationId = 1, day = day, minuteOfDay = 480, takenAt = 111),
            IntakeLog(medicationId = 2, day = day - 1, minuteOfDay = 480, takenAt = 222),
        )
        val entries = entriesForDay(day, listOf(b, a), logs)
        assertEquals(listOf("A" to 480, "B" to 480, "A" to 1140), entries.map { it.medication.name to it.minuteOfDay })
        assertEquals(listOf<Long?>(111, null, null), entries.map { it.takenAt })
        assertEquals(DaySummary(total = 3, taken = 1), summarize(entries))
    }

    @Test
    fun medicationIsScheduledOnlyBetweenStartAndArchive() {
        val med = Medication(id = 1, name = "A", times = listOf(480), startDay = day, archivedDay = day + 2)
        assertEquals(0, entriesForDay(day - 1, listOf(med), emptyList()).size)
        assertEquals(1, entriesForDay(day, listOf(med), emptyList()).size)
        assertEquals(1, entriesForDay(day + 1, listOf(med), emptyList()).size)
        assertEquals(0, entriesForDay(day + 2, listOf(med), emptyList()).size)
    }

    @Test
    fun medicationIsScheduledOnlyWithinItsPeriod() {
        val course = Medication(
            id = 1, name = "A", times = listOf(480), periodStart = day + 1, periodEnd = day + 3, startDay = day - 10,
        )
        assertEquals(
            listOf(0, 1, 1, 1, 0),
            (day..day + 4).map { entriesForDay(it, listOf(course), emptyList()).size },
        )
        // 終了日なし・開始日なし
        assertEquals(1, entriesForDay(day + 100, listOf(course.copy(periodEnd = null)), emptyList()).size)
        assertEquals(1, entriesForDay(day, listOf(course.copy(periodStart = null)), emptyList()).size)
    }

    @Test
    fun nextReminderSkipsDaysOutsideThePeriod() {
        val now = LocalDateTime.of(2026, 11, 10, 7, 0)
        val today = now.toLocalDate().toEpochDay()
        val med = Medication(id = 1, name = "A", times = listOf(480), startDay = today - 10)
        // 昨日で終了
        assertNull(nextReminder(now, listOf(med.copy(periodEnd = today - 1))))
        // 今日が最終日: 今日の分は鳴り、明日の分は鳴らない
        assertEquals(LocalDateTime.of(2026, 11, 10, 8, 0), nextReminder(now, listOf(med.copy(periodEnd = today))))
        assertNull(nextReminder(now.withHour(9), listOf(med.copy(periodEnd = today))))
        // 明日から開始
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 0), nextReminder(now, listOf(med.copy(periodStart = today + 1))))
    }

    @Test
    fun unsupportedDeviceLanguagesFallBackToJapanese() {
        assertNull(AppLocale.fallbackFor(Locale.JAPAN))
        assertNull(AppLocale.fallbackFor(Locale.SIMPLIFIED_CHINESE))
        assertEquals("zh-CN", AppLocale.fallbackFor(Locale.TRADITIONAL_CHINESE))
        assertEquals("zh-CN", AppLocale.fallbackFor(Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals("ja", AppLocale.fallbackFor(Locale.US))
    }

    @Test
    fun takenLogOutsideTheScheduleIsStillShown() {
        val archived = a.copy(archivedDay = day)
        val logs = listOf(IntakeLog(medicationId = 1, day = day, minuteOfDay = 480, takenAt = 111))
        val entries = entriesForDay(day, listOf(archived), logs)
        assertEquals(DaySummary(total = 1, taken = 1), summarize(entries))
    }

    @Test
    fun nextReminderPicksTheNextTimeToday() {
        val now = LocalDateTime.of(2026, 11, 10, 8, 0)
        assertEquals(LocalDateTime.of(2026, 11, 10, 19, 0), nextReminder(now, listOf(a, b)))
    }

    @Test
    fun nextReminderRollsOverToTomorrow() {
        val now = LocalDateTime.of(2026, 11, 10, 19, 0)
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 0), nextReminder(now, listOf(a, b)))
    }

    @Test
    fun nextReminderIgnoresDisabledAndArchivedMedications() {
        val now = LocalDateTime.of(2026, 11, 10, 7, 0)
        val today = now.toLocalDate().toEpochDay()
        val disabled = a.copy(reminderEnabled = false)
        val archived = b.copy(archivedDay = today)
        assertNull(nextReminder(now, listOf(disabled, archived)))
        assertNull(nextReminder(now, emptyList()))
    }
}
