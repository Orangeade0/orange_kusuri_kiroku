package app.okusurikiroku.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.okusurikiroku.R
import app.okusurikiroku.domain.DaySummary
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    month: YearMonth,
    today: Long,
    summaries: Map<Long, DaySummary>,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (Long) -> Unit,
) {
    val isCurrentMonth = month.atEndOfMonth().toEpochDay() >= today
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month))
                    }
                },
                title = { Text(stringResource(R.string.month_title, month.year, month.monthValue)) },
                actions = {
                    IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = !isCurrentMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
                    }
                },
            )
        },
    ) { padding ->
        // 月を替えると、進む向きに合わせて横にすべって入れ替わる
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { direction * it / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -direction * it / 4 } + fadeOut(tween(120)))
            },
            label = "month",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { shownMonth ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                // すべる途中の前の月には、その月の集計だけを出す
                val shown = remember(summaries, shownMonth) {
                    val first = shownMonth.atDay(1).toEpochDay()
                    val last = shownMonth.atEndOfMonth().toEpochDay()
                    summaries.filterKeys { it in first..last }
                }
                Spacer(Modifier.height(4.dp))
                MonthRateCard(shown.values)
                Spacer(Modifier.height(16.dp))
                CalendarCard(shownMonth, today, shown, onDayClick)
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Legend(DaySummary(2, 2), stringResource(R.string.legend_all))
                    Legend(DaySummary(2, 1), stringResource(R.string.legend_partial))
                    Legend(DaySummary(2, 0), stringResource(R.string.legend_none))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.calendar_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp + LocalBottomBarInset.current))
            }
        }
    }
}

@Composable
private fun MonthRateCard(summaries: Collection<DaySummary>) {
    val total = summaries.sumOf { it.total }
    val taken = summaries.sumOf { it.taken }
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (total == 0) {
                Text(stringResource(R.string.month_no_data), style = MaterialTheme.typography.titleMedium)
            } else {
                val percent = taken * 100 / total
                ProgressRing(
                    progress = taken.toFloat() / total,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeWidth = 8.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .clearAndSetSemantics { },
                ) {
                    Text("$percent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(stringResource(R.string.month_rate, percent), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.month_rate_detail, taken, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(month: YearMonth, today: Long, summaries: Map<Long, DaySummary>, onDayClick: (Long) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp)) {
            // 日曜始まり。曜日の表記は表示言語に合わせる（日本語: 日月火…、中国語: 日一二…）
            val locale = displayLocale()
            val weekdays = remember(locale) {
                (0L..6L).map { DayOfWeek.SUNDAY.plus(it).getDisplayName(TextStyle.NARROW, locale) }
            }
            Row(Modifier.fillMaxWidth()) {
                for (weekday in weekdays) {
                    Text(
                        weekday,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // 月初の曜日ぶんだけ空きマスを入れる
            val leadingBlanks = month.atDay(1).dayOfWeek.value % 7
            val cells: List<Int?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth())
            for (week in cells.chunked(7)) {
                Row(Modifier.fillMaxWidth()) {
                    for (dayOfMonth in week) {
                        Box(Modifier.weight(1f)) {
                            if (dayOfMonth != null) {
                                val day = month.atDay(dayOfMonth).toEpochDay()
                                DayCell(
                                    dayOfMonth = dayOfMonth,
                                    summary = summaries[day],
                                    isToday = day == today,
                                    isFuture = day > today,
                                    onClick = { onDayClick(day) },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(dayOfMonth: Int, summary: DaySummary?, isToday: Boolean, isFuture: Boolean, onClick: () -> Unit) {
    val hasDoses = summary != null && summary.total > 0
    val description = if (hasDoses) {
        stringResource(R.string.day_status_description, dayOfMonth, summary.total, summary.taken)
    } else {
        null
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(1.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !isFuture, onClick = onClick)
            .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier)
            .padding(vertical = 5.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                ),
        ) {
            Text(
                dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (hasDoses) StatusBadge(summary)
        }
    }
}

/** 「飲んだ回数/予定回数」。色だけに頼らず数字でも状態がわかるようにする。 */
@Composable
private fun StatusBadge(summary: DaySummary) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground, border) = when (summary.taken) {
        summary.total -> Triple(colors.primaryContainer, colors.onPrimaryContainer, null)
        0 -> Triple(Color.Transparent, colors.error, BorderStroke(1.dp, colors.error))
        else -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, null)
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50), border = border) {
        Text(
            "${summary.taken}/${summary.total}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun Legend(sample: DaySummary, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(sample)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
