package com.taskhelper

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val onEdit: (TaskEntity) -> Unit,
    private val onDelete: (TaskEntity) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val list = mutableListOf<TaskEntity>()

    fun submit(items: List<TaskEntity>) {
        list.clear()
        list.addAll(items)
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
        h.name.text = t.appName
        val base = String.format(
            "%02d:%02d · 停留 %d 分钟 · %s",
            t.hour, t.minute, t.durationMin, t.repeat
        )
        // 缺少精确闹钟权限时，在任务项上给出红标提示
        h.meta.text = if (PendingAuthStore.contains(h.itemView.context, t.id)) {
            "$base  ·  ⚠ 需授权精确闹钟"
        } else {
            base
        }
        h.icon.setBackgroundColor(Color.parseColor("#4C6FFF"))
        h.edit.setOnClickListener { onEdit(t) }
        h.delete.setOnClickListener { onDelete(t) }
    }

    override fun getItemCount() = list.size
}
