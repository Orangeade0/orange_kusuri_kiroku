package app.okusurikiroku.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    val firstDay = month.atDay(1)
    val isCurrentMonth = month.atEndOfMonth().toEpochDay() >= today
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            MonthRateCard(summaries.values)
            Spacer(Modifier.height(12.dp))

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
            Spacer(Modifier.height(4.dp))

            // 日曜始まり。月初の曜日ぶんだけ空きマスを入れる
            val leadingBlanks = firstDay.dayOfWeek.value % 7
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonthRateCard(summaries: Collection<DaySummary>) {
    val total = summaries.sumOf { it.total }
    val taken = summaries.sumOf { it.taken }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (total == 0) {
                Text(stringResource(R.string.month_no_data), style = MaterialTheme.typography.titleMedium)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.month_rate, taken * 100 / total),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.month_rate_detail, taken, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { taken.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
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
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isFuture, onClick = onClick)
            .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .then(
                    if (isToday) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isFuture) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            if (hasDoses) StatusBadge(summary)
        }
    }
}

/** 「飲んだ回数/予定回数」。色だけに頼らず数字でも状態がわかるようにする。 */
@Composable
private fun StatusBadge(summary: DaySummary) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground, border) = when (summary.taken) {
        summary.total -> Triple(colors.primary, colors.onPrimary, null)
        0 -> Triple(Color.Transparent, colors.error, BorderStroke(1.dp, colors.error))
        else -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, null)
    }
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(50), border = border) {
        Text(
            "${summary.taken}/${summary.total}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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
