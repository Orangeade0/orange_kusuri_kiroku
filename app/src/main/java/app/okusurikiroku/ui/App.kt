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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
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

    // 一覧と編集画面を、横にすべらせながら入れ替える
    AnimatedContent(
        targetState = editingId,
        contentKey = { it != null },
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(220))) togetherWith fadeOut(tween(140))
            } else {
                fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(240)) { it / 5 } + fadeOut(tween(160)))
            }
        },
        label = "screen",
    ) { editing ->
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
            } else {
                // 編集対象が見つからない（削除済みなど）なら一覧に戻る
                val loaded = allMedications != null
                LaunchedEffect(loaded) { if (loaded) editingId = null }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        } else {
            MainTabs(tab = tab, onTabChange = { tab = it }) { shown ->
                when (shown) {
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
    }
}

/** 下部のナビゲーションが覆う高さ。スクロールする画面は、この分だけ下に余白を足す。 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * 下部に浮かぶナビゲーションと、タブごとの画面。タブを替えると、ふわりと入れ替わる。
 * 画面はナビゲーションの下までスクロールでき、下端は背景色に溶けるように消える。
 */
@Composable
fun MainTabs(tab: Tab, onTabChange: (Tab) -> Unit, content: @Composable (Tab) -> Unit) {
    val brand = LocalBrand.current
    val density = LocalDensity.current
    val background = MaterialTheme.colorScheme.background
    var barInset by remember { mutableStateOf(0.dp) }
    Box(
        Modifier
            .fillMaxSize()
            .background(background),
    ) {
        CompositionLocalProvider(LocalBottomBarInset provides barInset) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.97f)) togetherWith
                        fadeOut(tween(90))
                },
                label = "tab",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                content(shown)
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barInset + 28.dp)
                .background(Brush.verticalGradient(0f to background.copy(alpha = 0f), 0.55f to background)),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barInset = with(density) { it.height.toDp() } }
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        ) {
            val shape = MaterialTheme.shapes.extraLarge
            Surface(
                color = brand.card,
                shape = shape,
                modifier = Modifier.shadow(
                    elevation = if (brand.isDark) 0.dp else 18.dp,
                    shape = shape,
                    ambientColor = brand.cardShadow.copy(alpha = 0.14f),
                    spotColor = brand.cardShadow.copy(alpha = 0.22f),
                ),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    for (item in Tab.entries) {
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { onTabChange(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
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
