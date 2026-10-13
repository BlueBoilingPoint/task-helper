package com.taskhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", -1L)
        val pkg = intent.getStringExtra("pkg") ?: return
        val duration = intent.getIntExtra("duration", 1)
        val app = intent.getStringExtra("app") ?: ""
        val repeat = intent.getStringExtra("repeat") ?: ""

        // 透传全部字段给执行服务（含 task_id / repeat，用于重排与“仅一次”防重触）
        val svc = Intent(context, MonitorService::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("pkg", pkg)
            putExtra("duration", duration)
            putExtra("app", app)
            putExtra("repeat", repeat)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
