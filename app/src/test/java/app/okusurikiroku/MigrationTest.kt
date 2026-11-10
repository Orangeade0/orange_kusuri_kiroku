package app.okusurikiroku

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.okusurikiroku.data.AppDatabase
import app.okusurikiroku.data.MealTiming
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 最初の版（v1）のデータベースから、データを保ったまま更新できること。 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @Test
    fun migratesVersion1DataToCurrentSchema() = runBlocking {
        val app: Application = ApplicationProvider.getApplicationContext()
        val file = app.getDatabasePath("okusuri.db").apply { parentFile!!.mkdirs() }
        // schemas/…/1.json と同じ、v1 のテーブル定義
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `times` TEXT NOT NULL, " +
                    "`reminderEnabled` INTEGER NOT NULL, `note` TEXT NOT NULL, `startDay` INTEGER NOT NULL, " +
                    "`archivedDay` INTEGER)",
            )
            v1.execSQL(
                "CREATE TABLE IF NOT EXISTS `intake_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`medicationId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `minuteOfDay` INTEGER NOT NULL, " +
                    "`takenAt` INTEGER NOT NULL, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            v1.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_intake_logs_medicationId_day_minuteOfDay` " +
                    "ON `intake_logs` (`medicationId`, `day`, `minuteOfDay`)",
            )
            v1.execSQL("CREATE INDEX IF NOT EXISTS `index_intake_logs_day` ON `intake_logs` (`day`)")
            v1.execSQL("INSERT INTO medications VALUES (1, 'ビタミンD', '2粒', '480,1140', 1, '食後', 100, NULL)")
            v1.execSQL("INSERT INTO intake_logs VALUES (1, 1, 200, 480, 123456)")
            v1.version = 1
        }

        val db = AppDatabase.create(app)
        try {
            val medication = db.dao().getMedications().single()
            assertEquals("ビタミンD", medication.name)
            assertEquals(listOf(480, 1140), medication.times)
            assertEquals(listOf(480, 1140), medication.manualTimes)
            assertEquals(MealTiming.NONE, medication.mealTiming)
            assertEquals(emptyList<Int>(), medication.meals)
            assertNull(medication.imagePath)
            assertEquals(123456L, db.dao().getLogs(200).single().takenAt)
        } finally {
            db.close()
        }
    }
}
