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
 *   1. 打开目标应用（解锁状态直接打开；锁屏状态先弹出全屏 WakeActivity 让用户点击打开）
 *   2. 持有 Wakelock 保活，停留 durationMin 分钟
 *   3. 返回桌面并尝试关闭目标应用（kill 兜底 + 无障碍）
 *   4. 写日志、清理超量日志、排下一轮（重复类）或关闭一次性任务
 *   5. 停止自身；若此刻没有任何界面在前台，直接终结进程（严格休眠）
 */
class MonitorService : Service() {

    private val CHANNEL = "monitor_channel"
    private var job: Job? = null
    private var waitJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // 当前任务上下文（成员变量，便于锁屏场景下由 WakeActivity 回调启动停留计时）
    private var pkg = ""
    private var app = ""
    private var repeat = ""
    private var taskId = -1L
    private var duration = 1
    private var openOk = true
    private var openReason = ""
    private var fgsFailed = false
    private var dwellStarted = false

    companion object {
        const val ACTION_MARK_OPENED = "com.taskhelper.MARK_OPENED"
        @Volatile var isExecuting = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 锁屏场景：用户在 WakeActivity 点击「打开」后回调，开始停留计时
        if (intent?.action == ACTION_MARK_OPENED) {
            openOk = intent.getBooleanExtra("open_ok", false)
            openReason = intent.getStringExtra("open_reason") ?: ""
            startDwell()
            return START_NOT_STICKY
        }

        val tid = intent?.getLongExtra("task_id", -1L) ?: -1L
        val p = intent?.getStringExtra("pkg") ?: return START_NOT_STICKY
        val d = intent.getIntExtra("duration", 1)
        val a = intent.getStringExtra("app") ?: ""
        val r = intent?.getStringExtra("repeat") ?: ""

        pkg = p; app = a; repeat = r; taskId = tid; duration = d
        isExecuting = true

        // 先尝试启动前台（通知），失败也不影响「打开应用」
        try {
            startForeground(1, buildNotification("正在打开 $app"))
        } catch (e: Throwable) {
            fgsFailed = true
        }

        // 仅一次任务：立即标记已触发，跨重启/被杀保证只真正响一次
        if (repeat == "仅一次") FiredStore.markFired(this, taskId)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try { km.isKeyguardLocked } catch (_: Throwable) { false }
        } else false

        if (!locked) {
            // 解锁状态：直接打开目标应用并开始停留计时
            val (ok, reason) = openApp(pkg)
            openOk = ok
            openReason = reason
            startDwell()
        } else {
            // 锁屏状态：无法在锁屏上直接打开第三方应用，弹出全屏 WakeActivity 引导用户解锁后点击打开
            val wake = Intent(this, WakeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("pkg", pkg)
                putExtra("app", app)
                putExtra("duration", duration)
                putExtra("repeat", repeat)
                putExtra("task_id", taskId)
                putExtra("fgs_failed", fgsFailed)
            }
            try { startActivity(wake) } catch (_: Throwable) {}
            // 持有一段较短 wakelock 等待用户解锁操作；超时未操作则记录未执行并退出
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock?.releaseSafe()
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskHelper::Monitor").apply {
                acquire(10 * 60_000L)
            }
            startWaitTimeout()
        }
        return START_NOT_STICKY
    }

    /** 锁屏等待超时：用户在限定时间内未手动打开，记录未执行并退出。 */
    private fun startWaitTimeout() {
        val waitMs = (minOf(duration, 3) * 60_000L).coerceAtLeast(20_000L)
        waitJob = CoroutineScope(Dispatchers.Main).launch {
            delay(waitMs)
            if (!dwellStarted) {
                runBlocking(Dispatchers.IO) {
                    val db = AppDatabase.get(this@MonitorService)
                    db.logDao().insert(buildLog(app, duration, false, "锁屏状态下未在限定时间内手动打开，任务未执行"))
                    db.logDao().trim()
                    rescheduleNext(this@MonitorService, taskId)
                }
                isExecuting = false
                stopSelf()
            }
        }
    }

    /** 开始停留计时：回到桌面 + 停留 duration 分钟 + 关闭目标 + 写日志 + 排下一轮。 */
    private fun startDwell() {
        if (dwellStarted) return
        dwellStarted = true
        waitJob?.cancel()
        wakeLock?.releaseSafe()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskHelper::Monitor").apply {
            acquire(duration * 60_000L + 10_000L)
        }

        job = CoroutineScope(Dispatchers.Main).launch {
            delay(duration * 60_000L)
            goHomeAndClose(pkg)
            wakeLock?.releaseSafe()
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

    /** 停留结束后：先回桌面把目标切到后台，再 kill 兜底 + 无障碍关闭。 */
    private suspend fun goHomeAndClose(pkg: String) {
        // 1) 回到桌面，把目标应用切到后台
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(home) } catch (_: Throwable) {}
        // 2) 给系统一点时间把目标真正切到后台，否则 kill 对前台应用无效
        delay(500)
        // 3) 结束后台进程（目标已非前台，多数机型可杀）
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("MissingPermission")
        am.killBackgroundProcesses(pkg)
        // 4) 无障碍兜底：把目标从最近任务移除（需在「我的」开启无障碍服务）
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
        waitJob?.cancel()
        isExecuting = false
        wakeLock?.releaseSafe()
        super.onDestroy()
    }
}

private fun PowerManager.WakeLock?.releaseSafe() {
    try { if (this?.isHeld == true) release() } catch (_: Throwable) {}
}
