package app.okusurikiroku.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * アプリ共通のカード。明るい配色では白地に淡い影、暗い配色では一段明るい地の色で浮かせる。
 * [onClick] を渡すと、押している間すこし縮む。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = LocalBrand.current.card,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val brand = LocalBrand.current
    val elevated = modifier.shadow(
        elevation = if (brand.isDark) 0.dp else 10.dp,
        shape = shape,
        ambientColor = brand.cardShadow.copy(alpha = 0.10f),
        spotColor = brand.cardShadow.copy(alpha = 0.16f),
    )
    if (onClick == null) {
        Surface(modifier = elevated, shape = shape, color = color, content = content)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = Modifier.pressScale(interactionSource).then(elevated),
            shape = shape,
            color = color,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

/** お薬のアイコン。写真があれば写真、なければグラデーションの丸にカプセルの印。 */
@Composable
fun MedicationAvatar(
    medication: Medication,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    onImageClick: (() -> Unit)? = null,
) {
    val brand = LocalBrand.current
    val image = medication.imagePath
    val shape = RoundedCornerShape(size * 0.3f)
    if (image != null) {
        MedicationImage(
            fileName = image,
            contentDescription = stringResource(
                if (onImageClick != null) R.string.photo_enlarge else R.string.photo_description,
                medication.name,
            ),
            modifier = modifier
                .size(size)
                .clip(shape)
                .then(if (onImageClick != null) Modifier.clickable(onClick = onImageClick) else Modifier),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(brand.heroStart, brand.heroEnd))),
        ) {
            Icon(
                painterResource(R.drawable.ic_notification),
                contentDescription = null,
                tint = brand.onHero,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/** カード内の区切りの見出し。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
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
