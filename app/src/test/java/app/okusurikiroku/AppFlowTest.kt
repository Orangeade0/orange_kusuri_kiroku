package app.okusurikiroku

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.okusurikiroku.data.AppDatabase
import app.okusurikiroku.data.MedRepository
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 実際の Activity・ViewModel・DB を通した一連の操作。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class AppFlowTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    private val setUp = object : ExternalResource() {
        override fun before() {
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            // 本番と同じファイルの DB を使う（Robolectric がテストごとに保存先を分ける）
            db = AppDatabase.create(app)
            (app as OkusuriApp).repository = MedRepository(db)
        }

        // Activity が終了してから閉じる
        override fun after() = db.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(setUp).around(compose)

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTextCompat(text).isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("「$text」が表示されませんでした", e)
        }
    }

    @Test
    fun registerMedicationThenRecordAndUndo() {
        val repository = (app as OkusuriApp).repository
        val today = LocalDate.now().toEpochDay()

        // 未登録: 案内から登録画面へ
        waitForText("お薬を登録する")
        compose.onNodeWithText("お薬を登録する").performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("お薬の名前").performTextInput("ビタミンD")
        compose.onNodeWithText("保存").assertIsNotEnabled() // 時間が未選択
        compose.onNodeWithText("朝 8:00").performClick()
        compose.onNodeWithText("夜 19:00").performClick()

        // 時間の追加ダイアログ
        compose.onNodeWithText("時間を追加").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/time_picker.png")
        compose.onNodeWithText("キャンセル").performClick()

        compose.onNodeWithText("保存").performClick()

        // きろく画面に2回分が並ぶ
        waitForText("0 / 2 回 服用済み")
        assertEquals(listOf(480, 1140), runBlocking { repository.getMedications() }.single().times)
        // 次の服用時刻にアラームが登録されている（22:00 以降の実行でも翌朝の分が入る）
        compose.waitUntil(5_000) {
            shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.size == 1
        }

        // 飲んだ → 記録される
        compose.onAllNodesWithTextCompat("飲んだ").first().performClick()
        waitForText("1 / 2 回 服用済み")
        assertEquals(480, runBlocking { repository.getLogs(today) }.single().minuteOfDay)
        compose.onNodeWithContentDescription("服用済み").assertIsDisplayed()

        // 記録をタップ → 確認のうえ取り消し
        compose.onNodeWithContentDescription("服用済み").performClick()
        compose.onNodeWithText("記録を取り消しますか？").assertIsDisplayed()
        compose.onNodeWithText("取り消す").performClick()
        waitForText("0 / 2 回 服用済み")
        assertTrue(runBlocking { repository.getLogs(today) }.isEmpty())

        // お薬タブに一覧表示され、カレンダーにも今日の分が出る
        compose.onNodeWithText("お薬").performClick()
        waitForText("19:00")
        compose.onNodeWithText("カレンダー").performClick()
        waitForText("0/2")
        captureScreenRoboImage("build/outputs/roborazzi/flow_calendar.png")
    }

    @Test
    fun registerMedicationAfterMealsThenChangeOffsetInSettings() {
        val repository = (app as OkusuriApp).repository

        waitForText("お薬を登録する")
        compose.onNodeWithText("お薬を登録する").performClick()
        compose.onNodeWithText("お薬の名前").performTextInput("胃薬")
        compose.onNodeWithText("食後").performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled() // 食事が未選択
        compose.onNodeWithText("朝食 8:00").performClick()
        compose.onNodeWithText("夕食 20:00").performClick()
        compose.onNodeWithText("保存").performClick()

        waitForText("0 / 2 回 服用済み")
        assertEquals(listOf(480, 1200), runBlocking { repository.getMedications() }.single().times)
        compose.onAllNodesWithTextCompat("食後").first().assertIsDisplayed()

        // 設定で「食後」を 60分後 → 45分後 に変える
        compose.onNodeWithText("設定").performClick()
        compose.onNodeWithText("食事の60分後").performClick()
        compose.onNodeWithText("45分").performClick()
        waitForText("食事の45分後")
        compose.waitUntil(5_000) {
            runBlocking { repository.getMedications() }.single().times == listOf(465, 1185)
        }

        compose.onNodeWithText("きろく").performClick()
        waitForText("7:45")
        waitForText("19:45")
    }

    @Test
    fun switchingLanguageInSettingsShowsChinese() {
        waitForText("お薬を登録する")
        compose.onNodeWithText("設定").performClick()
        compose.onNodeWithText("简体中文").performClick()

        // 画面が作り直され、中国語になる
        waitForText("用餐时间")
        compose.onNodeWithText("记录").performClick()
        waitForText("添加药物")
        captureScreenRoboImage("build/outputs/roborazzi/flow_chinese.png")

        // 日本語に戻す
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("日本語").performClick()
        waitForText("食事の時間")
    }
}
