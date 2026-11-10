package app.okusurikiroku.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 食事との関係。[NONE] は時刻だけで指定。 */
enum class MealTiming { NONE, BEFORE, WITH, AFTER }

/**
 * 登録されたお薬。
 *
 * 日付はすべて [java.time.LocalDate.toEpochDay] の値で持つ。
 * 飲む時間を変更した場合は過去の記録を保つため、古い行を [archivedDay] で終了させて
 * 新しい行を作る（[MedRepository.save] を参照）。
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    /** 時刻で直接指定した飲む時間（0時からの分）。 */
    @ColumnInfo(defaultValue = "") val manualTimes: List<Int> = emptyList(),
    @ColumnInfo(defaultValue = "NONE") val mealTiming: MealTiming = MealTiming.NONE,
    /** [mealTiming] の対象の食事。[MealSettings] の BREAKFAST / LUNCH / DINNER。 */
    @ColumnInfo(defaultValue = "") val meals: List<Int> = emptyList(),
    /**
     * 実際の飲む時間。食事から計算した時間と [manualTimes] を合わせたもので、昇順・重複なし。
     * 保存時に [MedRepository.save] が計算し直す。
     */
    val times: List<Int> = manualTimes,
    val reminderEnabled: Boolean = true,
    val note: String = "",
    /** 写真のファイル名（[ImageStore] が管理）。なければ null。 */
    val imagePath: String? = null,
    /** 服用期間の開始日。null なら登録した日から。 */
    val periodStart: Long? = null,
    /** 服用期間の終了日（この日まで飲む）。null なら終了日なし。 */
    val periodEnd: Long? = null,
    /** この行の予定が有効になる日。登録や時間変更のときにアプリが決める。 */
    val startDay: Long,
    /** この日から予定に入らない（削除や時間変更で終了した行）。null なら継続中。 */
    val archivedDay: Long? = null,
) {
    val isArchived: Boolean get() = archivedDay != null

    /** [day] に飲む予定があるか。行の有効期間と、利用者が選んだ服用期間の両方に入っている日。 */
    fun isActiveOn(day: Long): Boolean =
        day >= startDay && (archivedDay == null || day < archivedDay) && isInPeriod(day)

    fun isInPeriod(day: Long): Boolean =
        (periodStart == null || day >= periodStart) && (periodEnd == null || day <= periodEnd)

    /** [minuteOfDay] の回が、食事から計算した時間かどうか。 */
    fun isMealDose(minuteOfDay: Int): Boolean = mealTiming != MealTiming.NONE && minuteOfDay !in manualTimes
}
