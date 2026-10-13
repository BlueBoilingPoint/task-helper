package com.taskhelper

import android.app.Application
import android.os.Process
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 应用入口。核心职责：实现「严格休眠」。
 *
 * 当 APP 退到后台、且没有任务正在执行时：
 *   1. 先确保所有启用任务的「系统层精确闹钟」已登记（闹钟由系统持有，进程死亡依然有效）；
 *   2. 直接用 Process.killProcess 终结自身进程。
 *
 * 此后 APP 完全不占后台、不耗电，仅在 AlarmManager 到点时由系统冷启动唤醒执行，
 * 执行完毕（MonitorService）后再次退出进程，形成「平时全死、到点才活」的闭环。
 */
class App : Application() {

    companion object {
        const val PREFS = "taskhelper_prefs"
        const val KEY_DORMANT = "dormant_enabled"
        /** 在此时间戳之前不杀进程（跳转系统授权页等外部界面时，避免打断保存流程）。 */
        @Volatile var suspendDormantUntil = 0L
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // 任务正在执行 → 不杀进程，等 MonitorService 自行退出
                if (MonitorService.isExecuting) return
                // 正跳转外部设置页（如精确闹钟授权）的宽限期内 → 不杀，避免打断保存
                if (System.currentTimeMillis() < suspendDormantUntil) return
                // 严格休眠开关被用户关闭（调试用）→ 保持常驻
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                if (!prefs.getBoolean(KEY_DORMANT, true)) return

                // 退到后台：先保证系统闹钟已登记，再彻底退出进程
                runBlocking(Dispatchers.IO) {
                    AlarmScheduler.rescheduleAll(this@App)
                }
                Process.killProcess(Process.myPid())
            }
        })
    }
}
