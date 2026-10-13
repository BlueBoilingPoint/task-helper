package com.taskhelper

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val tasksFrag = TasksFragment()
    private val logsFrag = LogsFragment()
    private val meFrag = SettingsFragment()

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

        findViewById<BottomNavigationView>(R.id.bottom_nav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_tasks -> supportFragmentManager.beginTransaction()
                    .replace(R.id.container, tasksFrag).commit()
                R.id.nav_logs -> supportFragmentManager.beginTransaction()
                    .replace(R.id.container, logsFrag).commit()
                R.id.nav_me -> supportFragmentManager.beginTransaction()
                    .replace(R.id.container, meFrag).commit()
            }
            true
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            startActivity(Intent(this, AddEditActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户刚授予精确闹钟权限时，自动重排此前因无权限而未登记的闹钟
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact && PendingAuthStore.isNotEmpty(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                AlarmScheduler.rescheduleAll(this@MainActivity)
            }
        }
    }
}
