package com.taskhelper

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 锁屏场景下由 MonitorService 拉起的全屏界面：点亮屏幕并覆盖在锁屏之上，
 * 由用户点击「打开 XXX」完成解锁手势后，再启动目标应用并通知服务开始停留计时。
 * （Android 出于安全限制，第三方应用无法在锁屏上自动打开其它应用，必须用户手动确认。）
 */
class WakeActivity : AppCompatActivity() {

    private var pkg = ""
    private var app = ""
    private var duration = 1
    private var taskId = -1L
    private var repeat = ""
    private var fgsFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 允许在锁屏上显示并点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        pkg = intent.getStringExtra("pkg") ?: ""
        app = intent.getStringExtra("app") ?: ""
        duration = intent.getIntExtra("duration", 1)
        taskId = intent.getLongExtra("task_id", -1L)
        repeat = intent.getStringExtra("repeat") ?: ""
        fgsFailed = intent.getBooleanExtra("fgs_failed", false)

        setContentView(R.layout.activity_wake)
        findViewById<TextView>(R.id.wake_title).text = "任务时间到"
        findViewById<TextView>(R.id.wake_sub).text = "即将打开：$app"
        val btn = findViewById<Button>(R.id.wake_open)
        btn.text = "打开 $app"
        btn.setOnClickListener { openTarget() }
    }

    private fun openTarget() {
        var ok = false
        var reason = ""
        if (pkg.isNotBlank()) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(launch)
                    ok = true
                } catch (e: Throwable) {
                    reason = e.message ?: e.javaClass.simpleName
                }
            } else {
                reason = "找不到启动入口（可能已卸载：$pkg）"
            }
        } else {
            reason = "未选择应用（包名为空）"
        }

        // 通知服务：目标已打开，开始停留计时
        val svc = Intent(this, MonitorService::class.java).apply {
            action = MonitorService.ACTION_MARK_OPENED
            putExtra("open_ok", ok)
            putExtra("open_reason", reason)
        }
        try { ContextCompat.startForegroundService(this, svc) } catch (_: Throwable) {}
        finish()
    }
}
