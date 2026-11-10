package app.okusurikiroku

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.okusurikiroku.data.AppDatabase
import app.okusurikiroku.data.MedRepository
import app.okusurikiroku.data.Medication
import app.okusurikiroku.reminder.ReminderReceiver
import app.okusurikiroku.reminder.Reminders
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RemindersTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repository: MedRepository
    private val now = LocalDateTime.of(2026, 11, 10, 7, 30)
    private val today = now.toLocalDate().toEpochDay()

    @Before
    fun setUp() {
        val (database, repo) = installInMemoryRepository()
        db = database
        repository = repo
    }

    @After
    fun tearDown() = db.close()

    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Test
    fun schedulesAlarmAtNextDoseAndCancelsWhenNothingIsLeft() = runBlocking {
        val id = repository.save(Medication(name = "A", dosage = "1錠", manualTimes = listOf(480, 1140), startDay = 0), today)
        Reminders.scheduleNext(app, now)

        val alarm = alarms.scheduledAlarms.single()
        val expected = LocalDateTime.of(2026, 11, 10, 8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, alarm.triggerAtMs)
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(Reminders.ACTION_FIRE, intent.action)
        assertEquals(today, intent.getLongExtra(Reminders.EXTRA_DAY, -1))
        assertEquals(480, intent.getIntExtra(Reminders.EXTRA_MINUTE, -1))

        repository.delete(id, today)
        Reminders.scheduleNext(app, now)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun notifiesOnlyUntakenMedicationsAndTheActionRecordsThem() = runBlocking {
        val a = repository.save(Medication(name = "A", dosage = "1錠", manualTimes = listOf(480), startDay = 0), today)
        val b = repository.save(Medication(name = "B", manualTimes = listOf(480), startDay = 0), today)
        repository.save(Medication(name = "C", manualTimes = listOf(480), reminderEnabled = false, startDay = 0), today)
        repository.setTaken(b, today, 480, taken = true)

        Reminders.notifyDue(app, today, 480)
        val notification = notifications.allNotifications.single()
        assertEquals("お薬の時間です（8:00）", shadowOf(notification).contentTitle)
        assertEquals("A 1錠", shadowOf(notification).contentText)

        // 通知の「飲んだ」ボタン
        val actionIntent = shadowOf(notification.actions.single().actionIntent).savedIntent
        assertEquals(Reminders.ACTION_TAKEN, actionIntent.action)
        Reminders.markDueTaken(
            app,
            actionIntent.getLongExtra(Reminders.EXTRA_DAY, -1),
            actionIntent.getIntExtra(Reminders.EXTRA_MINUTE, -1),
        )
        assertEquals(setOf(a, b), repository.getLogs(today).map { it.medicationId }.toSet())
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun noNotificationWhenEverythingIsAlreadyTaken() = runBlocking {
        val a = repository.save(Medication(name = "A", manualTimes = listOf(480), startDay = 0), today)
        repository.setTaken(a, today, 480, taken = true)
        Reminders.notifyDue(app, today, 480)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun recordingInTheAppDismissesTheNotification() = runBlocking {
        val a = repository.save(Medication(name = "A", manualTimes = listOf(480), startDay = 0), today)
        Reminders.notifyDue(app, today, 480)
        assertEquals(1, notifications.allNotifications.size)

        repository.setTaken(a, today, 480, taken = true)
        Reminders.dismissIfDone(app, today, 480)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun receiverHandlesBootWithoutCrashing() {
        // goAsync() が null を返す直接呼び出しでも落ちないこと
        ReminderReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(alarms.nextScheduledAlarm)
    }
}
