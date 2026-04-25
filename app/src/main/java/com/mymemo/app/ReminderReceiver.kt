package com.mymemo.app

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mymemo.app.activities.MakeList
import com.mymemo.app.activities.TakeNote
import com.mymemo.app.miscellaneous.Constants
import com.mymemo.app.miscellaneous.Operations
import com.mymemo.app.room.BaseNote
import com.mymemo.app.room.Frequency
import com.mymemo.app.room.IdReminder
import com.mymemo.app.room.MyMemoDatabase
import com.mymemo.app.room.Reminder
import com.mymemo.app.room.Type
import java.util.Calendar
import kotlin.math.min

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (manager.canScheduleExactAlarms()) {
                    rescheduleReminders(context, manager)
                }
            } else rescheduleReminders(context, manager)
        } else {
            val id = intent.getLongExtra(Constants.SelectedBaseNote, 0)
            val database = MyMemoDatabase.getDatabase(context.applicationContext as Application)
            val baseNote = database.getBaseNoteDao().get(id)
            if (baseNote != null) {
                sendNotification(context, baseNote)
                if (baseNote.reminder != null) {
                    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (manager.canScheduleExactAlarms()) {
                            setRecurringReminder(context, manager, id, baseNote.reminder)
                        }
                    } else setRecurringReminder(context, manager, id, baseNote.reminder)
                }
            }
        }
    }

    companion object {

        private fun rescheduleReminders(context: Context, manager: AlarmManager) {
            val database = MyMemoDatabase.getDatabase(context.applicationContext as Application)
            val list = database.getBaseNoteDao().getAllReminders()
            rescheduleReminders(context, manager, list)
        }

        fun rescheduleReminders(context: Context, manager: AlarmManager, list: List<IdReminder>) {
            list.forEach { idReminder ->
                val id = idReminder.id
                val reminder = idReminder.reminder
                if (reminder.timestamp > System.currentTimeMillis()) {
                    setRecurringReminder(context, manager, id, reminder)
                }
            }
        }


        private fun sendNotification(context: Context, baseNote: BaseNote) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = Notification.Builder(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setCategory(Notification.CATEGORY_REMINDER)
            } else builder.setCategory(Notification.CATEGORY_EVENT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "com.mymemo.app.reminders"
                val channel = NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(channel)
                builder.setChannelId(channelId)
            } else {
                builder.setPriority(Notification.PRIORITY_HIGH)
                builder.setDefaults(Notification.DEFAULT_SOUND)
            }

            builder.setSmallIcon(R.drawable.reminder)
            builder.setShowWhen(true)
            builder.setAutoCancel(true)

            val title = baseNote.title
            val body: String
            val empty: String
            when (baseNote.type) {
                Type.NOTE -> {
                    body = baseNote.body
                    empty = context.getString(R.string.empty_note)
                }
                Type.LIST -> {
                    body = Operations.getBody(baseNote.items)
                    empty = context.getString(R.string.empty_list)
                }
            }
            if (title.isNotEmpty() || body.isNotEmpty()) {
                if (title.isNotEmpty()) {
                    builder.setContentTitle(title)
                }
                if (body.isNotEmpty()) {
                    builder.setContentText(body)
                    val style = Notification.BigTextStyle()
                    style.bigText(baseNote.body)
                    builder.setStyle(style)
                }
            } else builder.setContentTitle(empty)

            val clazz = when (baseNote.type) {
                Type.NOTE -> TakeNote::class.java
                Type.LIST -> MakeList::class.java
            }
            val intent = Intent(context, clazz)
            intent.putExtra(Constants.SelectedBaseNote, baseNote.id)
            Operations.embedIntentExtras(intent)
            val onClick = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(onClick)

            /**
             * One note can only have one notification associated with it.
             * Very unlikely that the user will have created more than 2^31 notes during their use of Notally.
             * That would be one note every second for 68 years.
             * Seems safe to convert Long to Int
             */
            manager.notify(baseNote.id.toInt(), builder.build())
        }


        private fun setRecurringReminder(context: Context, manager: AlarmManager, id: Long, reminder: Reminder) {
            when (reminder.frequency) {
                Frequency.ONCE -> {
                    // 单次提醒只响一次，不需要重复调度
                }
                Frequency.DAILY -> setDailyReminder(context, manager, id, reminder.timestamp)
                Frequency.WEEKDAY -> setWeekdayReminder(context, manager, id, reminder.timestamp)
                Frequency.WEEKLY -> setWeeklyReminder(context, manager, id, reminder.timestamp)
                Frequency.MONTHLY -> setMonthlyReminder(context, manager, id, reminder.timestamp)
                Frequency.YEARLY -> setYearlyReminder(context, manager, id, reminder.timestamp)
            }
        }


        private fun setDailyReminder(context: Context, manager: AlarmManager, id: Long, timestamp: Long) {
            val calendar = Calendar.getInstance()
            val currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            calendar.clear()
            calendar.timeInMillis = timestamp
            val reminderHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val reminderMinute = calendar.get(Calendar.MINUTE)

            var difference = currentHourOfDay - reminderHourOfDay
            if (difference == 0) {
                difference = currentMinute - reminderMinute
            }

            val next = if (difference < 0) {
                getReminderForCurrentDay(reminderHourOfDay, reminderMinute)
            } else getReminderForNextDay(reminderHourOfDay, reminderMinute)

            setReminder(context, manager, id, next)
        }

        private fun getReminderForNextDay(hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            calendar.add(Calendar.DAY_OF_MONTH, 1)

            return calendar.timeInMillis
        }

        private fun getReminderForCurrentDay(hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        }

        private fun setWeekdayReminder(context: Context, manager: AlarmManager, id: Long, timestamp: Long) {
            val calendar = Calendar.getInstance()
            val currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            calendar.clear()
            calendar.timeInMillis = timestamp
            val reminderHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val reminderMinute = calendar.get(Calendar.MINUTE)

            var difference = currentHourOfDay - reminderHourOfDay
            if (difference == 0) {
                difference = currentMinute - reminderMinute
            }

            val next = if (difference < 0) {
                getReminderForCurrentWeekday(reminderHourOfDay, reminderMinute)
            } else getReminderForNextWeekday(reminderHourOfDay, reminderMinute)

            setReminder(context, manager, id, next)
        }

        private fun getReminderForCurrentWeekday(hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // 检查是否是周末
            if (isWeekend(calendar)) {
                // 跳到下一个工作日
                return getNextWeekday(calendar, hourOfDay, minute)
            }

            return calendar.timeInMillis
        }

        private fun getReminderForNextWeekday(hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // 先加一天
            calendar.add(Calendar.DAY_OF_MONTH, 1)

            // 检查是否是周末
            while (isWeekend(calendar)) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            return calendar.timeInMillis
        }

        private fun getNextWeekday(calendar: Calendar, hourOfDay: Int, minute: Int): Long {
            val result = Calendar.getInstance()
            result.setTimeInMillis(calendar.timeInMillis)

            // 跳到下一个工作日
            while (isWeekend(result)) {
                result.add(Calendar.DAY_OF_MONTH, 1)
            }

            result.set(Calendar.HOUR_OF_DAY, hourOfDay)
            result.set(Calendar.MINUTE, minute)
            result.set(Calendar.SECOND, 0)
            result.set(Calendar.MILLISECOND, 0)

            return result.timeInMillis
        }

        private fun isWeekend(calendar: Calendar): Boolean {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        }

        private fun setWeeklyReminder(context: Context, manager: AlarmManager, id: Long, timestamp: Long) {
            val calendar = Calendar.getInstance()
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            calendar.clear()
            calendar.timeInMillis = timestamp
            val reminderDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val reminderHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val reminderMinute = calendar.get(Calendar.MINUTE)

            var difference = currentDayOfWeek - reminderDayOfWeek
            if (difference == 0) {
                difference = currentHourOfDay - reminderHourOfDay
                if (difference == 0) {
                    difference = currentMinute - reminderMinute
                }
            }

            val next = if (difference < 0) {
                getReminderForCurrentWeek(reminderDayOfWeek, reminderHourOfDay, reminderMinute)
            } else getReminderForNextWeek(reminderDayOfWeek, reminderHourOfDay, reminderMinute)

            setReminder(context, manager, id, next)
        }

        private fun getReminderForCurrentWeek(dayOfWeek: Int, hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        }

        private fun getReminderForNextWeek(dayOfWeek: Int, hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            calendar.add(Calendar.WEEK_OF_YEAR, 1)

            return calendar.timeInMillis
        }

        private fun setYearlyReminder(context: Context, manager: AlarmManager, id: Long, timestamp: Long) {
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            calendar.clear()
            calendar.timeInMillis = timestamp
            val reminderMonth = calendar.get(Calendar.MONTH)
            val reminderDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val reminderHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val reminderMinute = calendar.get(Calendar.MINUTE)

            var difference = currentMonth - reminderMonth
            if (difference == 0) {
                difference = currentDayOfMonth - reminderDayOfMonth
                if (difference == 0) {
                    difference = currentHourOfDay - reminderHourOfDay
                    if (difference == 0) {
                        difference = currentMinute - reminderMinute
                    }
                }
            }

            val next = if (difference < 0) {
                getReminderForCurrentYear(reminderMonth, reminderDayOfMonth, reminderHourOfDay, reminderMinute)
            } else getReminderForNextYear(reminderMonth, reminderDayOfMonth, reminderHourOfDay, reminderMinute)

            setReminder(context, manager, id, next)
        }

        private fun getReminderForCurrentYear(month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            val max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val finalDay = min(max, dayOfMonth)

            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, finalDay)
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        }

        private fun getReminderForNextYear(month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            val max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val finalDay = min(max, dayOfMonth)

            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, finalDay)
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            calendar.add(Calendar.YEAR, 1)

            return calendar.timeInMillis
        }


        private fun setMonthlyReminder(context: Context, manager: AlarmManager, id: Long, timestamp: Long) {
            val calendar = Calendar.getInstance()
            val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            calendar.clear()
            calendar.timeInMillis = timestamp
            val reminderDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val reminderHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            val reminderMinute = calendar.get(Calendar.MINUTE)

            var difference = currentDayOfMonth - reminderDayOfMonth
            if (difference == 0) {
                difference = currentHourOfDay - reminderHourOfDay
                if (difference == 0) {
                    difference = currentMinute - reminderMinute
                }
            }

            val next = if (difference < 0) {
                getReminderForCurrentMonth(reminderDayOfMonth, reminderHourOfDay, reminderMinute)
            } else getReminderForNextMonth(reminderDayOfMonth, reminderHourOfDay, reminderMinute)

            setReminder(context, manager, id, next)
        }

        private fun getReminderForNextMonth(dayOfMonth: Int, hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            calendar.add(Calendar.MONTH, 1)

            val max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val final = min(max, dayOfMonth)
            calendar.set(Calendar.DAY_OF_MONTH, final)

            return calendar.timeInMillis
        }

        private fun getReminderForCurrentMonth(dayOfMonth: Int, hourOfDay: Int, minute: Int): Long {
            val calendar = Calendar.getInstance()

            val max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val final = min(max, dayOfMonth)

            calendar.set(Calendar.DAY_OF_MONTH, final)
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        }


        fun deleteReminders(context: Context, manager: AlarmManager, ids: List<Long>) {
            ids.forEach { id ->
                val broadcast = getReminderIntent(context, id)
                manager.cancel(broadcast)
            }
        }

        fun setReminder(context: Context, manager: AlarmManager, id: Long, timestamp: Long) {
            val broadcast = getReminderIntent(context, id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, broadcast)
            } else manager.setExact(AlarmManager.RTC_WAKEUP, timestamp, broadcast)
        }


        private fun getReminderIntent(context: Context, id: Long): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
            intent.putExtra(Constants.SelectedBaseNote, id)
            Operations.embedIntentExtras(intent)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }
}




