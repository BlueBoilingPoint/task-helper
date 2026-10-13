package com.taskhelper

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val onEdit: (TaskEntity) -> Unit,
    private val onDelete: (TaskEntity) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val list = mutableListOf<TaskEntity>()
    private var failByApp: Map<String, Int> = emptyMap()

    fun submit(items: List<TaskEntity>, failByApp: Map<String, Int> = emptyMap()) {
        list.clear()
        list.addAll(items)
        this.failByApp = failByApp
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: View = v.findViewById(R.id.app_icon)
        val name: TextView = v.findViewById(R.id.task_name)
        val meta: TextView = v.findViewById(R.id.task_meta)
        val edit: ImageButton = v.findViewById(R.id.btn_edit)
        val delete: ImageButton = v.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val t = list[i]
        val ctx = h.itemView.context
        h.name.text = t.appName

        // 执行时间（HH:MM）使用黑色突出显示，停留时长与重复规则保持次要灰；
        // 缺少精确闹钟权限时追加红色提醒；该应用近期有失败则追加红色失败次数
        val timeStr = String.format("%02d:%02d", t.hour, t.minute)
        val restBase = String.format(" · 停留 %d 分钟 · %s", t.durationMin, t.repeat)
        val warn = PendingAuthStore.contains(ctx, t.id)
        val warnStr = if (warn) "  ·  ⚠ 需授权精确闹钟" else ""
        val failN = failByApp[t.appName] ?: 0
        val failStr = if (failN > 0) "  ·  失败${failN}次" else ""

        val full = timeStr + restBase + warnStr + failStr
        val sp = SpannableString(full)
        val black = ContextCompat.getColor(ctx, android.R.color.black)
        val gray = ContextCompat.getColor(ctx, R.color.text_2)
        val red = ContextCompat.getColor(ctx, R.color.danger)
        val timeEnd = timeStr.length
        val restEnd = timeEnd + restBase.length
        val warnStart = restEnd
        val warnEnd = warnStart + warnStr.length
        val failStart = warnEnd
        sp.setSpan(ForegroundColorSpan(black), 0, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(ForegroundColorSpan(gray), timeEnd, restEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (warn) {
            sp.setSpan(ForegroundColorSpan(red), warnStart, warnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (failN > 0) {
            sp.setSpan(ForegroundColorSpan(red), failStart, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        h.meta.text = sp

        h.icon.setBackgroundColor(Color.parseColor("#4C6FFF"))
        h.edit.setOnClickListener { onEdit(t) }
        h.delete.setOnClickListener { onDelete(t) }
    }

    override fun getItemCount() = list.size
}
