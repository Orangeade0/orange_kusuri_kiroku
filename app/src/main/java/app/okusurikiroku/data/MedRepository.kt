package app.okusurikiroku.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MedRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    /** お薬の登録・変更は順番に1つずつ行う。 */
    private val writeLock = Mutex()

    /** 終了済みも含むすべてのお薬。 */
    val medications: Flow<List<Medication>> = dao.observeMedications()

    fun logs(fromDay: Long, toDay: Long): Flow<List<IntakeLog>> = dao.observeLogs(fromDay, toDay)

    suspend fun getMedications(): List<Medication> = dao.getMedications()

    suspend fun getLogs(day: Long): List<IntakeLog> = dao.getLogs(day)

    suspend fun setTaken(
        medicationId: Long,
        day: Long,
        minuteOfDay: Int,
        taken: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        if (taken) {
            dao.insertLog(IntakeLog(medicationId = medicationId, day = day, minuteOfDay = minuteOfDay, takenAt = now))
        } else {
            dao.deleteLog(medicationId, day, minuteOfDay)
        }
    }

    /**
     * お薬を新規登録または更新して、その id を返す。
     *
     * 実際の飲む時間は、食事のタイミングと [settings] からここで計算する。
     * 飲む時間が変わるときは、昨日までの記録（何回中何回飲んだか）が変わってしまわないよう、
     * 古い行を今日で終了させ、今日から始まる新しい行に切り替える。
     */
    suspend fun save(medication: Medication, today: Long, settings: MealSettings = MealSettings()): Long =
        writeLock.withLock { db.withTransaction { saveLocked(medication, today, settings, settings) } }

    /** 食事の設定が変わったとき、食事に合わせて飲むお薬の時間を今日から更新する。 */
    suspend fun applyMealSettings(old: MealSettings, new: MealSettings, today: Long) = writeLock.withLock {
        db.withTransaction {
            dao.getMedications()
                .filter { !it.isArchived && it.mealTiming != MealTiming.NONE }
                .forEach { saveLocked(it, today, old, new) }
        }
    }

    private suspend fun saveLocked(
        medication: Medication,
        today: Long,
        oldSettings: MealSettings,
        settings: MealSettings,
    ): Long {
        val meals = if (medication.mealTiming == MealTiming.NONE) emptyList() else medication.meals.distinct().sorted()
        val manualTimes = medication.manualTimes.distinct().sorted()
        val times = settings.effectiveTimes(medication.mealTiming, meals, manualTimes)
        val normalized = medication.copy(meals = meals, manualTimes = manualTimes, times = times)
        val old = if (medication.id == 0L) null else dao.getMedication(medication.id)
        return when {
            old == null -> dao.insert(normalized.copy(id = 0, startDay = today, archivedDay = null))

            old.times == times -> {
                dao.update(normalized.copy(startDay = old.startDay, archivedDay = old.archivedDay))
                old.id
            }

            // 今日登録したばかりなら、守るべき過去の記録はない
            old.startDay >= today -> {
                dao.update(normalized.copy(startDay = old.startDay, archivedDay = old.archivedDay))
                carryOverTodayLogs(old, old.id, today, times, mealTimeRemap(old, oldSettings, normalized, settings))
                old.id
            }

            else -> {
                val newId = dao.insert(normalized.copy(id = 0, startDay = today, archivedDay = null))
                carryOverTodayLogs(old, newId, today, times, mealTimeRemap(old, oldSettings, normalized, settings))
                dao.update(old.copy(archivedDay = today))
                newId
            }
        }
    }

    /**
     * 今日すでに記録した分を、新しい予定に引き継ぐ。
     * 食事の回は時間がずれても同じ回として扱い（[remap]）、なくなった時間の記録は元のまま残す。
     */
    private suspend fun carryOverTodayLogs(
        old: Medication,
        newId: Long,
        today: Long,
        newTimes: List<Int>,
        remap: Map<Int, Int>,
    ) {
        for (log in dao.getLogs(today).filter { it.medicationId == old.id }) {
            val minute = remap[log.minuteOfDay] ?: log.minuteOfDay
            if (minute !in newTimes) continue
            if (newId == old.id && minute == log.minuteOfDay) continue
            dao.deleteLogById(log.id)
            dao.insertLog(log.copy(id = 0, medicationId = newId, minuteOfDay = minute))
        }
    }

    /** 今日以降の予定から外す。昨日までの記録は残す。 */
    suspend fun delete(medicationId: Long, today: Long) = writeLock.withLock {
        db.withTransaction {
            val old = dao.getMedication(medicationId) ?: return@withTransaction
            if (old.startDay >= today) {
                dao.deleteMedication(medicationId)
            } else if (old.archivedDay == null) {
                dao.update(old.copy(archivedDay = today))
            }
        }
    }
}
