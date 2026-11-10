package app.okusurikiroku.data

/** 食事の時間と、食前・食後を何分ずらすかの設定。 */
data class MealSettings(
    /** 朝食・昼食・夕食の時間（0時からの分）。 */
    val mealTimes: List<Int> = listOf(7 * 60, 12 * 60, 19 * 60),
    /** 食前は食事の何分前か。 */
    val beforeMinutes: Int = 30,
    /** 食後は食事の何分後か。 */
    val afterMinutes: Int = 60,
) {
    /** [meal] に対して [timing] で飲む時間。 */
    fun doseTime(timing: MealTiming, meal: Int): Int {
        val mealTime = mealTimes[meal]
        return when (timing) {
            MealTiming.BEFORE -> mealTime - beforeMinutes
            MealTiming.AFTER -> mealTime + afterMinutes
            MealTiming.WITH, MealTiming.NONE -> mealTime
        }.coerceIn(0, 24 * 60 - 1)
    }

    /** 食事から計算した時間と、時刻で指定した時間を合わせた、実際の飲む時間。 */
    fun effectiveTimes(timing: MealTiming, meals: List<Int>, manualTimes: List<Int>): List<Int> {
        val mealDoses = if (timing == MealTiming.NONE) emptyList() else meals.map { doseTime(timing, it) }
        return (mealDoses + manualTimes).distinct().sorted()
    }

    companion object {
        const val BREAKFAST = 0
        const val LUNCH = 1
        const val DINNER = 2
        val MEALS = listOf(BREAKFAST, LUNCH, DINNER)

        /** 食前・食後の分数として選べる値。 */
        val OFFSET_OPTIONS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)
    }
}

/**
 * 設定やタイミングの変更で同じ食事の回の時間がずれるとき、「変更前の時間 → 変更後の時間」を返す。
 * 今日すでに記録した分を新しい時間に引き継ぐために使う。
 */
fun mealTimeRemap(
    old: Medication,
    oldSettings: MealSettings,
    new: Medication,
    newSettings: MealSettings,
): Map<Int, Int> {
    if (old.mealTiming == MealTiming.NONE || new.mealTiming == MealTiming.NONE) return emptyMap()
    return old.meals.filter { it in new.meals }.associate { meal ->
        oldSettings.doseTime(old.mealTiming, meal) to newSettings.doseTime(new.mealTiming, meal)
    }
}
