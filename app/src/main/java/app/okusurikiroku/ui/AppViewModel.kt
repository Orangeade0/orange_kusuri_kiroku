package app.okusurikiroku.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import app.okusurikiroku.OkusuriApp
import app.okusurikiroku.data.MealSettings
import app.okusurikiroku.data.Medication
import app.okusurikiroku.domain.DaySummary
import app.okusurikiroku.domain.DoseEntry
import app.okusurikiroku.domain.entriesForDay
import app.okusurikiroku.domain.summarize
import app.okusurikiroku.reminder.Reminders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OkusuriApp
    private val repository = app.repository
    private val settings = app.settings

    /** 表示言語の言語タグ。null なら端末の設定に合わせる。 */
    val language: StateFlow<String?> = settings.language
    val mealSettings: StateFlow<MealSettings> = settings.mealSettings

    val today = MutableStateFlow(LocalDate.now().toEpochDay())
    val selectedDay = MutableStateFlow(today.value)
    val month = MutableStateFlow(YearMonth.now())

    /** 終了済みも含むすべてのお薬。読み込み中は null。 */
    val medications: StateFlow<List<Medication>?> =
        repository.medications.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 選択中の日の予定と記録。読み込み中は null。 */
    val dayEntries: StateFlow<List<DoseEntry>?> = selectedDay
        .flatMapLatest { day ->
            combine(repository.medications, repository.logs(day, day)) { medications, logs ->
                entriesForDay(day, medications, logs)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 表示中の月の、今日までの各日の集計。 */
    val monthSummaries: StateFlow<Map<Long, DaySummary>> = month
        .flatMapLatest { yearMonth ->
            val first = yearMonth.atDay(1).toEpochDay()
            val last = yearMonth.atEndOfMonth().toEpochDay()
            combine(repository.medications, repository.logs(first, last), today) { medications, logs, today ->
                val logsByDay = logs.groupBy { it.day }
                (first..minOf(last, today)).associateWith { day ->
                    summarize(entriesForDay(day, medications, logsByDay[day].orEmpty()))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 画面に戻ったとき。日付が変わっていたら「今日」を進める。 */
    fun onResume() {
        val now = LocalDate.now().toEpochDay()
        if (now != today.value) {
            if (selectedDay.value == today.value) selectedDay.value = now
            today.value = now
        }
        viewModelScope.launch { Reminders.scheduleNext(getApplication()) }
    }

    fun selectDay(day: Long) {
        selectedDay.value = day.coerceAtMost(today.value)
    }

    fun selectMonth(yearMonth: YearMonth) {
        month.value = yearMonth
    }

    fun setTaken(entry: DoseEntry, taken: Boolean) {
        val day = selectedDay.value
        viewModelScope.launch {
            repository.setTaken(entry.medication.id, day, entry.minuteOfDay, taken)
            if (taken) Reminders.dismissIfDone(getApplication(), day, entry.minuteOfDay)
        }
    }

    fun save(medication: Medication) {
        val mealSettings = settings.mealSettings.value
        viewModelScope.launch {
            repository.save(medication, today.value, mealSettings)
            Reminders.scheduleNext(getApplication())
            cleanupImages()
        }
    }

    fun delete(medicationId: Long) {
        viewModelScope.launch {
            repository.delete(medicationId, today.value)
            Reminders.scheduleNext(getApplication())
            cleanupImages()
        }
    }

    fun setLanguage(tag: String?) = settings.setLanguage(tag)

    /** 食事の時間や食前・食後の分数を変える。食事に合わせて飲むお薬の時間も今日から変わる。 */
    fun setMealSettings(new: MealSettings) {
        val old = settings.mealSettings.value
        if (old == new) return
        settings.setMealSettings(new)
        viewModelScope.launch {
            repository.applyMealSettings(old, new, today.value)
            Reminders.scheduleNext(getApplication())
        }
    }

    private suspend fun cleanupImages() {
        app.images.cleanup(repository.getMedications().mapNotNull { it.imagePath }.toSet())
    }

    init {
        viewModelScope.launch { cleanupImages() }
    }

    companion object {
        /** 画面を持つ Activity の Application から作る。 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])) }
        }
    }
}
