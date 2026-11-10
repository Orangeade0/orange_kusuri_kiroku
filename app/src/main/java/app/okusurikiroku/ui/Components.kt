package app.okusurikiroku.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.okusurikiroku.OkusuriApp
import app.okusurikiroku.R
import app.okusurikiroku.data.MealTiming
import app.okusurikiroku.data.Medication

/** お薬の写真。読み込み中や読み込めないときは地の色だけを出す。 */
@Composable
fun MedicationImage(
    fileName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxSizePx: Int = 512,
) {
    val images = (LocalContext.current.applicationContext as OkusuriApp).images
    val bitmap by produceState<ImageBitmap?>(null, fileName, maxSizePx) {
        value = images.load(fileName, maxSizePx)?.asImageBitmap()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(it, contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun mealTimingLabel(timing: MealTiming): String = stringResource(
    when (timing) {
        MealTiming.NONE -> R.string.timing_none
        MealTiming.BEFORE -> R.string.timing_before
        MealTiming.WITH -> R.string.timing_with
        MealTiming.AFTER -> R.string.timing_after
    },
)

/** カードの2行目: 「1錠・食後・メモ」。[showTiming] は食事に合わせた回のときだけ true にする。 */
@Composable
fun medicationDetail(medication: Medication, showTiming: Boolean): String {
    val timing = if (showTiming && medication.mealTiming != MealTiming.NONE) mealTimingLabel(medication.mealTiming) else ""
    return listOf(medication.dosage, timing, medication.note).filter { it.isNotBlank() }.joinToString("・")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinuteOfDay: Int,
    confirmLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
