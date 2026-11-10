package app.okusurikiroku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.okusurikiroku.R
import app.okusurikiroku.data.MealSettings
import app.okusurikiroku.data.SettingsStore

val MEAL_LABELS = listOf(R.string.meal_breakfast, R.string.meal_lunch, R.string.meal_dinner)

// 言語名は、その言語を読める人のためのものなので翻訳しない
private val LANGUAGES = listOf(
    SettingsStore.LANGUAGE_JAPANESE to "日本語",
    SettingsStore.LANGUAGE_CHINESE to "简体中文",
)

/** 編集中の項目。-1 は何も開いていない。 */
private const val NOTHING = -1
private const val EDIT_BEFORE = 10
private const val EDIT_AFTER = 11

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    language: String?,
    mealSettings: MealSettings,
    onLanguageChange: (String?) -> Unit,
    onMealSettingsChange: (MealSettings) -> Unit,
) {
    // 0〜2: その食事の時間、EDIT_BEFORE / EDIT_AFTER: 食前・食後の分数
    var editing by rememberSaveable { mutableStateOf(NOTHING) }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_language))
            AppCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .selectableGroup()
                        .padding(vertical = 6.dp),
                ) {
                    LanguageRow(stringResource(R.string.language_system), language == null) { onLanguageChange(null) }
                    for ((tag, name) in LANGUAGES) {
                        LanguageRow(name, language == tag) { onLanguageChange(tag) }
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_meal_times))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    for (meal in MealSettings.MEALS) {
                        ValueRow(
                            label = stringResource(MEAL_LABELS[meal]),
                            value = formatMinutes(mealSettings.mealTimes[meal]),
                            onClick = { editing = meal },
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_offsets))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    ValueRow(
                        label = stringResource(R.string.timing_before),
                        value = stringResource(R.string.offset_before_value, mealSettings.beforeMinutes),
                        onClick = { editing = EDIT_BEFORE },
                    )
                    ValueRow(
                        label = stringResource(R.string.timing_after),
                        value = stringResource(R.string.offset_after_value, mealSettings.afterMinutes),
                        onClick = { editing = EDIT_AFTER },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_meal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(16.dp + LocalBottomBarInset.current))
        }
    }

    when (editing) {
        NOTHING -> Unit

        EDIT_BEFORE, EDIT_AFTER -> {
            val before = editing == EDIT_BEFORE
            MinutesDialog(
                title = stringResource(if (before) R.string.timing_before else R.string.timing_after),
                initial = if (before) mealSettings.beforeMinutes else mealSettings.afterMinutes,
                onConfirm = { minutes ->
                    onMealSettingsChange(
                        if (before) mealSettings.copy(beforeMinutes = minutes) else mealSettings.copy(afterMinutes = minutes),
                    )
                    editing = NOTHING
                },
                onDismiss = { editing = NOTHING },
            )
        }

        else -> {
            val meal = editing
            TimePickerDialog(
                initialMinuteOfDay = mealSettings.mealTimes[meal],
                confirmLabel = stringResource(R.string.ok),
                onConfirm = { minute ->
                    onMealSettingsChange(
                        mealSettings.copy(mealTimes = mealSettings.mealTimes.toMutableList().also { it[meal] = minute }),
                    )
                    editing = NOTHING
                },
                onDismiss = { editing = NOTHING },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** 食事の何分前・何分後かを一覧から選ぶ。 */
@Composable
private fun MinutesDialog(title: String, initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    // 一覧にない値が保存されていた場合も、選択肢として出す
    val options = (MealSettings.OFFSET_OPTIONS + initial).distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                for (minutes in options) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = minutes == initial,
                                role = Role.RadioButton,
                                onClick = { onConfirm(minutes) },
                            )
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = minutes == initial, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.minutes_value, minutes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
