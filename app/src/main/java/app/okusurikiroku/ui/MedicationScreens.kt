package app.okusurikiroku.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import app.okusurikiroku.OkusuriApp
import app.okusurikiroku.data.MealSettings
import app.okusurikiroku.data.MealTiming
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import app.okusurikiroku.R
import app.okusurikiroku.data.Medication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    medications: List<Medication>?,
    notice: ReminderNotice?,
    onAdd: () -> Unit,
    onEdit: (Medication) -> Unit,
    onOpenSettings: (ReminderNotice) -> Unit,
) {
    val bottomInset = LocalBottomBarInset.current
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.medications_title), style = MaterialTheme.typography.headlineMedium)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            if (!medications.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = bottomInset + 12.dp),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add)) },
                )
            }
        },
    ) { padding ->
        when {
            medications == null -> Unit

            medications.isEmpty() -> Column(Modifier.padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.empty_no_medications),
                    hint = stringResource(R.string.empty_no_medications_hint),
                    actionLabel = stringResource(R.string.add_medication),
                    onAction = onAdd,
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (notice != null) {
                    item(key = "notice") { NoticeCard(notice, onOpenSettings, Modifier.animateItem()) }
                }
                items(medications, key = { it.id }) { medication ->
                    MedicationCard(medication, onClick = { onEdit(medication) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: ReminderNotice, onOpenSettings: (ReminderNotice) -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                stringResource(
                    when (notice) {
                        ReminderNotice.NOTIFICATIONS_OFF -> R.string.notice_notifications_off
                        ReminderNotice.EXACT_ALARM_OFF -> R.string.notice_exact_alarm_off
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onOpenSettings(notice) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationCard(medication: Medication, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MedicationAvatar(medication, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(medication.name, style = MaterialTheme.typography.titleMedium)
                val detail = medicationDetail(medication, showTiming = true)
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (minute in medication.times) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                formatMinutes(minute),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            if (medication.reminderEnabled) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.reminder_on),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private val PRESETS = listOf(
    R.string.preset_morning to 8 * 60,
    R.string.preset_noon to 12 * 60,
    R.string.preset_evening to 19 * 60,
    R.string.preset_bedtime to 22 * 60,
)

/** [initial] が null なら新規登録。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationEditScreen(
    initial: Medication?,
    mealSettings: MealSettings,
    onSave: (Medication) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val initialTiming = initial?.mealTiming ?: MealTiming.NONE
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var dosage by rememberSaveable { mutableStateOf(initial?.dosage.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var imagePath by rememberSaveable { mutableStateOf(initial?.imagePath) }
    var mealTiming by rememberSaveable { mutableStateOf(initialTiming) }
    // 状態の保存・復元ができるよう、リストの中身は常に ArrayList にしておく
    var meals by rememberSaveable { mutableStateOf<List<Int>>(ArrayList(initial?.meals.orEmpty())) }
    // 「時間指定」のときの時間と、食事に合わせるときの「そのほかの時間」は別々に持つ。
    // 切り替えたときに前の時間が残って、気づかないうちに回数が増えるのを防ぐ。
    var clockTimes by rememberSaveable {
        mutableStateOf<List<Int>>(ArrayList(initial?.manualTimes.orEmpty().takeIf { initialTiming == MealTiming.NONE }.orEmpty()))
    }
    var extraTimes by rememberSaveable {
        mutableStateOf<List<Int>>(ArrayList(initial?.manualTimes.orEmpty().takeIf { initialTiming != MealTiming.NONE }.orEmpty()))
    }
    var reminderEnabled by rememberSaveable { mutableStateOf(initial?.reminderEnabled ?: true) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val byMeal = mealTiming != MealTiming.NONE
    val manualTimes = if (byMeal) extraTimes else clockTimes
    fun toggleManualTime(minute: Int) {
        val updated = ArrayList(if (minute in manualTimes) manualTimes - minute else (manualTimes + minute).sorted())
        if (byMeal) extraTimes = updated else clockTimes = updated
    }

    val effectiveTimes = mealSettings.effectiveTimes(mealTiming, meals, manualTimes)
    val canSave = name.isNotBlank() && effectiveTimes.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (initial == null) R.string.edit_title_new else R.string.edit_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
                    }
                    Button(
                        enabled = canSave,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 12.dp),
                        onClick = {
                            onSave(
                                Medication(
                                    id = initial?.id ?: 0,
                                    name = name.trim(),
                                    dosage = dosage.trim(),
                                    manualTimes = manualTimes,
                                    mealTiming = mealTiming,
                                    meals = if (byMeal) meals else emptyList(),
                                    times = effectiveTimes,
                                    reminderEnabled = reminderEnabled,
                                    note = note.trim(),
                                    imagePath = imagePath,
                                    startDay = initial?.startDay ?: 0,
                                    archivedDay = initial?.archivedDay,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.field_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text(stringResource(R.string.field_dosage)) },
                        placeholder = { Text(stringResource(R.string.field_dosage_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(16.dp)) {
                    PhotoSection(name = name, imagePath = imagePath, onChange = { imagePath = it })
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel(stringResource(R.string.section_timing))
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        MealTiming.entries.forEachIndexed { index, timing ->
                            SegmentedButton(
                                selected = mealTiming == timing,
                                onClick = { mealTiming = timing },
                                shape = SegmentedButtonDefaults.itemShape(index, MealTiming.entries.size),
                                icon = {},
                            ) { Text(mealTimingLabel(timing), maxLines = 1) }
                        }
                    }

                    if (byMeal) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (meal in MealSettings.MEALS) {
                                FilterChip(
                                    selected = meal in meals,
                                    onClick = {
                                        meals = ArrayList(if (meal in meals) meals - meal else (meals + meal).sorted())
                                    },
                                    label = {
                                        val time = formatMinutes(mealSettings.doseTime(mealTiming, meal))
                                        Text("${stringResource(MEAL_LABELS[meal])} $time")
                                    },
                                )
                            }
                        }
                        Text(
                            when (mealTiming) {
                                MealTiming.BEFORE -> stringResource(R.string.meal_help_before, mealSettings.beforeMinutes)
                                MealTiming.AFTER -> stringResource(R.string.meal_help_after, mealSettings.afterMinutes)
                                else -> stringResource(R.string.meal_help_with)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(if (byMeal) R.string.section_extra_times else R.string.section_times),
                        style = if (byMeal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    // 食事に合わせるときは、朝・昼・夜の代わりに食事を選ぶので「寝る前」だけ出す
                    val presets = if (byMeal) PRESETS.takeLast(1) else PRESETS
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((labelRes, minute) in presets) {
                            FilterChip(
                                selected = minute in manualTimes,
                                onClick = { toggleManualTime(minute) },
                                label = { Text("${stringResource(labelRes)} ${formatMinutes(minute)}") },
                            )
                        }
                        for (minute in manualTimes.filter { time -> presets.none { it.second == time } }) {
                            val label = formatMinutes(minute)
                            InputChip(
                                selected = true,
                                onClick = { toggleManualTime(minute) },
                                label = { Text(label) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.remove_time, label),
                                        modifier = Modifier.size(InputChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                        AssistChip(
                            onClick = { showTimePicker = true },
                            label = { Text(stringResource(R.string.add_time)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp)) },
                        )
                    }
                    if (effectiveTimes.isEmpty()) {
                        Text(
                            stringResource(R.string.times_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = reminderEnabled, role = Role.Switch, onValueChange = { reminderEnabled = it })
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.reminder_switch),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = reminderEnabled, onCheckedChange = null)
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.field_note)) },
                        placeholder = { Text(stringResource(R.string.field_note_placeholder)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialMinuteOfDay = 8 * 60,
            confirmLabel = stringResource(R.string.add),
            onConfirm = { minute ->
                if (minute !in manualTimes) toggleManualTime(minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** 写真の表示と、撮影・選択・削除。取り込んだ写真のファイル名を [onChange] で返す。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoSection(name: String, imagePath: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val images = (context.applicationContext as OkusuriApp).images
    val scope = rememberCoroutineScope()

    fun import(uri: Uri) {
        scope.launch {
            val imported = images.import(uri)
            if (imported != null) {
                onChange(imported)
            } else {
                Toast.makeText(context, R.string.photo_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 撮影中に画面が作り直されても結果を受け取れるよう、保存先も状態として残す
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) import(uri)
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) import(uri)
    }

    Column {
        SectionLabel(stringResource(R.string.section_photo))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (imagePath != null) {
                MedicationImage(
                    fileName = imagePath,
                    contentDescription = stringResource(R.string.photo_description, name),
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_notification),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val uri = images.newCameraTarget().second
                    cameraUri = uri
                    try {
                        cameraLauncher.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.no_camera, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.take_photo)) }
                OutlinedButton(onClick = {
                    try {
                        pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.photo_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.choose_photo)) }
                if (imagePath != null) {
                    TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.remove_photo)) }
                }
            }
        }
    }
}
