package com.taskhelper

import android.content.Context

private const val PREFS = "taskhelper_prefs"

/**
 * 记录“仅一次”任务是否已触发，跨进程死亡/重启保证只响一次。
 * 用 SP 中的字符串集合存已触发任务 id，避免改动数据库 schema。
 */
object FiredStore {
    private const val KEY = "fired_once_set"
    private fun sp(c: Context) = c.getSharedPreferences(PREFS, 0)

    fun isFired(c: Context, id: Long): Boolean =
        sp(c).getStringSet(KEY, emptySet())?.contains(id.toString()) ?: false

    fun markFired(c: Context, id: Long) {
        val s = HashSet(sp(c).getStringSet(KEY, emptySet()) ?: emptySet())
        s.add(id.toString())
        sp(c).edit().putStringSet(KEY, s).apply()
    }

    fun clear(c: Context, id: Long) {
        val s = HashSet(sp(c).getStringSet(KEY, emptySet()) ?: emptySet())
        s.remove(id.toString())
        sp(c).edit().putStringSet(KEY, s).apply()
    }
}

/**
 * 记录因“缺少精确闹钟权限”而无法排程的任务 id。
 * TasksFragment 据此显示“需授权”红标，用户授予权限后由 MainActivity 自动重排清除。
 */
object PendingAuthStore {
    private const val KEY = "pending_auth_set"
    private fun sp(c: Context) = c.getSharedPreferences(PREFS, 0)

    fun add(c: Context, id: Long) {
        val s = HashSet(sp(c).getStringSet(KEY, emptySet()) ?: emptySet())
        s.add(id.toString())
        sp(c).edit().putStringSet(KEY, s).apply()
    }

    fun remove(c: Context, id: Long) {
        val s = HashSet(sp(c).getStringSet(KEY, emptySet()) ?: emptySet())
        s.remove(id.toString())
        sp(c).edit().putStringSet(KEY, s).apply()
    }

    fun contains(c: Context, id: Long): Boolean =
        sp(c).getStringSet(KEY, emptySet())?.contains(id.toString()) ?: false

    fun isNotEmpty(c: Context): Boolean =
        (sp(c).getStringSet(KEY, emptySet()) ?: emptySet()).isNotEmpty()
}
