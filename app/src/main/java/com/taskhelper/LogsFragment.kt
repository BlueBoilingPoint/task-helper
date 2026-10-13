package com.taskhelper

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LogsFragment : Fragment(R.layout.fragment_logs) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val logs = AppDatabase.get(requireContext()).logDao().recent().first()
            val okPct = if (logs.isEmpty()) 0 else logs.count { it.success } * 100 / logs.size

            view.findViewById<TextView>(R.id.stat_week).text = logs.size.toString()
            view.findViewById<TextView>(R.id.stat_ok).text = "$okPct%"
            // 第三格原误标为「已休眠」，实为失败次数
            view.findViewById<TextView>(R.id.stat_dormant).text =
                logs.count { !it.success }.toString()

            val box = view.findViewById<LinearLayout>(R.id.log_list)
            logs.forEach { l ->
                val summary = if (l.success) {
                    "${l.time}  ${l.appName}  ·  成功"
                } else {
                    "${l.time}  ${l.appName}  ·  失败" +
                        if (l.reason.isNotBlank()) "（${l.reason}）" else ""
                }
                val row = TextView(requireContext()).apply {
                    text = summary
                    setPadding(12, 10, 12, 10)
                    textSize = 14f
                    setTextColor(Color.parseColor(if (l.success) "#1A1D26" else "#EF4444"))
                    isClickable = true
                    setOnClickListener {
                        val detail = buildString {
                            append("时间：${l.time}\n")
                            append("应用：${l.appName}\n")
                            append("停留：${l.durationMin} 分钟\n")
                            append("结果：${if (l.success) "成功" else "失败"}")
                            if (!l.success && l.reason.isNotBlank()) {
                                append("\n原因：${l.reason}")
                            }
                        }
                        AlertDialog.Builder(requireContext())
                            .setTitle("执行详情")
                            .setMessage(detail)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                box.addView(row)
            }
        }
    }
}
