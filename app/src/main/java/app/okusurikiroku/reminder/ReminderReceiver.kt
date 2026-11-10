package app.okusurikiroku.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** アラーム・通知の「飲んだ」ボタン・再起動や時刻変更を受け取る。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val day = intent.getLongExtra(Reminders.EXTRA_DAY, -1)
                val minute = intent.getIntExtra(Reminders.EXTRA_MINUTE, -1)
                when (intent.action) {
                    Reminders.ACTION_FIRE -> {
                        if (minute >= 0) Reminders.notifyDue(appContext, day, minute)
                        Reminders.scheduleNext(appContext)
                    }

                    Reminders.ACTION_TAKEN -> if (minute >= 0) Reminders.markDueTaken(appContext, day, minute)

                    // 再起動・アプリ更新・時刻やタイムゾーンの変更: アラームを登録し直す
                    else -> Reminders.scheduleNext(appContext)
                }
            } finally {
                pending?.finish()
            }
        }
    }
}
