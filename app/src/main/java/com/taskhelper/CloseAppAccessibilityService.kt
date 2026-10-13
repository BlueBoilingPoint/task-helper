package com.taskhelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：在任务结束后，从“最近任务”中关闭目标应用。
 * 注意：不同厂商 ROM 的“最近任务”交互不同，关闭手势需按机型适配，
 * 此处先以 GLOBAL_ACTION_RECENTS 作为尽力而为的实现。
 */
class CloseAppAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: CloseAppAccessibilityService? = null

        fun requestClose() {
            instance?.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }

        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
