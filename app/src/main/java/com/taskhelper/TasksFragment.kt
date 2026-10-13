package com.taskhelper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private lateinit var adapter: TaskAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TaskAdapter(
            onEdit = { task ->
                startActivity(
                    Intent(requireContext(), AddEditActivity::class.java)
                        .putExtra("task_id", task.id)
                )
            },
            onDelete = { task -> confirmDelete(task) }
        )

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.task_list)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // 空状态引导按钮 → 新建任务
        view.findViewById<View>(R.id.empty_cta).setOnClickListener {
            startActivity(Intent(requireContext(), AddEditActivity::class.java))
        }
        // 需授权横幅 → 跳系统设置
        view.findViewById<View>(R.id.warn_banner).setOnClickListener {
            App.suspendDormantUntil = System.currentTimeMillis() + 15000
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val ctx = requireContext()
            val db = AppDatabase.get(ctx)
            val tasks = db.taskDao().all().first()
            // 统计各应用近期失败次数（取最近 50 条日志），用于在任务卡片上提示
            val failByApp = db.logDao().recent().first()
                .filter { !it.success }
                .groupingBy { it.appName }
                .eachCount()
            val active = tasks.filter { it.enabled }
            view?.let { v ->
                val empty = tasks.isEmpty()
                v.findViewById<View>(R.id.empty_state).visibility = if (empty) View.VISIBLE else View.GONE
                v.findViewById<View>(R.id.dormant_banner).visibility =
                    if (!empty && active.isEmpty()) View.VISIBLE else View.GONE
                v.findViewById<View>(R.id.overview).visibility =
                    if (active.isEmpty()) View.GONE else View.VISIBLE
                v.findViewById<TextView>(R.id.ov_count).text = "${active.size} 项待执行"
                v.findViewById<TextView>(R.id.ov_next).text = active.minByOrNull { it.hour * 60 + it.minute }?.let {
                    "下次执行：今天 ${String.format("%02d:%02d", it.hour, it.minute)} · ${it.appName}"
                } ?: "下次执行：—"
                val needAuth = tasks.any { PendingAuthStore.contains(ctx, it.id) }
                v.findViewById<View>(R.id.warn_banner).visibility = if (needAuth) View.VISIBLE else View.GONE
            }
            adapter.submit(tasks, failByApp)
        }
    }

    private fun confirmDelete(task: TaskEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除任务")
            .setMessage("确定删除「${task.appName}」的定时任务吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> deleteTask(task) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteTask(task: TaskEntity) {
        lifecycleScope.launch {
            AlarmScheduler.cancel(requireContext(), task.id)
            AppDatabase.get(requireContext()).taskDao().delete(task)
            refresh()
        }
    }
}
