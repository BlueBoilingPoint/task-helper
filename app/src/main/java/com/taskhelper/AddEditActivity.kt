package com.taskhelper

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddEditActivity : AppCompatActivity() {

    private var editingId: Long = -1L
    private var selPkg = ""
    private var selApp = ""
    private var hour = 14
    private var minute = 30
    private var duration = 1
    private var repeat = "每天"
    private val repViews = mutableListOf<TextView>()

    private val pickApp =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                selApp = res.data?.getStringExtra("app") ?: ""
                selPkg = res.data?.getStringExtra("pkg") ?: ""
                val tv = findViewById<TextView>(R.id.sel_app_name)
                tv.text = selApp
                tv.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)

        editingId = intent.getLongExtra("task_id", -1L)
        findViewById<TextView>(R.id.title).text = if (editingId == -1L) "新建任务" else "编辑任务"

        val tp = findViewById<TimePicker>(R.id.time_picker)
        tp.setIs24HourView(true)
        tp.setOnTimeChangedListener { _, h, m -> hour = h; minute = m }

        val seek = findViewById<SeekBar>(R.id.dur_seek)
        val durVal = findViewById<TextView>(R.id.dur_val)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                duration = p + 1
                durVal.text = "$duration 分钟"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        listOf(R.id.preset1 to 1, R.id.preset5 to 5, R.id.preset10 to 10, R.id.preset30 to 30)
            .forEach { (id, v) ->
                findViewById<Button>(id).setOnClickListener {
                    duration = v
                    seek.progress = v - 1
                    durVal.text = "$duration 分钟"
                }
            }

        repViews.addAll(
            listOf(R.id.rep0, R.id.rep1, R.id.rep2, R.id.rep3).map { findViewById<TextView>(it) }
        )
        repViews.forEachIndexed { i, v -> v.setOnClickListener { selectRep(i) } }

        findViewById<LinearLayout>(R.id.pick_app).setOnClickListener {
            pickApp.launch(Intent(this, AppPickerActivity::class.java))
        }

        findViewById<TextView>(R.id.btn_save).setOnClickListener { save() }
        findViewById<Button>(R.id.btn_create).setOnClickListener { save() }
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        if (editingId != -1L) loadEditing()
    }

    private fun selectRep(i: Int) {
        repeat = repViews[i].text.toString()
        repViews.forEachIndexed { idx, v ->
            val on = idx == i
            v.setBackgroundColor(
                if (on) ContextCompat.getColor(this, R.color.primary)
                else android.graphics.Color.TRANSPARENT
            )
            v.setTextColor(
                if (on) android.graphics.Color.WHITE
                else ContextCompat.getColor(this, R.color.text_2)
            )
        }
    }

    private fun loadEditing() {
        CoroutineScope(Dispatchers.IO).launch {
            val t = AppDatabase.get(this@AddEditActivity).taskDao().getById(editingId) ?: return@launch
            selApp = t.appName
            selPkg = t.pkg
            hour = t.hour
            minute = t.minute
            duration = t.durationMin
            repeat = t.repeat
            runOnUiThread {
                findViewById<TextView>(R.id.sel_app_name).text = selApp
                findViewById<TimePicker>(R.id.time_picker).apply {
                    hour = t.hour; minute = t.minute
                }
                findViewById<SeekBar>(R.id.dur_seek).progress = t.durationMin - 1
                findViewById<TextView>(R.id.dur_val).text = "${t.durationMin} 分钟"
                val idx = listOf("每天", "工作日", "周末", "仅一次").indexOf(t.repeat).coerceAtLeast(0)
                selectRep(idx)
            }
        }
    }

    private fun save() {
        if (selPkg.isEmpty()) {
            Toast.makeText(this, "请先选择应用", Toast.LENGTH_SHORT).show()
            return
        }
        // Android 12+ 精确闹钟需用户授权：跳转系统设置前挂起“退后台自杀”，避免打断保存
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                App.suspendDormantUntil = System.currentTimeMillis() + 15000
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }

        val entity = TaskEntity(
            id = if (editingId == -1L) 0 else editingId,
            appName = selApp,
            pkg = selPkg,
            hour = hour,
            minute = minute,
            durationMin = duration,
            repeat = repeat,
            remind = false,
            enabled = true
        )

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(this@AddEditActivity)
            if (editingId == -1L) db.taskDao().insert(entity)
            else db.taskDao().update(entity)
            // 仅一次任务被编辑后重新武装，清除“已触发”标记，允许再次触发
            if (entity.repeat == "仅一次") FiredStore.clear(this@AddEditActivity, entity.id)
            val ok = AlarmScheduler.schedule(this@AddEditActivity, entity)
            runOnUiThread {
                if (!ok) showExactAlarmGuide()
                finish()
            }
        }
    }

    /** 未授予精确闹钟权限时的引导弹窗。 */
    private fun showExactAlarmGuide() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("需要精确闹钟权限")
            .setMessage("未授予“精确闹钟”权限，任务可能无法在指定时间触发。是否前往设置开启？")
            .setPositiveButton("去开启") { _, _ ->
                App.suspendDormantUntil = System.currentTimeMillis() + 15000
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
