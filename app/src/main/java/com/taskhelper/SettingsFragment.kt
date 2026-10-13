package com.taskhelper

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val perms = listOf(
            "精确闹钟" to "定时到点唤醒应用（Android 12+）",
            "电池优化白名单" to "防止系统回收后台任务",
            "自启动 / 后台弹出" to "厂商 ROM 限制，需手动授权",
            "无障碍服务" to "用于自动关闭目标应用（部分机型需配合电池白名单，关闭效果因 ROM 而异）",
            "通知权限" to "执行提醒与状态通知"
        )

        val box = view.findViewById<LinearLayout>(R.id.perm_list)
        perms.forEachIndexed { i, (name, desc) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 14, 12, 14)
            }
            val textCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(requireContext()).apply {
                text = name; textSize = 15f; setTextColor(Color.parseColor("#1A1D26"))
            })
            textCol.addView(TextView(requireContext()).apply {
                text = desc; textSize = 11f; setTextColor(Color.parseColor("#9AA1B2"))
            })
            row.addView(textCol)
            row.addView(TextView(requireContext()).apply {
                text = if (i % 2 == 0) "已开启" else "未开启"
                textSize = 11f
                setTextColor(Color.parseColor(if (i % 2 == 0) "#16924A" else "#C96A12"))
            })
            box.addView(row)
        }

        // 极致省电 / 休眠模式：控制“退到后台即退出进程”的严格休眠行为（默认开启）
        val prefs = requireContext().getSharedPreferences("taskhelper_prefs", 0)
        val swDormant = view.findViewById<Switch>(R.id.sw_dormant)
        swDormant.isChecked = prefs.getBoolean("dormant_enabled", true)
        swDormant.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dormant_enabled", isChecked).apply()
        }
    }
}
