package app.okusurikiroku.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.okusurikiroku.R
import app.okusurikiroku.data.Medication
import app.okusurikiroku.domain.DaySummary
import app.okusurikiroku.domain.DoseEntry
import app.okusurikiroku.domain.summarize

@Composable
fun TodayScreen(
    day: Long,
    today: Long,
    entries: List<DoseEntry>?,
    hasMedications: Boolean,
    onDayChange: (Long) -> Unit,
    onSetTaken: (DoseEntry, Boolean) -> Unit,
    onAddMedication: () -> Unit,
    /** 現在時刻（0時からの分）。今日を表示しているときの「あと◯分」に使う。わからなければ null。 */
    nowMinute: Int? = null,
) {
    var undoTarget by remember { mutableStateOf<DoseEntry?>(null) }
    var enlarged by remember { mutableStateOf<Medication?>(null) }
    val haptics = LocalHapticFeedback.current
    val motionEnabled = rememberMotionEnabled()

    val summary = entries?.let(::summarize)
    val allDone = summary != null && summary.total > 0 && summary.taken == summary.total

    // その日の最後の1回を記録した瞬間だけ、紙吹雪を出す（表示した時点で飲み終えていた日は出さない）
    var confettiTrigger by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    LaunchedEffect(day, allDone, summary == null) {
        if (summary == null) return@LaunchedEffect
        val before = previous
        if (motionEnabled && allDone && before != null && before.first == day && !before.second) {
            confettiTrigger++
        }
        previous = day to allDone
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            HeroCard(day = day, today = today, summary = summary, onDayChange = onDayChange)
            when {
                entries == null -> Unit

                entries.isEmpty() -> EmptyState(
                    title = stringResource(
                        if (hasMedications) R.string.empty_no_doses else R.string.empty_no_medications,
                    ),
                    hint = if (hasMedications) null else stringResource(R.string.empty_no_medications_hint),
                    actionLabel = if (hasMedications) null else stringResource(R.string.add_medication),
                    onAction = onAddMedication,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 16.dp + LocalBottomBarInset.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // これから飲む分を時間順に上へ、飲んだ分はその下へ分ける。
                    // 記録するとカードは同じキーのまま下の区分へ移るので、動いて見える。
                    val (pending, taken) = entries.partition { !it.taken }
                    val now = nowMinute.takeIf { day == today }
                    val doseCard: @Composable (DoseEntry, Modifier) -> Unit = { entry, modifier ->
                        DoseCard(
                            entry = entry,
                            modifier = modifier,
                            onClick = {
                                if (entry.taken) {
                                    undoTarget = entry
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onSetTaken(entry, true)
                                }
                            },
                            onImageClick = { enlarged = entry.medication },
                        )
                    }

                    if (pending.isNotEmpty()) {
                        item(key = "pending-title") {
                            SectionHeader(
                                title = stringResource(
                                    if (day == today) R.string.section_upcoming else R.string.section_unrecorded,
                                ),
                                count = pending.size,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        // 「あと◯分」は、まだ時間が来ていない中でいちばん早い回にだけ出す
                        val nextMinute = now?.let { current -> pending.map { it.minuteOfDay }.filter { it >= current }.minOrNull() }
                        pending.groupBy { it.minuteOfDay }.forEach { (minute, group) ->
                            item(key = "pending-time-$minute") {
                                TimeHeader(
                                    minute = minute,
                                    done = false,
                                    status = when {
                                        now == null -> null
                                        minute < now -> DueStatus.Overdue
                                        minute == nextMinute -> DueStatus.In(minute - now)
                                        else -> null
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            items(group, key = { "${it.medication.id}-${it.minuteOfDay}" }) { entry ->
                                doseCard(entry, Modifier.animateItem())
                            }
                        }
                    }

                    if (taken.isNotEmpty()) {
                        item(key = "taken-title") {
                            SectionHeader(
                                title = stringResource(R.string.section_taken_list),
                                count = taken.size,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        taken.groupBy { it.minuteOfDay }.forEach { (minute, group) ->
                            item(key = "taken-time-$minute") {
                                TimeHeader(minute = minute, done = true, status = null, modifier = Modifier.animateItem())
                            }
                            items(group, key = { "${it.medication.id}-${it.minuteOfDay}" }) { entry ->
                                doseCard(entry, Modifier.animateItem())
                            }
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = confettiTrigger, colors = LocalBrand.current.confetti)
    }

    enlarged?.let { medication ->
        medication.imagePath?.let { ImageDialog(medication.name, it, onDismiss = { enlarged = null }) }
    }

    undoTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text(stringResource(R.string.undo_title)) },
            text = {
                Text(stringResource(R.string.undo_message, entry.medication.name, formatMinutes(entry.minuteOfDay)))
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetTaken(entry, false)
                    undoTarget = null
                }) { Text(stringResource(R.string.undo_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { undoTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** 日付の切り替えと、その日の進み具合をまとめたグラデーションのカード。 */
@Composable
private fun HeroCard(day: Long, today: Long, summary: DaySummary?, onDayChange: (Long) -> Unit) {
    val brand = LocalBrand.current
    val shape = MaterialTheme.shapes.extraLarge
    val onHero = brand.onHero
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            .shadow(
                elevation = if (brand.isDark) 0.dp else 16.dp,
                shape = shape,
                ambientColor = brand.cardShadow.copy(alpha = 0.25f),
                spotColor = brand.cardShadow.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(brand.heroStart, brand.heroEnd)))
            .drawBehind {
                // 奥行きを出すための淡い光の円
                drawCircle(
                    Color.White.copy(alpha = 0.10f),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.95f, -size.height * 0.05f),
                )
                drawCircle(
                    Color.White.copy(alpha = 0.07f),
                    radius = size.width * 0.30f,
                    center = Offset(size.width * 0.08f, size.height * 1.10f),
                )
            }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val arrowColors = IconButtonDefaults.iconButtonColors(
                contentColor = onHero,
                disabledContentColor = onHero.copy(alpha = 0.35f),
            )
            IconButton(onClick = { onDayChange(day - 1) }, colors = arrowColors) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDay(day), style = MaterialTheme.typography.titleLarge, color = onHero)
                val relative = when (day) {
                    today -> stringResource(R.string.today)
                    today - 1 -> stringResource(R.string.yesterday)
                    else -> null
                }
                if (relative != null) {
                    Text(relative, style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.8f))
                }
            }
            IconButton(onClick = { onDayChange(day + 1) }, enabled = day < today, colors = arrowColors) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
            }
        }

        if (summary != null && summary.total > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                ProgressRing(
                    progress = summary.taken.toFloat() / summary.total,
                    color = onHero,
                    trackColor = onHero.copy(alpha = 0.22f),
                    strokeWidth = 11.dp,
                    // 同じ内容を右の文章で伝えているので、読み上げでは重ねて読まない
                    modifier = Modifier
                        .size(112.dp)
                        .clearAndSetSemantics { },
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(
                            targetState = summary.taken,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 2 } + fadeOut())
                            },
                            label = "takenCount",
                        ) { taken ->
                            Text(taken.toString(), style = MaterialTheme.typography.displaySmall, color = onHero)
                        }
                        Text(
                            " / ${summary.total}",
                            style = MaterialTheme.typography.titleMedium,
                            color = onHero.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    val done = summary.taken == summary.total
                    Text(
                        if (done) {
                            stringResource(R.string.progress_all_done)
                        } else {
                            stringResource(R.string.hero_remaining, summary.total - summary.taken)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = onHero,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.progress_count, summary.taken, summary.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onHero.copy(alpha = 0.85f),
                    )
                    if (done) {
                        Text(
                            stringResource(
                                if (day == today) R.string.hero_all_done_sub else R.string.hero_past_done_sub,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = onHero.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        if (day != today) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onDayChange(today) },
                border = BorderStroke(1.dp, onHero.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.back_to_today), color = onHero)
            }
        }
    }
}

/** 時間の見出しの横に出す、予定までの残り時間。 */
private sealed interface DueStatus {
    data object Overdue : DueStatus

    data class In(val minutes: Int) : DueStatus
}

/** 「これから飲むお薬」「飲んだお薬」の区分の見出し。 */
@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                stringResource(R.string.dose_count, count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** 時間ごとの見出し。飲んだ分は丸に色がつき、これから飲む分には残り時間を添える。 */
@Composable
private fun TimeHeader(minute: Int, done: Boolean, status: DueStatus?, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(formatMinutes(minute), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        if (status != null) {
            Spacer(Modifier.width(12.dp))
            val overdue = status is DueStatus.Overdue
            Surface(
                color = if (overdue) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (overdue) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    when (status) {
                        DueStatus.Overdue -> stringResource(R.string.overdue)
                        is DueStatus.In -> when {
                            status.minutes == 0 -> stringResource(R.string.due_now)
                            status.minutes < 60 -> stringResource(R.string.due_in_minutes, status.minutes)
                            status.minutes % 60 == 0 -> stringResource(R.string.due_in_hours, status.minutes / 60)
                            else -> stringResource(
                                R.string.due_in_hours_minutes,
                                status.minutes / 60,
                                status.minutes % 60,
                            )
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun DoseCard(entry: DoseEntry, onClick: () -> Unit, onImageClick: () -> Unit, modifier: Modifier = Modifier) {
    val medication = entry.medication
    val container by animateColorAsState(
        targetValue = if (entry.taken) MaterialTheme.colorScheme.primaryContainer else LocalBrand.current.card,
        animationSpec = tween(350),
        label = "doseCardColor",
    )
    AppCard(onClick = onClick, color = container, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedicationAvatar(medication, onImageClick = onImageClick)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(medication.name, style = MaterialTheme.typography.titleMedium)
                val detail = medicationDetail(medication, showTiming = medication.isMealDose(entry.minuteOfDay))
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.takenAt?.let { stringResource(R.string.taken_at, formatClock(it)) }
                        ?: stringResource(R.string.not_taken),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry.taken) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            // 「飲んだ」ボタンが、弾むように出てくるチェックの印に入れ替わる
            AnimatedContent(
                targetState = entry.taken,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                        (scaleOut(tween(120)) + fadeOut(tween(120)))
                },
                contentAlignment = Alignment.Center,
                label = "doseAction",
            ) { taken ->
                if (taken) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.taken),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else {
                    Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.mark_taken))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String?, actionLabel: String?, onAction: () -> Unit) {
    val brand = LocalBrand.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = LocalBottomBarInset.current),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_notification),
                    contentDescription = null,
                    tint = brand.heroStart.takeUnless { brand.isDark } ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAction, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** 写真を大きく表示する。 */
@Composable
private fun ImageDialog(name: String, fileName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            MedicationImage(
                fileName = fileName,
                contentDescription = stringResource(R.string.photo_description, name),
                contentScale = ContentScale.Fit,
                maxSizePx = 1280,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
