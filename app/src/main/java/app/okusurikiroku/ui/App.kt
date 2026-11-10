package app.okusurikiroku.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.okusurikiroku.R
import app.okusurikiroku.reminder.Reminders

enum class Tab(val labelRes: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Filled.CheckCircle),
    HISTORY(R.string.tab_history, Icons.Filled.DateRange),
    MEDICATIONS(R.string.tab_medications, Icons.AutoMirrored.Filled.List),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

/** 通知が届かない・遅れる状態のお知らせ。 */
enum class ReminderNotice { NOTIFICATIONS_OFF, EXACT_ALARM_OFF }

private const val NEW_MEDICATION = 0L

@Composable
fun OkusuriAppUi(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // null: 一覧, NEW_MEDICATION: 新規登録, それ以外: その id を編集
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var notice by rememberSaveable { mutableStateOf<ReminderNotice?>(null) }

    val today by viewModel.today.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val allMedications by viewModel.medications.collectAsStateWithLifecycle()
    val dayEntries by viewModel.dayEntries.collectAsStateWithLifecycle()
    val monthSummaries by viewModel.monthSummaries.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val mealSettings by viewModel.mealSettings.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val activeMedications = allMedications?.filter { !it.isArchived }

    val hasReminders = activeMedications?.any { it.reminderEnabled } == true
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        notice = reminderNotice(context)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notice = reminderNotice(context)
    }

    val editing = editingId
    if (editing != null) {
        BackHandler { editingId = null }
        val medication = allMedications?.find { it.id == editing }
        // プロセス再生成の直後など、編集対象の読み込みが終わるまでは何も出さない
        if (editing == NEW_MEDICATION || medication != null) {
            MedicationEditScreen(
                initial = medication,
                mealSettings = mealSettings,
                onSave = { saved ->
                    viewModel.save(saved)
                    if (saved.reminderEnabled && needsNotificationPermission(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    editingId = null
                },
                onDelete = {
                    viewModel.delete(editing)
                    editingId = null
                },
                onBack = { editingId = null },
            )
        } else if (allMedications != null) {
            editingId = null
        }
        return
    }

    MainTabs(tab = tab, onTabChange = { tab = it }) {
        when (tab) {
            Tab.TODAY -> TodayScreen(
                day = selectedDay,
                today = today,
                entries = dayEntries,
                hasMedications = activeMedications?.isNotEmpty() == true,
                onDayChange = viewModel::selectDay,
                onSetTaken = viewModel::setTaken,
                onAddMedication = { editingId = NEW_MEDICATION },
            )

            Tab.HISTORY -> HistoryScreen(
                month = month,
                today = today,
                summaries = monthSummaries,
                onMonthChange = viewModel::selectMonth,
                onDayClick = { day ->
                    viewModel.selectDay(day)
                    tab = Tab.TODAY
                },
            )

            Tab.MEDICATIONS -> MedicationListScreen(
                medications = activeMedications,
                notice = notice.takeIf { hasReminders },
                onAdd = { editingId = NEW_MEDICATION },
                onEdit = { editingId = it.id },
                onOpenSettings = { openReminderSettings(context, it) },
            )

            Tab.SETTINGS -> SettingsScreen(
                language = language,
                mealSettings = mealSettings,
                onLanguageChange = { tag ->
                    if (tag != language) {
                        viewModel.setLanguage(tag)
                        // 画面を作り直して、選んだ言語の文言に切り替える
                        activity?.recreate()
                    }
                },
                onMealSettingsChange = viewModel::setMealSettings,
            )
        }
    }
}

@Composable
fun MainTabs(tab: Tab, onTabChange: (Tab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                for (item in Tab.entries) {
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { onTabChange(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            content()
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun reminderNotice(context: Context): ReminderNotice? = when {
    !Reminders.notificationsEnabled(context) -> ReminderNotice.NOTIFICATIONS_OFF
    !Reminders.canScheduleExact(context) -> ReminderNotice.EXACT_ALARM_OFF
    else -> null
}

private fun openReminderSettings(context: Context, notice: ReminderNotice) {
    val intent = if (notice == ReminderNotice.EXACT_ALARM_OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // 端末に該当の設定画面がない
    }
}
