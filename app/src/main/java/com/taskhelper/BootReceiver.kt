package com.taskhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // 开机、时间/时区/日期变更、应用更新后，统一重排系统闹钟
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_DATE_CHANGED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        runBlocking(Dispatchers.IO) {
            AlarmScheduler.rescheduleAll(context)
        }
    }
}
