package app.okusurikiroku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import app.okusurikiroku.data.Medication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.okusurikiroku.R
import app.okusurikiroku.domain.DoseEntry
import app.okusurikiroku.domain.summarize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    day: Long,
    today: Long,
    entries: List<DoseEntry>?,
    hasMedications: Boolean,
    onDayChange: (Long) -> Unit,
    onSetTaken: (DoseEntry, Boolean) -> Unit,
    onAddMedication: () -> Unit,
) {
    var undoTarget by remember { mutableStateOf<DoseEntry?>(null) }
    var enlarged by remember { mutableStateOf<Medication?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onDayChange(day - 1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day))
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatDay(day), style = MaterialTheme.typography.titleLarge)
                        val relative = when (day) {
                            today -> stringResource(R.string.today)
                            today - 1 -> stringResource(R.string.yesterday)
                            else -> null
                        }
                        if (relative != null) {
                            Text(
                                relative,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onDayChange(day + 1) }, enabled = day < today) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (day != today) {
                TextButton(
                    onClick = { onDayChange(today) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.back_to_today))
                }
            }
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

                else -> {
                    ProgressCard(entries)
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entries.groupBy { it.minuteOfDay }.forEach { (minute, group) ->
                            item(key = "time-$minute") {
                                Text(
                                    formatMinutes(minute),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                                )
                            }
                            items(group, key = { "${it.medication.id}-${it.minuteOfDay}" }) { entry ->
                                DoseCard(
                                    entry = entry,
                                    onClick = {
                                        if (entry.taken) undoTarget = entry else onSetTaken(entry, true)
                                    },
                                    onImageClick = { enlarged = entry.medication },
                                )
                            }
                        }
                    }
                }
            }
        }
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

@Composable
private fun ProgressCard(entries: List<DoseEntry>) {
    val summary = summarize(entries)
    val done = summary.taken == summary.total
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.progress_count, summary.taken, summary.total),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (done) {
                    Text(
                        stringResource(R.string.progress_all_done),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { summary.taken.toFloat() / summary.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DoseCard(entry: DoseEntry, onClick: () -> Unit, onImageClick: () -> Unit) {
    val medication = entry.medication
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (entry.taken) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            medication.imagePath?.let { image ->
                MedicationImage(
                    fileName = image,
                    contentDescription = stringResource(R.string.photo_enlarge, medication.name),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onImageClick),
                )
                Spacer(Modifier.width(12.dp))
            }
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
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.takenAt?.let { stringResource(R.string.taken_at, formatClock(it)) }
                        ?: stringResource(R.string.not_taken),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (entry.taken) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.taken),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(36.dp),
                )
            } else {
                Button(onClick = onClick) { Text(stringResource(R.string.mark_taken)) }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String?, actionLabel: String?, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAction) { Text(actionLabel) }
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
