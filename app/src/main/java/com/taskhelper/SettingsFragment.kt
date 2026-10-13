package com.taskhelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    /** 权限项：key 用于生成引导与跳转；enabled 为 null 表示无法程序化检测（需手动）。 */
    private data class PermItem(
        val key: String,
        val name: String,
        val desc: String,
        val enabled: Boolean?
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderPerms(view)
        setupDormant(view)
        view.findViewById<TextView>(R.id.about_version)?.text =
            "关于 · 版本 ${BuildConfig.VERSION_NAME}"
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置返回后刷新权限真实状态
        view?.let { renderPerms(it) }
    }

    private fun setupDormant(view: View) {
        val prefs = requireContext().getSharedPreferences("taskhelper_prefs", 0)
        val swDormant = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.sw_dormant)
        swDormant?.isChecked = prefs.getBoolean("dormant_enabled", true)
        swDormant?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dormant_enabled", isChecked).apply()
        }
    }

    private fun renderPerms(view: View) {
        val ctx = requireContext()
        val box = view.findViewById<LinearLayout>(R.id.perm_list)
        box.removeAllViews()

        val items = listOf(
            PermItem(
                "exact_alarm", "精确闹钟",
                "定时到点唤醒应用（Android 12+ 必需）", isExactAlarmEnabled(ctx)
            ),
            PermItem(
                "battery", "电池优化白名单",
                "防止系统回收后台任务", isBatteryWhitelisted(ctx)
            ),
            PermItem(
                "autostart", "自启动 / 后台弹出",
                "厂商 ROM 限制，需手动授权（无法程序化检测）", null
            ),
            PermItem(
                "accessibility", "无障碍服务",
                "用于自动关闭目标应用（关闭效果因 ROM 而异）", isAccessibilityEnabled(ctx)
            ),
            PermItem(
                "notification", "通知权限",
                "执行提醒与状态通知", isNotificationEnabled(ctx)
            )
        )

        val manufacturer = detectManufacturer()

        items.forEach { item ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 14, 12, 14)
            }
            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(ctx).apply {
                text = item.name
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#1A1D26"))
            })
            textCol.addView(TextView(ctx).apply {
                text = item.desc
                textSize = 11f
                setTextColor(android.graphics.Color.parseColor("#9AA1B2"))
            })
            row.addView(textCol)

            val (label, color) = when (item.enabled) {
                true -> "已开启" to "#16924A"
                false -> "未开启" to "#C96A12"
                null -> "需手动" to "#6B7280"
            }
            val status = TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor(color))
                // 未开启 / 需手动 时可点击查看开通方法
                isClickable = item.enabled != true
                isFocusable = item.enabled != true
                if (item.enabled != true) {
                    paint.isUnderlineText = true
                }
            }
            status.setOnClickListener {
                showGuide(ctx, item.key, item.name, manufacturer)
            }
            row.setOnClickListener {
                if (item.enabled != true) showGuide(ctx, item.key, item.name, manufacturer)
            }
            row.addView(status)
            box.addView(row)
        }
    }

    private fun detectManufacturer(): String {
        val raw = "${Build.MANUFACTURER ?: ""} ${Build.BRAND ?: ""}"
        return raw.lowercase(java.util.Locale.ROOT)
    }

    /** 弹出适配机型的开通引导，并提供一键跳转系统设置。 */
    private fun showGuide(ctx: Context, key: String, name: String, manufacturer: String) {
        val isXiaomi = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
        val isHuawei = manufacturer.contains("huawei") || manufacturer.contains("honor")

        val deviceTag = when {
            isXiaomi -> "（适配：小米 / Redmi 澎湃 OS）"
            isHuawei -> "（适配：华为 / 荣耀 鸿蒙 HarmonyOS）"
            else -> "（通用 Android 路径，不同品牌可能略有差异）"
        }
        val steps = when (key) {
            "exact_alarm" -> if (isXiaomi) {
                "1. 设置 → 应用设置 → 应用管理\n2. 找到「定时任务助手」→ 权限管理\n3. 闹钟和提醒 → 允许\n4. 或在 设置 → 通知与控制中心 → 通知管理 中允许本应用"
            } else if (isHuawei) {
                "1. 设置 → 应用和服务 → 应用管理\n2. 找到「定时任务助手」→ 权限\n3. 闹钟和提醒 → 允许"
            } else {
                "1. 设置 → 应用 → 定时任务助手\n2. 闹钟和提醒 / 通知 → 允许设置精确闹钟"
            }
            "battery" -> if (isXiaomi) {
                "1. 设置 → 省电与电池 → 应用智能省电\n2. 找到「定时任务助手」→ 设为「无限制」\n3. 并在 应用管理 → 本应用 → 权限管理 开启「自启动」"
            } else if (isHuawei) {
                "1. 设置 → 应用和服务 → 应用启动管理\n2. 找到「定时任务助手」→ 关闭「自动管理」\n3. 手动开启「允许自启动」「允许后台活动」"
            } else {
                "1. 设置 → 电池 → 电池优化\n2. 找到「定时任务助手」→ 设为「不优化 / 不受限制」"
            }
            "autostart" -> if (isXiaomi) {
                "1. 设置 → 应用设置 → 应用管理\n2. 找到「定时任务助手」→ 权限管理\n3. 开启「自启动」与「后台弹出界面」\n4. 并在 省电与电池 → 应用智能省电 设为「无限制」"
            } else if (isHuawei) {
                "1. 设置 → 应用和服务 → 应用启动管理\n2. 找到「定时任务助手」→ 关闭「自动管理」\n3. 手动开启「允许自启动」「允许后台活动」「允许后台弹出」"
            } else {
                "1. 设置 → 应用 → 定时任务助手\n2. 电池 / 自启动管理 中允许后台运行与自启动（各品牌路径不同）"
            }
            "accessibility" -> if (isXiaomi) {
                "1. 设置 → 更多设置 → 无障碍\n2. 已下载的服务 → 定时任务助手 → 开启"
            } else if (isHuawei) {
                "1. 设置 → 辅助功能 → 无障碍\n2. 已下载的服务 / 应用 → 定时任务助手 → 开启"
            } else {
                "1. 设置 → 无障碍 → 已安装的服务\n2. 找到「定时任务助手」→ 开启"
            }
            "notification" -> if (isXiaomi) {
                "1. 设置 → 通知与控制中心 → 通知管理\n2. 找到「定时任务助手」→ 允许通知\n3. 建议开启「悬浮通知」「锁屏通知」"
            } else if (isHuawei) {
                "1. 设置 → 通知 → 通知管理\n2. 找到「定时任务助手」→ 允许通知\n3. 按需开启「横幅」「锁屏」"
            } else {
                "1. 设置 → 应用 → 定时任务助手 → 通知\n2. 允许通知"
            }
            else -> "请在系统设置中为「定时任务助手」开启对应权限。"
        }

        val message = "$deviceTag\n\n$steps"
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("开启「$name」")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ -> openSettings(ctx, key) }
            .setNegativeButton("知道了", null)
            .show()
    }

    private fun openSettings(ctx: Context, key: String) {
        val intent = when (key) {
            "exact_alarm" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            } else {
                appDetails(ctx)
            }
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "notification" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                }
            } else {
                appDetails(ctx)
            }
            else -> appDetails(ctx)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try { ctx.startActivity(appDetails(ctx)) } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    // ---- 真实权限检测 ----

    private fun isExactAlarmEnabled(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true
    }

    private fun isBatteryWhitelisted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val flat = ComponentName(ctx, CloseAppAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabled?.split(":")?.any { it.equals(flat, ignoreCase = true) } ?: false
    }

    private fun isNotificationEnabled(ctx: Context): Boolean {
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
}
