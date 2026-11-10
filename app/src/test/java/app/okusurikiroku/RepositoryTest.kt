package app.okusurikiroku

import app.okusurikiroku.data.AppDatabase
import app.okusurikiroku.data.MedRepository
import app.okusurikiroku.data.Medication
import app.okusurikiroku.domain.DaySummary
import app.okusurikiroku.domain.entriesForDay
import app.okusurikiroku.domain.summarize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: MedRepository
    private val today = 20_000L

    @Before
    fun setUp() {
        val (database, repo) = installInMemoryRepository()
        db = database
        repository = repo
    }

    @After
    fun tearDown() = db.close()

    private suspend fun summary(day: Long): DaySummary =
        summarize(entriesForDay(day, repository.getMedications(), repository.logs(day, day).first()))

    @Test
    fun takenCanBeRecordedAndUndone() = runBlocking {
        val id = repository.save(Medication(name = "A", manualTimes = listOf(1140, 480, 480), startDay = 0), today)
        assertEquals(listOf(480, 1140), repository.getMedications().single().times)

        repository.setTaken(id, today, 480, taken = true, now = 1_000)
        repository.setTaken(id, today, 480, taken = true, now = 2_000) // 二重に記録しても1件のまま
        assertEquals(1_000L, repository.getLogs(today).single().takenAt)
        assertEquals(DaySummary(2, 1), summary(today))

        repository.setTaken(id, today, 480, taken = false)
        assertEquals(DaySummary(2, 0), summary(today))
    }

    @Test
    fun renamingKeepsTheSameRow() = runBlocking {
        val id = repository.save(Medication(name = "A", manualTimes = listOf(480), startDay = 0), today - 5)
        val saved = repository.getMedications().single()
        assertEquals(id, repository.save(saved.copy(name = "B", reminderEnabled = false), today))
        val updated = repository.getMedications().single()
        assertEquals("B", updated.name)
        assertEquals(today - 5, updated.startDay)
    }

    @Test
    fun changingTimesKeepsPastDaysIntact() = runBlocking {
        val oldId = repository.save(Medication(name = "A", manualTimes = listOf(480, 1140), startDay = 0), today - 5)
        repository.setTaken(oldId, today - 1, 480, taken = true)
        repository.setTaken(oldId, today - 1, 1140, taken = true)
        repository.setTaken(oldId, today, 480, taken = true)

        val saved = repository.getMedications().single()
        val newId = repository.save(saved.copy(manualTimes = listOf(480, 720, 1320)), today)
        assertNotEquals(oldId, newId)

        // 昨日は「2回中2回」のまま
        assertEquals(DaySummary(2, 2), summary(today - 1))
        // 今日は新しい予定3回で、すでに飲んだ8:00の記録は引き継がれる
        assertEquals(DaySummary(3, 1), summary(today))
        assertEquals(1, repository.getMedications().count { !it.isArchived })
    }

    @Test
    fun deletingKeepsHistoryButRemovesFromToday() = runBlocking {
        val id = repository.save(Medication(name = "A", manualTimes = listOf(480), startDay = 0), today - 5)
        repository.setTaken(id, today - 1, 480, taken = true)
        repository.delete(id, today)

        assertTrue(repository.getMedications().single().isArchived)
        assertEquals(DaySummary(1, 1), summary(today - 1))
        assertEquals(DaySummary(0, 0), summary(today))
    }

    @Test
    fun deletingAMedicationAddedTodayRemovesItCompletely() = runBlocking {
        val id = repository.save(Medication(name = "A", manualTimes = listOf(480), startDay = 0), today)
        repository.setTaken(id, today, 480, taken = true)
        repository.delete(id, today)

        assertTrue(repository.getMedications().isEmpty())
        assertTrue(repository.getLogs(today).isEmpty())
    }
}
