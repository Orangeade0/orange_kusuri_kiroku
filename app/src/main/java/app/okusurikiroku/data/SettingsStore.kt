package app.okusurikiroku.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** アプリの設定（表示言語、食事の時間）。 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val languageState = MutableStateFlow(prefs.getString(KEY_LANGUAGE, null))

    /** 表示言語の言語タグ（[LANGUAGE_JAPANESE] など）。null なら端末の設定に合わせる。 */
    val language: StateFlow<String?> get() = languageState

    private val mealState = MutableStateFlow(readMealSettings())
    val mealSettings: StateFlow<MealSettings> get() = mealState

    fun setLanguage(tag: String?) {
        prefs.edit { if (tag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, tag) }
        languageState.value = tag
    }

    fun setMealSettings(settings: MealSettings) {
        prefs.edit {
            putInt(KEY_BREAKFAST, settings.mealTimes[MealSettings.BREAKFAST])
            putInt(KEY_LUNCH, settings.mealTimes[MealSettings.LUNCH])
            putInt(KEY_DINNER, settings.mealTimes[MealSettings.DINNER])
            putInt(KEY_BEFORE, settings.beforeMinutes)
            putInt(KEY_AFTER, settings.afterMinutes)
        }
        mealState.value = settings
    }

    private fun readMealSettings(): MealSettings {
        val defaults = MealSettings()
        return MealSettings(
            mealTimes = listOf(
                prefs.getInt(KEY_BREAKFAST, defaults.mealTimes[MealSettings.BREAKFAST]),
                prefs.getInt(KEY_LUNCH, defaults.mealTimes[MealSettings.LUNCH]),
                prefs.getInt(KEY_DINNER, defaults.mealTimes[MealSettings.DINNER]),
            ),
            beforeMinutes = prefs.getInt(KEY_BEFORE, defaults.beforeMinutes),
            afterMinutes = prefs.getInt(KEY_AFTER, defaults.afterMinutes),
        )
    }

    companion object {
        const val LANGUAGE_JAPANESE = "ja"
        const val LANGUAGE_CHINESE = "zh-CN"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_BREAKFAST = "meal_breakfast"
        private const val KEY_LUNCH = "meal_lunch"
        private const val KEY_DINNER = "meal_dinner"
        private const val KEY_BEFORE = "before_minutes"
        private const val KEY_AFTER = "after_minutes"
    }
}
