package app.okusurikiroku.domain

import app.okusurikiroku.data.IntakeLog
import app.okusurikiroku.data.Medication
import java.time.LocalDate
import java.time.LocalDateTime

/** ある日の「このお薬をこの時間に飲む」1回分。 */
data class DoseEntry(
    val medication: Medication,
    val minuteOfDay: Int,
    /** 記録した時刻（エポックミリ秒）。まだ飲んでいなければ null。 */
    val takenAt: Long?,
) {
    val taken: Boolean get() = takenAt != null
}

data class DaySummary(val total: Int, val taken: Int)

/**
 * [day] の予定と記録を時間順に並べる。
 *
 * 予定から外れた枠（時間を変更した・お薬を削除した）でも、飲んだ記録が残っていれば表示する。
 */
fun entriesForDay(day: Long, medications: List<Medication>, logs: List<IntakeLog>): List<DoseEntry> {
    val dayLogs = logs.filter { it.day == day }.associateBy { it.medicationId to it.minuteOfDay }
    val entries = mutableListOf<DoseEntry>()
    val scheduled = HashSet<Pair<Long, Int>>()
    for (medication in medications) {
        if (!medication.isActiveOn(day)) continue
        for (minute in medication.times) {
            val key = medication.id to minute
            if (scheduled.add(key)) entries += DoseEntry(medication, minute, dayLogs[key]?.takenAt)
        }
    }
    val byId = medications.associateBy { it.id }
    for ((key, log) in dayLogs) {
        if (key in scheduled) continue
        val medication = byId[key.first] ?: continue
        entries += DoseEntry(medication, key.second, log.takenAt)
    }
    return entries.sortedWith(compareBy({ it.minuteOfDay }, { it.medication.name }, { it.medication.id }))
}

fun summarize(entries: List<DoseEntry>): DaySummary = DaySummary(entries.size, entries.count { it.taken })

/** [now] より後で、通知をオンにしているお薬の次の服用時刻。なければ null。 */
fun nextReminder(now: LocalDateTime, medications: List<Medication>): LocalDateTime? {
    val today = now.toLocalDate().toEpochDay()
    val nowMinute = now.hour * 60 + now.minute
    for (offset in 0L..1L) {
        val day = today + offset
        val minute = medications
            .filter { it.reminderEnabled && it.isActiveOn(day) }
            .flatMap { it.times }
            .filter { offset > 0 || it > nowMinute }
            .minOrNull()
        if (minute != null) return LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60)
    }
    return null
}
