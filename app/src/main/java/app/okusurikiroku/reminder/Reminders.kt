package app.okusurikiroku.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.okusurikiroku.AppLocale
import app.okusurikiroku.MainActivity
import app.okusurikiroku.OkusuriApp
import app.okusurikiroku.R
import app.okusurikiroku.data.MealTiming
import app.okusurikiroku.domain.DoseEntry
import app.okusurikiroku.domain.entriesForDay
import app.okusurikiroku.domain.nextReminder
import app.okusurikiroku.ui.formatMinutes
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 服用リマインダー。
 *
 * アラームは常に「次の1回」だけを登録し、鳴ったら通知を出して次を登録し直す。
 */
object Reminders {
    const val ACTION_FIRE = "app.okusurikiroku.action.REMINDER_FIRE"
    const val ACTION_TAKEN = "app.okusurikiroku.action.REMINDER_TAKEN"
    const val EXTRA_DAY = "day"
    const val EXTRA_MINUTE = "minute"
    private const val CHANNEL_ID = "reminders"

    private fun repository(context: Context) = (context.applicationContext as OkusuriApp).repository

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 次の服用時刻にアラームを登録する。対象がなければ解除する。 */
    suspend fun scheduleNext(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val next = nextReminder(now, repository(context).getMedications())
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        if (next == null) {
            alarmManager.cancel(firePendingIntent(context, intent))
            return
        }
        intent.putExtra(EXTRA_DAY, next.toLocalDate().toEpochDay())
        intent.putExtra(EXTRA_MINUTE, next.hour * 60 + next.minute)
        val pendingIntent = firePendingIntent(context, intent)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // 判定と登録の間に権限が取り消された場合
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun firePendingIntent(context: Context, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** [day] の [minute] に飲む予定で、まだ記録がなく、通知オンのもの。 */
    private suspend fun dueEntries(context: Context, day: Long, minute: Int): List<DoseEntry> {
        val repository = repository(context)
        return entriesForDay(day, repository.getMedications(), repository.getLogs(day)).filter {
            it.minuteOfDay == minute && !it.taken && it.medication.reminderEnabled && it.medication.isActiveOn(day)
        }
    }

    /** まだ飲んでいないお薬があれば通知を出す。 */
    suspend fun notifyDue(appContext: Context, day: Long, minute: Int) {
        val due = dueEntries(appContext, day, minute)
        if (due.isEmpty() || !notificationsEnabled(appContext)) return
        // 通知の文面も、設定で選んだ言語にする
        val context = AppLocale.wrap(appContext)
        ensureChannel(context)

        val text = due.joinToString(context.getString(R.string.notification_separator)) { entry ->
            val medication = entry.medication
            val timing = when {
                !medication.isMealDose(minute) -> null
                medication.mealTiming == MealTiming.BEFORE -> context.getString(R.string.timing_before)
                medication.mealTiming == MealTiming.WITH -> context.getString(R.string.timing_with)
                medication.mealTiming == MealTiming.AFTER -> context.getString(R.string.timing_after)
                else -> null
            }
            val base = if (medication.dosage.isBlank()) medication.name else "${medication.name} ${medication.dosage}"
            if (timing == null) base else context.getString(R.string.notification_item_with_timing, base, timing)
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markTaken = PendingIntent.getBroadcast(
            context,
            minute + 1,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_TAKEN)
                .putExtra(EXTRA_DAY, day)
                .putExtra(EXTRA_MINUTE, minute),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title, formatMinutes(minute)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notification_action_taken), markTaken)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(minute, notification)
        } catch (e: SecurityException) {
            // 通知の権限がない
        }
    }

    /** 通知の「飲んだ」ボタン: その時間の分をまとめて記録する。 */
    suspend fun markDueTaken(context: Context, day: Long, minute: Int) {
        val repository = repository(context)
        for (entry in dueEntries(context, day, minute)) {
            repository.setTaken(entry.medication.id, day, minute, taken = true)
        }
        NotificationManagerCompat.from(context).cancel(minute)
    }

    /** アプリ側で記録したとき、その時間の分をすべて飲み終えていたら通知を消す。 */
    suspend fun dismissIfDone(context: Context, day: Long, minute: Int) {
        if (dueEntries(context, day, minute).isEmpty()) {
            NotificationManagerCompat.from(context).cancel(minute)
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
