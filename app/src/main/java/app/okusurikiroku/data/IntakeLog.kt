package app.okusurikiroku.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 「飲んだ」の記録。行があれば服用済み。 */
@Entity(
    tableName = "intake_logs",
    indices = [
        Index(value = ["medicationId", "day", "minuteOfDay"], unique = true),
        Index("day"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class IntakeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val day: Long,
    /** 予定の時間（0時からの分）。 */
    val minuteOfDay: Int,
    /** 記録した時刻（エポックミリ秒）。 */
    val takenAt: Long,
)
