package app.okusurikiroku

import app.okusurikiroku.data.AppDatabase
import app.okusurikiroku.data.MealSettings
import app.okusurikiroku.data.MealTiming
import app.okusurikiroku.data.MedRepository
import app.okusurikiroku.data.Medication
import app.okusurikiroku.domain.DaySummary
import app.okusurikiroku.domain.entriesForDay
import app.okusurikiroku.domain.summarize
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MealTimingTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: MedRepository
    private val today = 20_000L
    private val defaults = MealSettings() // 朝食 7:00・昼食 12:00・夕食 19:00、食前30分・食後60分
    private val allMeals = MealSettings.MEALS

    @Before
    fun setUp() {
        val (database, repo) = installInMemoryRepository()
        db = database
        repository = repo
    }

    @After
    fun tearDown() = db.close()

    private suspend fun active() = repository.getMedications().single { !it.isArchived }

    private suspend fun summary(day: Long): DaySummary =
        summarize(entriesForDay(day, repository.getMedications(), repository.getLogs(day)))

    @Test
    fun defaultOffsetsAreThirtyMinutesBeforeAndOneHourAfter() {
        assertEquals(listOf(390, 690, 1110), defaults.effectiveTimes(MealTiming.BEFORE, allMeals, emptyList()))
        assertEquals(listOf(420, 720, 1140), defaults.effectiveTimes(MealTiming.WITH, allMeals, emptyList()))
        assertEquals(listOf(480, 780, 1200), defaults.effectiveTimes(MealTiming.AFTER, allMeals, emptyList()))
    }

    @Test
    fun mealDosesAreCombinedWithManualTimes() {
        val times = defaults.effectiveTimes(MealTiming.AFTER, listOf(MealSettings.BREAKFAST), listOf(1320, 480))
        assertEquals(listOf(480, 1320), times) // 朝食後 8:00 と手入力の 8:00 は1回にまとめる
        assertEquals(listOf(600), defaults.effectiveTimes(MealTiming.NONE, allMeals, listOf(600)))
    }

    @Test
    fun timesStayWithinTheDay() {
        val settings = MealSettings(mealTimes = listOf(10, 720, 1430), beforeMinutes = 30, afterMinutes = 60)
        assertEquals(0, settings.doseTime(MealTiming.BEFORE, MealSettings.BREAKFAST))
        assertEquals(1439, settings.doseTime(MealTiming.AFTER, MealSettings.DINNER))
    }

    @Test
    fun savingComputesTimesFromMeals() = runBlocking {
        repository.save(
            Medication(
                name = "A",
                mealTiming = MealTiming.AFTER,
                meals = listOf(MealSettings.DINNER, MealSettings.BREAKFAST),
                manualTimes = listOf(1320),
                startDay = 0,
            ),
            today,
            defaults,
        )
        val saved = active()
        assertEquals(listOf(480, 1200, 1320), saved.times)
        assertTrue(saved.isMealDose(480))
        assertFalse(saved.isMealDose(1320)) // 寝る前の分は食後ではない
    }

    @Test
    fun changingOffsetMovesTimesFromTodayAndKeepsHistory() = runBlocking {
        val id = repository.save(
            Medication(name = "A", mealTiming = MealTiming.AFTER, meals = allMeals, startDay = 0),
            today - 3,
            defaults,
        )
        repository.save(Medication(name = "B", manualTimes = listOf(600), startDay = 0), today - 3, defaults)
        repository.setTaken(id, today - 1, 480, taken = true)
        repository.setTaken(id, today, 480, taken = true) // 今日の朝食後は記録済み

        // 食後を 60分後 → 30分後 に変更
        val changed = defaults.copy(afterMinutes = 30)
        repository.applyMealSettings(defaults, changed, today)

        val a = repository.getMedications().single { it.name == "A" && !it.isArchived }
        assertEquals(listOf(450, 750, 1170), a.times)
        // 時間指定のお薬は変わらない
        assertEquals(listOf(600), repository.getMedications().single { it.name == "B" }.times)
        // 昨日は元の時間のまま（A 3回中1回 + B 1回中0回）
        val yesterday = entriesForDay(today - 1, repository.getMedications(), repository.getLogs(today - 1))
        assertEquals(listOf(480, 600, 780, 1200), yesterday.map { it.minuteOfDay })
        assertEquals(DaySummary(4, 1), summarize(yesterday))
        // 今日の朝食後の記録は、新しい時間 7:30 の回に引き継がれる
        val todayEntries = entriesForDay(today, repository.getMedications(), repository.getLogs(today))
        assertEquals(listOf(450 to true, 600 to false, 750 to false, 1170 to false), todayEntries.map { it.minuteOfDay to it.taken })
    }

    @Test
    fun changingMealTimeTwiceOnTheSameDayDoesNotAddRows() = runBlocking {
        repository.save(
            Medication(name = "A", mealTiming = MealTiming.BEFORE, meals = listOf(MealSettings.LUNCH), startDay = 0),
            today - 3,
            defaults,
        )
        val second = defaults.copy(mealTimes = listOf(420, 750, 1140))
        val third = second.copy(beforeMinutes = 15)
        repository.applyMealSettings(defaults, second, today)
        repository.applyMealSettings(second, third, today)

        assertEquals(listOf(735), active().times)
        assertEquals(2, repository.getMedications().size) // 昨日までの行 + 今日からの行
        assertEquals(DaySummary(1, 0), summary(today))
    }

    @Test
    fun switchingBeforeToAfterCarriesOverTodaysRecord() = runBlocking {
        val id = repository.save(
            Medication(name = "A", mealTiming = MealTiming.BEFORE, meals = listOf(MealSettings.BREAKFAST), startDay = 0),
            today - 3,
            defaults,
        )
        repository.setTaken(id, today, 390, taken = true)
        repository.save(active().copy(mealTiming = MealTiming.AFTER), today, defaults)

        assertEquals(listOf(480), active().times)
        assertEquals(DaySummary(1, 1), summary(today))
    }
}
