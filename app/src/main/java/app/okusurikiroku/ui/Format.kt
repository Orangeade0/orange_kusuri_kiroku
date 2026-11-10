package app.okusurikiroku.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.okusurikiroku.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 0時からの分を "8:05" の形にする。 */
fun formatMinutes(minuteOfDay: Int): String =
    String.format(Locale.ROOT, "%d:%02d", minuteOfDay / 60, minuteOfDay % 60)

fun formatClock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    return formatMinutes(time.hour * 60 + time.minute)
}

/** 表示中の言語（日本語か中国語）のロケール。曜日などの表記に使う。 */
@Composable
fun displayLocale(): Locale {
    val tag = stringResource(R.string.locale_tag)
    return remember(tag) { Locale.forLanguageTag(tag) }
}

/** "11月10日(火)" のような日付。 */
@Composable
fun formatDay(epochDay: Long): String {
    val pattern = stringResource(R.string.date_pattern)
    val locale = displayLocale()
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    return LocalDate.ofEpochDay(epochDay).format(formatter)
}
