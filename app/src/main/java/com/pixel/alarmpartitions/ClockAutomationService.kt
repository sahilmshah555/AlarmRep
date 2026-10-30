package com.pixel.alarmpartitions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ClockAutomationService : AccessibilityService() {

    companion object {
        val pendingDisableTags = mutableSetOf<String>()
        var isAutomating = false

        fun isServiceEnabled(context: Context): Boolean {
            val prefString = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return prefString.contains("${context.packageName}/${ClockAutomationService::class.java.name}")
        }

        fun enablePartitionInStockClock(context: Context, partition: AlarmPartition) {
            val handler = Handler(Looper.getMainLooper())
            val calendarDays = ArrayList(partition.repeatDays)

            partition.times.forEachIndexed { index, alarmTime ->
                handler.postDelayed({
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, alarmTime.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, alarmTime.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "[${partition.name}]")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        putExtra(AlarmClock.EXTRA_VIBRATE, true)
                        if (calendarDays.isNotEmpty()) {
                            putExtra(AlarmClock.EXTRA_DAYS, calendarDays)
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }, index * 350L)
            }
        }

        fun disablePartitionInStockClock(context: Context, partition: AlarmPartition) {
            pendingDisableTags.add("[${partition.name}]")
            isAutomating = true

            val showAlarmsIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(showAlarmsIntent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isAutomating || pendingDisableTags.isEmpty()) return
        val rootNode = rootInActiveWindow ?: return

        var toggledAny = false
        val tagsSnapshot = pendingDisableTags.toList()

        for (tag in tagsSnapshot) {
            val matchingNodes = rootNode.findAccessibilityNodeInfosByText(tag)
            for (node in matchingNodes) {
                val cardContainer = findAlarmCardContainer(node)
                if (cardContainer != null) {
                    val switchNode = findCheckedSwitch(cardContainer)
                    if (switchNode != null) {
                        switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        toggledAny = true
                    }
                }
            }
        }

        if (toggledAny || pendingDisableTags.isNotEmpty()) {
            pendingDisableTags.clear()
            isAutomating = false
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 450L)
        }
    }

    private fun findAlarmCardContainer(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.className?.contains("CardView") == true ||
                current.className?.contains("ViewGroup") == true ||
                current.className?.contains("ConstraintLayout") == true
            ) {
                if (findCheckedSwitch(current) != null) return current
            }
            current = current.parent
            depth++
        }
        return node?.parent
    }

    private fun findCheckedSwitch(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val isSwitch = root.className?.contains("Switch") == true ||
                root.className?.contains("CompoundButton") == true
        if (isSwitch && root.isCheckable && root.isChecked && root.isClickable) {
            return root
        }
        for (i in 0 until root.childCount) {
            val found = findCheckedSwitch(root.getChild(i))
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {
        isAutomating = false
        pendingDisableTags.clear()
    }
}
