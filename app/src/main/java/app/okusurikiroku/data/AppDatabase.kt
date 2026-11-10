package app.okusurikiroku.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MedDao {
    @Query("SELECT * FROM medications ORDER BY name, id")
    fun observeMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY name, id")
    suspend fun getMedications(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedication(id: Long): Medication?

    @Insert
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedication(id: Long)

    /** [day] に終了した、ほかの行の数。時間変更で切り替わった「前の行」があるかの判定に使う。 */
    @Query("SELECT COUNT(*) FROM medications WHERE archivedDay = :day AND id != :id")
    suspend fun countArchivedOn(day: Long, id: Long): Int

    @Query("SELECT * FROM intake_logs WHERE day BETWEEN :fromDay AND :toDay")
    fun observeLogs(fromDay: Long, toDay: Long): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs WHERE day = :day")
    suspend fun getLogs(day: Long): List<IntakeLog>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: IntakeLog)

    @Query("DELETE FROM intake_logs WHERE medicationId = :medicationId AND day = :day AND minuteOfDay = :minuteOfDay")
    suspend fun deleteLog(medicationId: Long, day: Long, minuteOfDay: Int)

    @Query("DELETE FROM intake_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}

class Converters {
    @TypeConverter
    fun minutesToString(minutes: List<Int>): String = minutes.joinToString(",")

    @TypeConverter
    fun stringToMinutes(value: String): List<Int> =
        if (value.isEmpty()) emptyList() else value.split(",").map { it.toInt() }
}

/** v2: 食事タイミング（mealTiming / meals / manualTimes）と写真（imagePath）を追加。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medications ADD COLUMN manualTimes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE medications ADD COLUMN mealTiming TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE medications ADD COLUMN meals TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE medications ADD COLUMN imagePath TEXT")
        // これまでの飲む時間は、すべて時刻で指定したもの
        db.execSQL("UPDATE medications SET manualTimes = times")
    }
}

/** v3: 服用期間（periodStart / periodEnd）を追加。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medications ADD COLUMN periodStart INTEGER")
        db.execSQL("ALTER TABLE medications ADD COLUMN periodEnd INTEGER")
    }
}

@Database(entities = [Medication::class, IntakeLog::class], version = 3)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "okusuri.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
