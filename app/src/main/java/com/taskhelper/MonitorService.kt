package com.taskhelper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 闹钟触发后由 AlarmReceiver 启动的前台服务（仅在“执行任务”窗口内存在）：
 *   1. 打开目标应用
 *   2. 持有 Wakelock 保活，停留 durationMin 分钟
 *   3. 返回桌面并尝试关闭目标应用（kill 兜底 + 无障碍）
 *   4. 写日志、清理超量日志、排下一轮（重复类）或关闭一次性任务
 *   5. 停止自身；若此刻没有界面在前台，直接终结进程（严格休眠）
 */
class MonitorService : Service() {

    private val CHANNEL = "monitor_channel"
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        @Volatile var isExecuting = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra("task_id", -1L) ?: -1L
        val pkg = intent?.getStringExtra("pkg") ?: return START_NOT_STICKY
        val duration = intent.getIntExtra("duration", 1)
        val app = intent.getStringExtra("app") ?: ""
        val repeat = intent?.getStringExtra("repeat") ?: ""

        isExecuting = true

        // 先尝试打开目标应用（核心诉求），再启动前台服务；即使通知权限缺失导致
        // startForeground 失败，也至少完成「打开应用」，不会整段崩溃。
        val (openOk, openReason) = openApp(pkg)

        var fgsFailed = false
        try {
            startForeground(1, buildNotification("正在打开 $app"))
        } catch (e: Throwable) {
            fgsFailed = true
        }

        // 仅一次任务：立即标记已触发，跨重启/被杀保证只真正响一次
        if (repeat == "仅一次") FiredStore.markFired(this, taskId)

        // 执行期间持 PARTIAL_WAKE_LOCK，防止灭屏 Doze 打断打开/关闭
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskHelper::Monitor").apply {
            acquire(duration * 60_000L + 10_000L)
        }

        job = CoroutineScope(Dispatchers.Main).launch {
            delay(duration * 60_000L)
            goHomeAndClose(pkg)
            try { wakeLock?.release() } catch (_: Throwable) {}
            wakeLock = null

            runBlocking(Dispatchers.IO) {
                val db = AppDatabase.get(this@MonitorService)
                // 成败判定：以「目标应用是否成功启动」为准；前台服务异常记到原因里
                val success = openOk
                val reason = when {
                    !openOk -> openReason
                    fgsFailed -> "提醒：前台服务未启动（通知权限未授予），停留计时可能不准确"
                    else -> ""
                }
                db.logDao().insert(buildLog(app, duration, success, reason))
                db.logDao().trim() // 仅保留最近 100 条
                rescheduleNext(this@MonitorService, taskId)
            }

            isExecuting = false
            stopSelf()

            // 严格休眠：若此刻没有任何界面在前台，直接终结进程，只留系统闹钟唤醒
            val foreground = ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            if (!foreground) {
                Process.killProcess(Process.myPid())
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 打开目标应用。返回是否成功及失败原因（包名无效/已卸载/系统拦截均记为失败）。
     */
    private fun openApp(pkg: String): Pair<Boolean, String> {
        if (pkg.isBlank()) return false to "未选择应用（包名为空）"
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        return if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(launch)
                true to ""
            } catch (e: Throwable) {
                false to "启动 $pkg 失败：${e.message ?: e.javaClass.simpleName}"
            }
        } else {
            false to "找不到启动入口（可能已卸载：$pkg）"
        }
    }

    private fun goHomeAndClose(pkg: String) {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(home)
        // 兜底：先尝试结束后台目标进程（多数机型有效）
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("MissingPermission")
        am.killBackgroundProcesses(pkg)
        // 再尝试无障碍关闭“最近任务”中的目标（部分机型需配合电池白名单）
        if (CloseAppAccessibilityService.isEnabled()) {
            try { CloseAppAccessibilityService.requestClose() } catch (_: Throwable) {}
        }
    }

    /** 执行完后重排：重复类排下一次；仅一次则禁用并取消，不再触发。 */
    private suspend fun rescheduleNext(context: Context, taskId: Long) {
        val db = AppDatabase.get(context)
        val task = db.taskDao().getById(taskId) ?: return
        if (!task.enabled) {
            AlarmScheduler.cancel(context, taskId)
            return
        }
        if (task.repeat == "仅一次") {
            val updated = task.copy(enabled = false)
            db.taskDao().update(updated)
            AlarmScheduler.cancel(context, taskId)
        } else {
            AlarmScheduler.schedule(context, task)
        }
    }

    private fun buildLog(app: String, duration: Int, success: Boolean, reason: String): LogEntity {
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return LogEntity(
            time = fmt.format(Date()),
            appName = app,
            durationMin = duration,
            success = success,
            reason = reason
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(CHANNEL, "任务监控", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("定时任务助手")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

    override fun onDestroy() {
        job?.cancel()
        isExecuting = false
        try { wakeLock?.release() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
