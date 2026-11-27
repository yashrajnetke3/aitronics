package com.aitronics.assistant
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class AssistantAccessibility : AccessibilityService() {
    companion object {
        var active: AssistantAccessibility? = null
            private set
    }
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if(active === this) active = null; super.onDestroy() }
    fun screenText(): String {
        val root = rootInActiveWindow ?: return "No accessible screen."
        val output = mutableListOf<String>()
        var visited = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if(depth > 30 || visited++ >= 500 || node.isPassword || !node.isVisibleToUser) return
            val label = node.text?.toString() ?: node.contentDescription?.toString()
            if(!label.isNullOrBlank()) output.add(label.take(200))
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it,depth+1) }
        }
        walk(root,0)
        return output.distinct().joinToString("\n").take(3000).ifBlank { "No readable screen text." }
    }
}
