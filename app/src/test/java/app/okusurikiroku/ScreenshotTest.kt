package app.okusurikiroku

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import app.okusurikiroku.data.MealSettings
import app.okusurikiroku.data.MealTiming
import app.okusurikiroku.data.SettingsStore
import app.okusurikiroku.ui.SettingsScreen
import org.junit.Before
import app.okusurikiroku.data.Medication
import app.okusurikiroku.domain.DaySummary
import app.okusurikiroku.domain.DoseEntry
import app.okusurikiroku.ui.HistoryScreen
import app.okusurikiroku.ui.MainTabs
import app.okusurikiroku.ui.MedicationEditScreen
import app.okusurikiroku.ui.MedicationListScreen
import app.okusurikiroku.ui.OkusuriTheme
import app.okusurikiroku.ui.ReminderNotice
import app.okusurikiroku.ui.Tab
import app.okusurikiroku.ui.TodayScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** 各画面を build/outputs/roborazzi に画像として書き出す（見た目の確認用）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {
    private val today = LocalDate.of(2026, 11, 10).toEpochDay()
    private val medications = listOf(
        Medication(id = 1, name = "アムロジピン", dosage = "1錠", times = listOf(480), startDay = 0),
        Medication(
            id = 2,
            name = "ビタミンD",
            dosage = "2粒",
            mealTiming = MealTiming.AFTER,
            meals = listOf(MealSettings.BREAKFAST, MealSettings.DINNER),
            manualTimes = listOf(1320),
            times = listOf(480, 1200, 1320),
            imagePath = "sample.jpg",
            periodStart = LocalDate.of(2026, 11, 10).toEpochDay(),
            periodEnd = LocalDate.of(2026, 11, 23).toEpochDay(),
            startDay = 0,
        ),
        Medication(id = 3, name = "整腸剤", dosage = "1包", times = listOf(480, 720, 1140), reminderEnabled = false, startDay = 0),
    )

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, 10, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val entries = listOf(
        DoseEntry(medications[0], 480, at(8, 5)),
        DoseEntry(medications[1], 480, at(8, 5)),
        DoseEntry(medications[2], 480, null),
        DoseEntry(medications[2], 720, null),
        DoseEntry(medications[2], 1140, null),
        DoseEntry(medications[1], 1200, null),
        DoseEntry(medications[1], 1320, null),
    )

    @Before
    fun createSamplePhoto() {
        val app = ApplicationProvider.getApplicationContext<OkusuriApp>()
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(250, 240, 220))
            drawCircle(150f, 200f, 90f, Paint().apply { color = Color.rgb(230, 120, 60) })
            drawCircle(280f, 200f, 60f, Paint().apply { color = Color.WHITE })
        }
        app.images.file("sample.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            OkusuriTheme(darkTheme = dark, dynamicColor = false, content = content)
        }
    }

    @Composable
    private fun Today(entries: List<DoseEntry>, hasMedications: Boolean = true, day: Long = today) {
        MainTabs(Tab.TODAY, {}) {
            TodayScreen(day, today, entries, hasMedications, {}, { _, _ -> }, {}, nowMinute = 9 * 60 + 30)
        }
    }

    @Test
    fun today() = capture("today") { Today(entries) }

    @Test
    fun todayDark() = capture("today_dark", dark = true) { Today(entries) }

    @Test
    fun todayAllDone() = capture("today_all_done") { Today(entries.take(2)) }

    @Test
    fun todayEmpty() = capture("today_empty") { Today(emptyList(), hasMedications = false) }

    @Test
    fun pastDay() = capture("past_day") { Today(entries.take(3), day = today - 3) }

    @Test
    fun history() = capture("history") {
        val first = YearMonth.of(2026, 11).atDay(1).toEpochDay()
        val summaries = (first..today).associateWith { day ->
            when ((day - first).toInt()) {
                2 -> DaySummary(6, 4)
                5 -> DaySummary(6, 0)
                9 -> DaySummary(6, 2)
                else -> DaySummary(6, 6)
            }
        }
        MainTabs(Tab.HISTORY, {}) {
            HistoryScreen(YearMonth.of(2026, 11), today, summaries, {}, {})
        }
    }

    @Test
    fun medicationList() = capture("medication_list") {
        MainTabs(Tab.MEDICATIONS, {}) {
            MedicationListScreen(
                medications + listOf(
                    Medication(
                        id = 4,
                        name = "抗生物質",
                        dosage = "1錠",
                        times = listOf(480, 1200),
                        periodStart = today - 9,
                        periodEnd = today - 3,
                        startDay = 0,
                    ),
                    Medication(
                        id = 5,
                        name = "花粉症の薬",
                        times = listOf(480),
                        periodStart = today + 5,
                        startDay = 0,
                    ),
                ),
                today,
                ReminderNotice.EXACT_ALARM_OFF,
                {},
                {},
                {},
            )
        }
    }

    @Test
    fun medicationEdit() = capture("medication_edit") {
        MedicationEditScreen(medications[1], today, MealSettings(), {}, {}, {})
    }

    @Test
    fun medicationEditByClock() = capture("medication_edit_clock") {
        MedicationEditScreen(medications[2].copy(manualTimes = listOf(480, 630, 1140)), today, MealSettings(), {}, {}, {})
    }

    @Test
    fun medicationNew() = capture("medication_new") {
        MedicationEditScreen(null, today, MealSettings(), {}, {}, {})
    }

    @Test
    fun settings() = capture("settings") {
        MainTabs(Tab.SETTINGS, {}) {
            SettingsScreen(SettingsStore.LANGUAGE_JAPANESE, MealSettings(), {}, {})
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-420dpi")
    fun todayChinese() = capture("zh_today") { Today(entries) }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-420dpi")
    fun historyChinese() = capture("zh_history") {
        MainTabs(Tab.HISTORY, {}) {
            HistoryScreen(YearMonth.of(2026, 11), today, mapOf(today to DaySummary(6, 2)), {}, {})
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-420dpi")
    fun medicationEditChinese() = capture("zh_medication_edit") {
        MedicationEditScreen(medications[1], today, MealSettings(), {}, {}, {})
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-420dpi")
    fun settingsChinese() = capture("zh_settings") {
        MainTabs(Tab.SETTINGS, {}) {
            SettingsScreen(SettingsStore.LANGUAGE_CHINESE, MealSettings(), {}, {})
        }
    }
}
