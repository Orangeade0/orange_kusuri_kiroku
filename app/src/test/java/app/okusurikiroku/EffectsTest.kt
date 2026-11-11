package app.okusurikiroku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.okusurikiroku.data.Medication
import app.okusurikiroku.domain.DoseEntry
import app.okusurikiroku.ui.MainTabs
import app.okusurikiroku.ui.OkusuriTheme
import app.okusurikiroku.ui.Tab
import app.okusurikiroku.ui.TodayScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 動きのある演出の確認。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class EffectsTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = 20_000L
    private val medications = listOf(
        Medication(id = 1, name = "アムロジピン", dosage = "1錠", times = listOf(480), startDay = 0),
        Medication(id = 2, name = "整腸剤", dosage = "1包", times = listOf(480), startDay = 0),
    )

    private fun top(text: String): Float =
        compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun upcomingDosesAreListedFirstInTimeOrder() {
        val noon = Medication(id = 3, name = "ビタミンC", times = listOf(720), startDay = 0)
        val evening = Medication(id = 4, name = "鉄剤", times = listOf(1140), startDay = 0)
        var entries by mutableStateOf(
            listOf(
                DoseEntry(medications[0], 480, 1_000L), // 8:00 飲んだ
                DoseEntry(medications[1], 480, null), // 8:00 まだ（時間を過ぎている）
                DoseEntry(noon, 720, null), // 12:00 まだ
                DoseEntry(evening, 1140, null), // 19:00 まだ
            ),
        )
        compose.setContent {
            OkusuriTheme(darkTheme = false) {
                TodayScreen(
                    day = today,
                    today = today,
                    entries = entries,
                    hasMedications = true,
                    onDayChange = {},
                    onSetTaken = { entry, taken ->
                        entries = entries.map { if (it == entry) it.copy(takenAt = if (taken) 2_000L else null) else it }
                    },
                    onAddMedication = {},
                    nowMinute = 9 * 60 + 30,
                )
            }
        }

        // これから飲む分が時間順に上、飲んだ分が下
        val order = listOf("これから飲むお薬", "整腸剤", "ビタミンC", "鉄剤", "飲んだお薬", "アムロジピン").map(::top)
        assertEquals(order.sorted(), order)
        // 過ぎた回と、次の回までの時間
        compose.onNodeWithText("予定の時間を過ぎています").assertExists()
        compose.onNodeWithText("あと 2時間30分").assertExists()

        // 記録すると、そのお薬は「飲んだお薬」へ移る
        compose.onAllNodesWithTextCompat("飲んだ").first().performClick()
        compose.waitForIdle()
        val after = listOf("これから飲むお薬", "ビタミンC", "鉄剤", "飲んだお薬", "アムロジピン", "整腸剤").map(::top)
        assertEquals(after.sorted(), after)
        compose.onNodeWithText("予定の時間を過ぎています").assertDoesNotExist()
    }

    @Test
    fun pastDaysListUnrecordedDosesFirstWithoutCountdown() {
        compose.setContent {
            OkusuriTheme(darkTheme = false) {
                TodayScreen(
                    day = today - 1,
                    today = today,
                    entries = listOf(DoseEntry(medications[0], 480, 1_000L), DoseEntry(medications[1], 480, null)),
                    hasMedications = true,
                    onDayChange = {},
                    onSetTaken = { _, _ -> },
                    onAddMedication = {},
                    nowMinute = 9 * 60 + 30,
                )
            }
        }
        compose.onNodeWithText("記録していないお薬").assertExists()
        compose.onNodeWithText("これから飲むお薬").assertDoesNotExist()
        compose.onNodeWithText("予定の時間を過ぎています").assertDoesNotExist()
    }

    @Test
    fun takingTheLastDoseCelebratesWithConfetti() {
        var entries by mutableStateOf(
            listOf(DoseEntry(medications[0], 480, 1_000L), DoseEntry(medications[1], 480, null)),
        )
        var recorded = 0
        compose.setContent {
            OkusuriTheme(darkTheme = false) {
                MainTabs(Tab.TODAY, {}) {
                    TodayScreen(
                        day = today,
                        today = today,
                        entries = entries,
                        hasMedications = true,
                        onDayChange = {},
                        onSetTaken = { entry, taken ->
                            recorded++
                            entries = entries.map { if (it == entry) it.copy(takenAt = if (taken) 2_000L else null) else it }
                        },
                        onAddMedication = {},
                    )
                }
            }
        }
        compose.onNodeWithText("あと 1 回").assertExists()

        // 最後の1回を記録した直後の、紙吹雪が舞っている途中の画面を撮る
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("飲んだ").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/confetti.png")
        assertEquals(1, recorded)

        // 演出は自動で終わり、画面は落ち着く
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("すべて飲みました").assertExists()
    }
}
