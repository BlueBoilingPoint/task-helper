package com.taskhelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val tasksFrag = TasksFragment()
    private val logsFrag = LogsFragment()
    private val meFrag = SettingsFragment()

    private val navIds = listOf(R.id.nav_tasks, R.id.nav_logs, R.id.nav_me)
    private val frags = listOf(tasksFrag, logsFrag, meFrag)

    // Android 13+ 需运行时申请通知权限，否则前台服务（停留计时）无法正常启动
    private val reqNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 无论结果，闹钟时钟 API 已保证触发；通知仅影响状态提示 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                reqNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 冷启动即确保系统层闹钟已登记（应用可能被闹钟从死亡态唤醒而拉起）
        lifecycleScope.launch(Dispatchers.IO) {
            AlarmScheduler.rescheduleAll(this@MainActivity)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, tasksFrag)
            .commit()

        navIds.forEachIndexed { i, id ->
            findViewById<TextView>(id).setOnClickListener {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, frags[i])
                    .commit()
                selectTab(i)
            }
        }
        selectTab(0)

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            startActivity(Intent(this, AddEditActivity::class.java))
        }
    }

    /** 切换底部三个矩形标签的选中态：未选中黑字白底，选中白字蓝底。 */
    private fun selectTab(which: Int) {
        navIds.forEachIndexed { i, id ->
            val tv = findViewById<TextView>(id)
            val sel = i == which
            tv.setBackgroundColor(Color.parseColor(if (sel) "#4C6FFF" else "#FFFFFF"))
            tv.setTextColor(if (sel) Color.WHITE else Color.BLACK)
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台都重新登记闹钟（无需精确闹钟权限，setAlarmClock 已保证可靠触发）
        lifecycleScope.launch(Dispatchers.IO) {
            AlarmScheduler.rescheduleAll(this@MainActivity)
        }
    }
}
