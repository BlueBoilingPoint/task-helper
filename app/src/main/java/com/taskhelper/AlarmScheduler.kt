package com.taskhelper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object AlarmScheduler {

    /**
     * 依据任务时间与重复规则，计算下一次应触发的毫秒时间。
     * - 若今天的时间点已过，顺延到最近的一个未来日期；
     * - 对“工作日 / 周末”，继续向后跳过不符合的日期。
     */
    fun nextTriggerMillis(task: TaskEntity): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        var guard = 0
        while (cal.timeInMillis <= now && guard < 400) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            guard++
        }
        guard = 0
        while (!matchesRepeat(cal, task.repeat) && guard < 14) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            guard++
        }
        return cal.timeInMillis
    }

    private fun matchesRepeat(cal: Calendar, repeat: String): Boolean {
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=周日 .. 7=周六
        return when (repeat) {
            "工作日" -> dow in 2..6
            "周末" -> dow == 1 || dow == 7
            else -> true // 每天 / 仅一次
        }
    }

    /**
     * 为单个任务设定精确闹钟（系统层持有，进程死亡依然有效）。
     * @return true=已登记；false=缺少精确闹钟权限（已记入 PendingAuthStore，需用户在系统设置授予）。
     */
    fun schedule(context: Context, task: TaskEntity): Boolean {
        // 仅一次且已触发：无需再排程
        if (task.repeat == "仅一次" && FiredStore.isFired(context, task.id)) return true

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = nextTriggerMillis(task)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("pkg", task.pkg)
            putExtra("duration", task.durationMin)
            putExtra("app", task.appName)
            putExtra("repeat", task.repeat)
        }
        val pi = PendingIntent.getBroadcast(
            context, task.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock 不依赖「精确闹钟」权限，且享有「后台启动前台服务 / 打开其它应用」的
        // 系统豁免，在小米澎湃 / 华为鸿蒙等严格 OEM 上最可靠；仅在它真正抛 SecurityException
        // 时才降级到普通精确闹钟（此时才需要用户授予精确闹钟权限）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return try {
                val showIntent = PendingIntent.getActivity(
                    context, task.id.toInt() + 100000,
                    Intent(context, MainActivity::class.java)
                        .setAction("com.taskhelper.ALARM_SHOW"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent), pi)
                PendingAuthStore.remove(context, task.id)
                true
            } catch (e: SecurityException) {
                // 极少数 ROM 仍限制 setAlarmClock：降级到 setExact（需精确闹钟权限）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    PendingAuthStore.add(context, task.id)
                    return false
                }
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                PendingAuthStore.remove(context, task.id)
                true
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            PendingAuthStore.remove(context, task.id)
            return true
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /**
     * 全量重排：取消所有任务闹钟，仅重新登记启用中的任务。
     * 在「退到后台」「应用冷启动」「开机」时调用，保证进程退出后只靠闹钟唤醒，
     * 且重复类任务（每天/工作日/周末）能自动排到下一次触发。
     */
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val all = db.taskDao().allList()
        all.forEach { cancel(context, it.id) }
        all.filter { it.enabled }.forEach { schedule(context, it) }
    }
}
